<img width="1916" height="526" alt="image" src="https://github.com/user-attachments/assets/ff51ed18-90d5-44ae-a94a-d1c846d4efbc" />

# Dishes Be Crazy

A food-ordering platform where local Montreal home cooks sell their homemade meals (SOEN 343, Phase II). Customers browse their kitchens' menus and place orders; cooks accept or reject them and prepare them in FIFO order. Kitchen accounts use the existing RESTAURANT role. Every command and error appears in the on-screen output console.

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
| Home cook - Sam's Family Kitchen | `restaurant.demo@example.com` |
| Home cook - Robin's Sunday Kitchen | `restaurant2.demo@example.com` |
| Home cook - Casey's Home Kitchen | `restaurant3.demo@example.com` |
| Courier | `courier.demo@example.com` |
| Operator | `operator.demo@example.com` |

Run initial setup from `src/main/resources/demo/` using the database configured in `.env`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo-seed
```

The seed profile runs without an HTTP server and exits after the runners finish. Then start the backend normally:

```sh
./mvnw spring-boot:run
```

For the starting data, the four scenario scripts and a manual results checklist, see [Phase III demo walkthroughs](docs/demo-walkthroughs.md). Reset restores the fixture starting state globally: it clears everyone's order activity, removes accounts and menus outside the fixtures (including non-fixture Auth users), restores fixture values and history, and rebuilds both restaurant and dispatch queues. The six fixture Auth accounts keep their IDs and logins; deleted accounts' emails can be registered again. Use the fixture accounts after Reset.

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
