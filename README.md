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

## Demo accounts

Password for all of them: `DEMO_SEED_PASSWORD` from `.env`.

| Role | Email |
|---|---|
| Customer | `customer.demo@example.com` |
| Home cook — Sam's Family Kitchen | `restaurant.demo@example.com` |
| Home cook — Robin's Sunday Kitchen | `restaurant2.demo@example.com` |
| Home cook — Casey's Home Kitchen | `restaurant3.demo@example.com` |
| Courier | `courier.demo@example.com` |
| Operator | `operator.demo@example.com` |

The fixtures represent three local Montreal home cooks, each with an OPEN kitchen, three available dishes
and one sold-out dessert. Personal kitchen names, family recipes and small batches reflect the app's home-cooking theme.
Their dishes have distinct names and prices so a kitchen mix-up is visible during a walkthrough:

| Home kitchen | Available dishes | Sold-out dessert |
|---|---|---|
| Sam's Family Kitchen | Grandma's Tourtière, Butter Chicken, Pierogi (6) | Pouding Chômeur |
| Robin's Sunday Kitchen | Sunday Mushroom Risotto, Handmade Spinach Ravioli, Garden Tomato Flatbread | Robin's Tiramisu |
| Casey's Home Kitchen | Home Braised Beef Bowl, Homemade Chicken Burrito, Black Bean Tacos (3) | Cinnamon Churros |

The order fixture defines nine accepted orders for future preparation queues of 0 / 3 / 6,
plus delivered, rejected and cancelled customer history: twelve orders, twelve lines and twenty-eight events.
Refunded history awaits the missing `DeliveredState.refund()` implementation; billing records remain separate integration work.
With `demo-seed`, startup validates `orders.json` but does not yet insert its orders or events,
so those queues are not populated by seeding.
Seeding creates missing records; it does not reset existing data.
Existing profile and menu rows keep their previous kitchen names, dish names and prices when seeding is repeated.
The revised kitchens and dishes appear on initial population or when the later Reset restoration uses these fixtures.

If they don't exist yet, create the accounts and menus once from `src/main/resources/demo/`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo-seed
```

## Tests

```sh
./mvnw test
```
