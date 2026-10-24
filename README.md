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
| Restaurant | `restaurant.demo@example.com` |
| Courier | `courier.demo@example.com` |
| Operator | `operator.demo@example.com` |

If they don't exist yet, create them (and the demo restaurant's menu) once from `src/main/resources/demo/`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo-seed
```

## Tests

```sh
./mvnw test
```
