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

This operation is not run automatically at startup and has no HTTP endpoint or button yet. The next integration
must stop concurrent demo commands, call restoration, rebuild the in-memory order queues and restore DLV/dispatch
state before reporting Reset success. Current courier profile values follow the existing fixtures; DLV assignments
and runtime availability still need their module integration.

## Tests

```sh
./mvnw test
```

Fixture, seeding and restoration checks use isolated in-memory databases and mocked services:

```sh
./mvnw -o -Dtest=DemoDataRestorerTest,DemoOrderSeederTest,DemoFixtureLoaderTest,DemoOrderFixtureTest,DemoProfileSeederTests,DemoMenuSeederTest test
```
