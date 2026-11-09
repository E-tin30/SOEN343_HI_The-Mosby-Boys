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
STRIPE_WEBHOOK_SECRET=...
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

## Checkout payments

Before running the payment branch, apply `src/main/resources/db/migration/V1__add_recoverable_checkouts.sql`
to the chosen development database. Flyway remains disabled, so this SQL file is not applied automatically.
It adds a `Checkout` table containing the customer, restaurant, frozen server-priced items, total and Stripe reference.
The backend accesses this table; row-level security prevents direct Data API access without a policy.

Use matching Stripe test keys for the project demonstration. Configure a Stripe webhook for
`POST /payments/webhook` with `payment_intent.amount_capturable_updated` and `payment_intent.succeeded` events.
Set its signing secret as `STRIPE_WEBHOOK_SECRET` in `.env`. Checkout creation stays unavailable until this is set.
For local forwarding, keep the [Stripe CLI listener](https://docs.stripe.com/webhooks#test-webhook) running:

```sh
stripe listen --forward-to localhost:8080/payments/webhook
```

Use the signing secret printed by that listener for local testing, then restart the backend.
The callback verifies the signature and reads the checkout reference; the backend retrieves the current payment
through its pinned Stripe SDK before taking action, so it does not depend on the listener's event API version.

The browser first creates/reuses a checkout and shows its exact CAD payment total. Pay and Place Order waits
for card authorization. The backend then checks current availability/pricing, prepares the order, captures payment
and commits the order. It adds the order to the in-memory queue only after commit.
Rejected carts release the authorization without charging. Verified callbacks and retries can complete a captured
payment after a database/network interruption using the stored purchase and the same order ID.

If checkout is interrupted, retry it rather than starting another payment. The browser stores a non-secret attempt ID
per customer/cart. Back to Cart asks the backend to cancel the attempt; if payment already completed, it opens
that order instead. `/placeOrder` now accepts `{ "checkoutId": "..." }` and checks the authenticated customer;
raw cart payloads cannot bypass payment verification.

Future Reset integration must resolve/cancel pending checkout authorizations and include `Checkout` records in its
agreed restoration boundary. Seeded historical orders do not initiate Stripe payments.

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
npm test                      # mocked frontend tests
npm run build                 # TypeScript and production build
./mvnw test
```
