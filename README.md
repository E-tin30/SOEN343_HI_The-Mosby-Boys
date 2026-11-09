<img width="1916" height="526" alt="image" src="https://github.com/user-attachments/assets/ff51ed18-90d5-44ae-a94a-d1c846d4efbc" />

# Dishes Be Crazy

A food-ordering platform for home cooks (SOEN 343, Phase II). Customers browse menus and place orders; restaurants accept or reject them and prepare them in FIFO order. Every command and error appears in the on-screen output console.

**Built this phase:** REG (registration, login, roles) and ORD (menus, orders, order lifecycle, preparation queue), plus a minimal DASH.

## Team

- Ethan Elkaim 40311428 - Lead Developer
- Zayden Kung'u 40311065 - Project Manager (Team Leader)
- Ashton Levine 40306341 - Product Manager
- Adrian Charbonneau 40310777 - Prompt Engineer
- Yana Vassilyev 40270779 - DevOps
- Jalen Brayne 40319433 - QA

## Requirements

- Java 21 (Maven comes with the wrapper, `mvnw`)
- Node.js 22+
- A `.env` file in the project root with the Supabase credentials (ask the team):

```text
SUPABASE_DB_URL=...
SUPABASE_DB_USER=...
SUPABASE_DB_PASSWORD=...
SUPABASE_URL=...
SUPABASE_PUBLISHABLE_KEY=...
SUPABASE_SECRET_KEY=...
DEMO_SEED_PASSWORD=...
VITE_STRIPE_PUBLISHABLE_KEY=...
STRIPE_SECRET_KEY=...
```

## Run

Two terminals, from the project root:

```sh
./mvnw spring-boot:run        # backend  → http://localhost:8080
```

```sh
npm install                   # first time only
npm run dev                   # frontend → http://127.0.0.1:5173
```

Open http://127.0.0.1:5173.

### With Docker

Needs only Docker and the `.env` above. Stop any local backend/frontend first (ports 8080, 5173).

```sh
docker compose up --build     # stop with: docker compose down
```

Open http://localhost:5173.

## Demo accounts

Password for all of them: `DEMO_SEED_PASSWORD` from `.env`.

| Role | Email |
|---|---|
| Customer | `customer.demo@example.com` |
| Restaurant — Dishes Be Crazy Demo | `restaurant.demo@example.com` |
| Restaurant — Demo Kitchen Building | `restaurant2.demo@example.com` |
| Restaurant — Demo Kitchen Busy | `restaurant3.demo@example.com` |
| Courier | `courier.demo@example.com` |
| Operator | `operator.demo@example.com` |

The fixtures define three OPEN restaurants, each with three available dishes and one sold-out dessert.
The order fixture defines nine accepted orders for preparation queues of 0 / 3 / 6 on a fresh demo dataset,
plus delivered, rejected and cancelled customer history: twelve orders, twelve lines and twenty-eight events.
Refunded history awaits the missing `DeliveredState.refund()` implementation; billing records remain separate integration work.
With `demo-seed`, the profile and menu seeders run first, followed by `DemoOrderSeeder`.
The order seeder resolves usernames to established user IDs and inserts missing orders, purchased-item lines
and events together in one transaction. Historical lines retain their purchased names/prices and event times;
seeding history does not consume menu stock or change the courier's current availability.

Run initial setup from `src/main/resources/demo/` using the database configured in `.env`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo-seed
```

The seed profile runs without an HTTP server and exits after the runners finish. Then start the backend normally:

```sh
./mvnw spring-boot:run
```

Normal startup loads persisted waiting orders into the in-memory queues. Repeating the seed command skips
existing orders as a unit, preserving changed states and additional history. Other database records are retained,
so pre-existing orders can change the shared database's queue counts. This is initial population.

### Database restoration for Reset

`DemoDataRestorer.restore()` is the database step behind the upcoming operator Reset action.
It loads and validates the same JSON fixtures, then restores the database in one transaction:

- Restore demo profile fields, OPEN restaurant statuses, courier profile state/location and menu stock/availability.
- Recreate deleted public profiles and fixture menu items under the existing Auth IDs.
- Remove orders belonging to a fixture customer **or** fixture restaurant, including their lines, events and bills.
  Remove other bills owned by those demo customers/restaurants and extra menu items under demo restaurants.
- Reinsert the twelve fixture orders, twelve lines and twenty-eight events, restoring the persisted
  preparation counts to 0 / 3 / 6 and the original delivered/rejected/cancelled customer history.

For example: pause a demo restaurant, sell out its menu and progress an accepted order to delivery.
Restoration reopens that restaurant, restores its stock, and puts that order and its event history back
to the fixture state. Orders created during the walkthrough disappear. Repeat restoration gives the same baseline.

Existing Auth accounts, passwords and sessions stay unchanged, including the operator login. Missing Auth accounts
require initial `demo-seed` setup. Unrelated team data is preserved; fixture ID collisions or unrelated orders
referencing an extra demo menu item stop restoration with an error. Any database failure rolls back the entire step.
Existing profile/menu creation times are retained; recreated rows receive their database-default creation times.
Order and event times come from the fixtures. Bills start empty for demo-owned activity; no payment API is called.

### Coordinating database and ORD queue restoration

`ResetService.restoreDatabaseAndOrderQueues(operatorId)` combines database restoration with the ORD queue rebuild.
It checks the caller's current profile through REG and permits only an OPERATOR. It commits database restoration
first, then clears and rebuilds each restaurant's placed, preparation and ready queues from the restored records.
For example, an order prepared during the walkthrough returns to AcceptedState in the database **and** returns
to its original position in the preparation waiting list. Repeating the operation restores the same 0 / 3 / 6 baseline.

The service rejects overlapping restoration attempts in this application instance. It also rejects calls inside an
existing transaction, which could otherwise delay the database commit until after queues have already changed.
If database restoration fails, its writes roll back and queue rebuilding is skipped. If the subsequent queue read
fails, the database has already committed and the existing queues are retained: the operation throws an error.
Normal requests stay blocked until a successful retry restores the database and rebuilds the queues again.

`DemoStateAccess` gives normal requests shared access and restoration exclusive access. Reset first marks itself
pending, refuses new requests, and waits up to five seconds for existing requests to finish before changing the database.
If access times out or the waiting thread is interrupted, Reset fails before restoration starts and clears its pending flag.
Normal requests can resume unless an earlier incomplete restoration still requires recovery; the operator can retry Reset.
The `DemoRequestFilter` holds shared access through each synchronous request, including transaction commits and
synchronous after-commit callbacks. It runs after authentication/authorization in the existing security chain.
Blocked requests receive HTTP 503 with `Retry-After: 1`; requests run concurrently when Reset is inactive.

Login and the authenticated current-profile lookup remain available so the operator can recover. The future
`POST /api/demo/reset` route bypasses shared access so its service can acquire exclusive access; it still requires
authentication and the service's OPERATOR check. No route is registered by that exemption. A failure before the
database commits releases normal access unless an earlier attempt already left recovery incomplete.

This is an internal operation: no HTTP endpoint, dashboard button or automatic restoration at startup is enabled.
The guard covers synchronous HTTP work in this backend instance. Direct background/service writers and other
backend processes sharing the database must also be stopped or coordinated; asynchronous callbacks need their
own participation. The full Reset integration still needs DLV's global dispatch queue, offers, assignments and
courier-state contract before the dashboard can report Reset success. Current courier profile values follow the
existing fixtures; no delivery behavior is changed by the request guard.

## Tests

```sh
npm test                      # mocked frontend tests
npm run build                 # TypeScript and production build
./mvnw test
```

Fixture, seeding and restoration checks use isolated in-memory databases and mocked services:

```sh
./mvnw -o -Dtest=DemoRequestFilterTest,DemoStateAccessTest,ResetServiceTest,ResetServiceIntegrationTest,OrderQueueLoaderTest,OrderQueuesTest,PreparationQueueTest,DemoDataRestorerTest,DemoOrderSeederTest,DemoFixtureLoaderTest,DemoOrderFixtureTest,DemoProfileSeederTests,DemoMenuSeederTest test
```
