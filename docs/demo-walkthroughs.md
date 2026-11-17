# Dishes Be Crazy demo walkthroughs

This is the step by step manual test guide for issue [56](https://github.com/E-tin30/SOEN343_HI_The-Mosby-Boys/issues/56). Follow one walkthrough at a time and use Reset between them. Each numbered step says which account to use, what to click and what should happen.

The navigation and button labels below were checked against `phase-3` commit `bf9b56b`. The shared database baseline was independently checked read-only on November 13, 2026; that does not mark the browser walkthroughs as passed. Record the commit you actually run in the results table at the end.

Phase III instructions, section 9.1 on page 11, require **Happy Path Order** and **No Courier Available**. The demonstrator may also request **Sold Out / Paused** or **Dinner Rush**. Prepare all four.

## Contents

- [Set up the app and accounts](#set-up-the-app-and-accounts)
- [Restore and verify the starting point](#restore-and-verify-the-starting-point)
- [Walkthrough 1 Happy Path Order](#walkthrough-1-happy-path-order)
- [Walkthrough 2 No Courier Available](#walkthrough-2-no-courier-available)
- [Walkthrough 3 Sold Out and Paused](#walkthrough-3-sold-out-and-paused)
- [Walkthrough 4 Dinner Rush](#walkthrough-4-dinner-rush)
- [Additional Reset checks](#additional-reset-checks)
- [Record the results](#record-the-results)

## Set up the app and accounts

### Start the correct application

1. Check that your checkout is on `phase-3`. Stop any older local backend before starting the current one.
2. From the project root, start the backend in one terminal:

   ```sh
   ./mvnw spring-boot:run
   ```

3. Start the frontend in another terminal:

   ```sh
   npm run dev
   ```

4. Open [http://127.0.0.1:5173/login](http://127.0.0.1:5173/login). Use this same address in all test windows.
5. Ask teammates to stop other backends or writers using this shared database during the test. All test browser sessions should use this one backend.
6. Use the existing demo accounts. The database is already populated; do not rerun the initial seeder just to begin these walkthroughs. Reset is the action that returns populated data to the starting point.
7. Before testing checkout, confirm the backend uses a Stripe test secret key and the frontend uses a Stripe test publishable key. Their prefixes are `sk_test_` and `pk_test_`. Do not put keys or passwords in screenshots or the results table.

### Open separate browser profiles

Use four separate browser profiles, labelled **Operator**, **Customer**, **Cook** and **Courier**. Different browsers also work. Ordinary windows and tabs in the same browser profile share login storage and do not isolate accounts. Multiple private windows in the same browser may also share a private session.

Use ordinary additional tabs only for the **same role**. For example, the Operator profile can have one tab on the operator home and another on the live map. The Customer profile can keep an order-summary tab and a history tab.

All demo accounts use the password held in `DEMO_SEED_PASSWORD` in the project root `.env`. Enter its value in the Password field; the literal text `DEMO_SEED_PASSWORD` is not the password. This guide deliberately does not copy the password.

| Browser profile | Account email | Role and purpose | Home after login |
|---|---|---|---|
| Operator | `operator.demo@example.com` | Reset, kitchen controls and city map | `/operator/home` |
| Customer | `customer.demo@example.com` | Place orders and inspect history | `/home` |
| Cook | `restaurant.demo@example.com` | Sam's Family Kitchen for walkthroughs 1 to 3 | `/restaurant/home` |
| Cook | `restaurant2.demo@example.com` | Robin's Sunday Kitchen for walkthrough 4 | `/restaurant/home` |
| Optional second Cook | `restaurant3.demo@example.com` | Casey's Home Kitchen, baseline comparison | `/restaurant/home` |
| Courier | `courier.demo@example.com` | Availability, offers, pickup and delivery | `/courier/home` |

To sign in to any account:

1. In the appropriate browser profile, open `/login`.
2. Enter the account email in **Email**.
3. Enter the demo password in **Password**.
4. Click **Login** once and wait for the role's home page.
5. If a different account is already signed in, first return to its home page and click **Log out**, then sign in to the intended account.

### Refresh without losing the cart

- The operator restaurant list and live map normally refresh about every five seconds while visible. Return to the tab and allow a refresh, or reload it.
- The courier page polls about every five seconds while online. Its offline page does not keep polling for offers. Reload it after Reset to see the restored state.
- The cook's **View Orders** board, customer order summary and order history need a manual browser refresh after another account changes an order.
- The customer cart checks availability on focus and about every fifteen seconds. Use **Retry availability** when it appears. Do not reload a customer cart that you need to keep: the cart is held in browser memory and a full reload clears it.
- Reset restores database data and server queues. It does not navigate other browsers or clear their current carts. Refresh those pages and clear any remaining cart before a new walkthrough.

### Enter a successful test payment

Whenever a walkthrough asks you to complete checkout:

1. Click **Continue to Checkout** on the cart page.
2. Wait for the Stripe payment form. Select **Card** if Stripe offers a choice of payment methods.
3. Enter card number **4242 4242 4242 4242**, expiry **12/34** and CVC **123**. Use a later valid expiry if running this guide after that date.
4. If requested, use test name **Alex Demo**, country **Canada** and postal code **H3G 1M8**. Complete any other required test fields shown by the form.
5. Click **Pay and Place Order** once.
6. If the browser shows **Order placed successfully!**, click **OK** to dismiss it.
7. Expect the order-summary page. Record its full URL; the last segment is the full order ID.
8. Check that there is no payment error in the page or **Output console**. An order-success alert alone does not prove that Stripe confirmation succeeded. Record a payment error as a failure even if an order was created.

These are Stripe's documented interactive test values. Use them only with test keys. See [Stripe testing](https://docs.stripe.com/testing#testing-interactively).

## Restore and verify the starting point

Run these steps before each walkthrough. **Reset affects everyone in the configured shared database.** It deletes non-demo orders, accounts including their Auth users, and menus, then restores the fixtures. The six fixture Auth accounts retain their IDs and passwords. This is not a Stripe reset and does not clear browser carts.

### Click Reset as the operator

1. In the **Operator** browser profile, log in as `operator.demo@example.com`.
2. Confirm you are on the operator home page. If you are on the map or history page, click **Back to home**.
3. Click **Reset demo** in the header beside **Log out**. In the checked revision, Reset appears before Log out. There is no extra confirmation dialog.
4. Expect the button to read **Resetting…** while disabled.
5. Wait for **Demo data restored.** Do not begin the next walkthrough while Reset is still pending or reports an error.
6. In the **Restaurants** section, clear **Search restaurants** and select **All** so no kitchen is hidden by a filter.
7. Wait for a refresh or reload the operator page. Verify the following:

   | Kitchen | Status | Text in operator list | Preparation count | Map colour while open |
   |---|---|---|---:|---|
   | Sam's Family Kitchen | Open | No orders waiting | 0 | Green, Quiet |
   | Robin's Sunday Kitchen | Open | 3 orders waiting | 3 | Amber, Steady |
   | Casey's Home Kitchen | Open | 6 orders waiting | 6 | Red, Busy |

8. Click **View live map**. Verify that all three kitchen markers and the **On the map** list show the same baseline counts.
9. The map legend distinguishes **Quiet (0–2)**, **Steady (3–5)**, **Busy (6+)** and **Paused**. Paused uses a separate terracotta colour in this revision; do not treat it as the busy red colour.
10. Click **Back to home** when you need the Reset button again. Optionally keep a second Operator tab open on the map.

The preparation count measures orders that are **Accepted and waiting to start preparation**. It does not include Incoming, already Preparing, or Ready orders. The cook's **Active Orders** column combines Accepted and Preparing, so its total can differ from the operator's preparation count.

### Verify the other accounts

1. In the **Courier** profile, log in as `courier.demo@example.com` or reload its home page.
2. Expect **Offline**, **Start Driving**, no active delivery and no new offer. Leave it offline until the walkthrough tells you to start driving. An offline courier is not expected to have an online map marker.
3. In the **Customer** profile, log in as `customer.demo@example.com`.
4. Click **Order history**, then **All**. Expect thirteen fixture orders: nine Accepted, one Delivered, one Refunded, one Rejected and one Cancelled.
5. Check the history tabs: **In progress (9)**, **Delivered (1)**, **Refunded (1)** and **Cancelled / rejected (2)**.
6. Click **Back to home**, then **View cart**. If it contains leftovers, click **Back to Cart** first if you are on checkout, then **Clear cart**. Expect **Your cart is empty.**
7. For walkthroughs 1 to 3, sign the **Cook** profile into `restaurant.demo@example.com`. On its home, confirm **Incoming: 0**, **Accepted: 0**, **In-preparation: 0** and **Ready: 0** after a fresh Reset.
8. On Sam's home menu, confirm **Grandma's Tourtière** shows **6 in stock**, **Butter Chicken** shows **10 in stock**, **Pierogi (6)** shows **15 in stock**, and **Pouding Chômeur** shows **0 in stock** with **Sold out**.

Database reference after Reset: six Auth accounts, six application profiles, three kitchens, twelve dishes, thirteen orders, thirteen purchased lines, thirty-five events and zero bills. This was verified by a read-only database audit; not every count has its own frontend screen.

Starting point result: [ ] Pass [ ] Fail. Notes: ______________________________

## Walkthrough 1 Happy Path Order

Goal: place a customer order, prepare it at Sam's kitchen, deliver it with the courier, inspect its price summary, then Reset and repeat.

### A Restore the baseline and make the courier available

1. **Operator — `operator.demo@example.com`:** perform every check in [Restore and verify the starting point](#restore-and-verify-the-starting-point).
2. **Courier — `courier.demo@example.com`:** open or reload `/courier/home` and confirm **Offline**.
3. Click **Start Driving**. Expect **Idle** and **Waiting for an offer…**. Do not click **End Driving** during this walkthrough.

### B Place one customer order

1. **Customer — `customer.demo@example.com`:** from home, click **Browse restaurants**.
2. Click the **Sam's Family Kitchen** card, which contains **View menu →**.
3. Find **Grandma's Tourtière**, priced at **$18.50** after Reset.
4. Click **Add to cart** exactly once on that dish. The header should show **View cart (1)**.
5. Click **View cart (1)**. Confirm one Grandma's Tourtière, quantity **1**, with an item subtotal of **$18.50**. Use the quantity controls if you accidentally added more than one.
6. Wait for the price breakdown. Under the checked default pricing configuration, expect:

   | Price component | Expected displayed amount |
   |---|---:|
   | Subtotal | $18.50 |
   | Delivery Fee | $4.99 |
   | Service Fee | $2.22 |
   | Small Order Surcharge | $0.00, normally hidden |
   | Taxable Subtotal | $25.71 |
   | GST | $1.29 |
   | QST | $2.56 |
   | Total | $29.56 |

   These amounts assume the committed pricing defaults have not been overridden.

7. Click **Continue to Checkout** and enter the [successful test payment](#enter-a-successful-test-payment).
8. After **Pay and Place Order**, dismiss the success alert with **OK** if shown.
9. Expect **Your Order**, **Order Status: PLACED**, the correct dish and quantity, and the price breakdown.
10. Copy the full summary URL and save it as **Happy Path order**. Keep this summary tab open. New order ID: ______________________________

### C Accept and prepare the same order

1. **Cook — `restaurant.demo@example.com`:** confirm you are signed in to Sam's Family Kitchen.
2. Click **View Orders**. If the board was already open before the customer ordered, reload it.
3. Under **Incoming Orders**, find the new order containing **1× Grandma's Tourtière**. Match its displayed ID prefix with the full ID saved by the customer.
4. Click **Accept** on that order. It should move into **Active Orders** and show **Start preparing**. Do not click **Reject**.
5. Optionally check the Operator list: Sam's waiting count should become **1** before preparation begins.
6. Return to the Cook board and click **Start preparing** on the same order.
7. Expect the **In preparation** tag and a **Mark ready** button. Sam's waiting preparation count should return to **0**, since this order has started preparation.
8. Click **Mark ready**. Expect the order in the **Ready** column until it is picked up.

### D Accept the offer and deliver

1. **Courier — `courier.demo@example.com`:** return to the courier home and allow at least one polling interval, about five seconds. Reload if needed.
2. Expect **New offer** with the same order's dish and ID prefix. The courier can still show **Idle** while the offer is pending.
3. Click **Accept**, not **Decline**.
4. Expect **Active delivery**, a **Delivering** status with **Heading to the restaurant.**, and the **Pick Up Order** button. This is the assigned stage.
5. Click **Pick Up Order**.
6. Expect **Delivering** with **On the way to the customer.**, and the **Deliver** button. This is the carrying stage.
7. Click **Deliver**.
8. Expect the courier to return to **Idle**, the active delivery to disappear and the page to show **Waiting for an offer…**. There should be no duplicate offer for the delivered order.

### E Verify the customer result and Reset

1. **Customer — `customer.demo@example.com`:** refresh the saved order-summary tab.
2. Expect **Order Status: DELIVERED**, the same dish and quantity, and the same price breakdown as checkout.
3. Click **See Order History**, then **Delivered**. Find the new order with the matching ID prefix. The delivered count should now be **2**, including the seeded delivered example.
4. **Operator — `operator.demo@example.com`:** return home and click **Reset demo**. Wait for **Demo data restored.**
5. Reload the Operator page and verify Open kitchens and **0 / 3 / 6** preparation counts.
6. **Customer:** refresh order history. The new order must be gone and the fixture history must remain, including **Delivered (1)** and **Refunded (1)**. The deleted order's old summary URL should no longer load an order; it may show **Unable to load your order.**
7. **Courier:** reload the home page. Expect **Offline**, **Start Driving** and no offer or active delivery.
8. **Cook:** reload **View Orders**. Sam must have no Incoming, Active or Ready orders. Click **Back to home** and check that Grandma's Tourtière is back to **6 in stock**, with its original **$18.50** price.
9. Repeat sections A to E once. A fresh order should complete without stale assignments or deleted order IDs reappearing.

Result: [ ] Pass [ ] Fail. First order ID: __________ Repeat order ID: __________

## Walkthrough 2 No Courier Available

Goal: show a Ready order waiting without a courier, then automatic dispatch when the courier becomes available.

### A Reset and keep the courier offline

1. **Operator — `operator.demo@example.com`:** restore and verify the starting point.
2. **Courier — `courier.demo@example.com`:** reload `/courier/home`. Confirm **Offline** and **Start Driving**. Do not click Start Driving yet.
3. **Cook — `restaurant.demo@example.com`:** confirm Sam's kitchen is open with no queued work.

### B Place and prepare an order while nobody is driving

1. **Customer — `customer.demo@example.com`:** click **Browse restaurants** on home.
2. Click **Sam's Family Kitchen**, then **Add to cart** once for **Grandma's Tourtière**.
3. Click **View cart (1)** and verify quantity **1**.
4. Click **Continue to Checkout**, fill the test payment form, then click **Pay and Place Order** once. Dismiss the success alert if shown.
5. Save the order-summary URL as **Waiting order**. Order ID: ______________________________
6. **Cook — `restaurant.demo@example.com`:** click **View Orders**, or reload the existing board.
7. Find this order in **Incoming Orders** and click **Accept**.
8. On that order in **Active Orders**, click **Start preparing**, then **Mark ready**.
9. Leave the order in **Ready**. Do not change the courier's availability yet.

### C Show that the order waits

1. **Customer:** refresh the saved summary. Expect **Order Status: READY**.
2. **Courier:** reload the home page. Expect **Offline**, no **New offer**, and no **Pick Up Order** button.
3. Leave the courier offline for about thirty seconds, then reload the Cook board and customer summary. The order must remain **Ready**; it must not disappear, become delivered or gain an active courier delivery.
4. **Operator — `operator.demo@example.com`:** from home, click **View all orders**, then **In progress**. Find the saved order and verify its **Ready** tag.
5. Record how the waiting order is visible to the operator. This revision exposes it through order history; it does not implement a dedicated no-courier popup. The operator-alert wording in issue 56 remains a separate acceptance interpretation to confirm, not a popup to invent for this test.

### D Make the courier available and finish delivery

1. **Courier — `courier.demo@example.com`:** click **Start Driving**.
2. Expect the waiting order to appear as **New offer** after a refresh or polling interval. Do not use any operator assignment action.
3. Confirm it is the same Waiting order and click **Accept**.
4. Click **Pick Up Order**, then **Deliver**, waiting for the page to update between clicks.
5. Expect the courier to return to **Idle**.
6. **Customer:** refresh the saved summary and confirm **DELIVERED**.

### E Reset and check that dispatch forgets the old order

1. **Operator:** click **Back to home**, then **Reset demo**. Wait for success.
2. Recheck the Open kitchens and **0 / 3 / 6** preparation counts.
3. **Customer:** refresh history. The Waiting order must be gone; the thirteen fixture orders remain.
4. **Cook:** reload Sam's board. All three columns should be empty.
5. **Courier:** reload and confirm **Offline** with no active delivery. Click **Start Driving** once more and wait about ten seconds.
6. Expect **Idle** and **Waiting for an offer…**, with no reappearance of the deleted Waiting order and no error about loading a deleted order.
7. **Operator:** Reset once more, then reload the Courier page to leave it **Offline** for the next walkthrough.

Result: [ ] Pass [ ] Fail. Order ID: __________ Operator visibility notes: __________

## Walkthrough 3 Sold Out and Paused

Goal: prevent ordering a seeded sold-out dish and prevent checkout when the operator pauses a kitchen after a customer has already selected an available dish.

### A Reset and show the sold-out dish

1. **Operator — `operator.demo@example.com`:** restore and verify the starting point.
2. **Customer — `customer.demo@example.com`:** make sure the cart is empty. From home, click **Browse restaurants**.
3. Click **Sam's Family Kitchen**.
4. Find **Pouding Chômeur**. Expect **$7.50**, a **Sold out** tag and a disabled **Add to cart** button.
5. Confirm the button cannot add that dish and the header remains **View cart (0)**.
6. Find **Grandma's Tourtière** and click its enabled **Add to cart** once. Expect **View cart (1)**. This shows an available dish still works in the same kitchen.

### B Leave a checkout open before the pause

1. **Customer:** click **View cart (1)** and check the dish and quantity.
2. Click **Continue to Checkout** and wait for the payment form.
3. You may fill the test card details, but **do not click Pay and Place Order**. Leave the checkout open so the kitchen can be paused after the cart was prepared.
4. Confirm the page has no availability error before the pause. The initial **Pay and Place Order** button should be enabled once availability checks finish.

### C Pause Sam from the operator account

1. **Operator — `operator.demo@example.com`:** open the operator home and locate **Sam's Family Kitchen** under **Restaurants**. Clear any search or status filter if needed.
2. Click **Pause** on Sam's row. Do not click another kitchen's button.
3. Wait for the row to show **Paused** and a **Reopen** button. Its waiting count should remain **No orders waiting**.
4. Click **View live map**. Find Sam's marker or click Sam in **On the map**.
5. Expect status **Paused**, queue **0 orders**, the paused terracotta marker colour, and **Reopen orders** in its details panel. The open/busy red and paused colours are different in this revision.

### D Verify the existing customer cart is blocked

1. **Customer — `customer.demo@example.com`:** return to the checkout left open in section B. Do not reload it, since a full reload clears the in-memory cart.
2. Allow the focus-triggered availability check to finish. If necessary, wait about fifteen seconds for polling.
3. Expect **This restaurant is not accepting orders.** and a disabled **Pay and Place Order** button.
4. If **Retry availability** appears, click it and confirm the blocked result persists while Sam is paused.
5. Confirm no success alert or new order-summary navigation occurs. Do not force-enable buttons or send crafted API requests as part of this browser walkthrough.
6. In a second Customer tab in the same Customer profile, open `/restaurants`. A fresh list should omit Sam while it is paused. Keep the checkout tab open.
7. **Operator:** click **Back to home**, then **View all orders** and refresh. Expect **All (13)**: preparing the cart and pausing the kitchen must not have created an order.

### E Reopen and restore the baseline

1. **Operator:** click **Back to home**, find Sam's row and click **Reopen**.
2. Wait for **Open** and a **Pause** button. On the live map, Sam should return to the quiet green colour with queue **0**.
3. **Customer:** return to the original checkout tab. Click **Retry availability** if shown, or allow focus/polling to refresh.
4. Expect the unavailable-restaurant message to disappear and **Pay and Place Order** to become enabled. Do not submit it; this walkthrough verifies reopening without creating an extra order.
5. Click **Back to Cart**, then **Clear cart**. Expect **Your cart is empty.**
6. **Operator:** Reset and wait for success. Recheck Open kitchens and **0 / 3 / 6** preparation counts.
7. **Customer:** reopen Sam's menu. Grandma's Tourtière should be available at **$18.50** and Pouding Chômeur should still be sold out. Reset restores fixture availability rather than making every dish available.

Result: [ ] Pass [ ] Fail. Sold-out check: __________ Pause and reopen check: __________

## Walkthrough 4 Dinner Rush

Goal: grow Robin's waiting preparation queue from three to six, observe the busy warning, pause new orders, process existing work in FIFO order, reopen and Reset.

### A Reset and switch the Cook account to Robin

1. **Operator — `operator.demo@example.com`:** restore and verify the starting point. Confirm Robin has **3 orders waiting** and an amber map marker.
2. **Courier — `courier.demo@example.com`:** reload the home page, confirm **Offline**, and leave it offline throughout this walkthrough.
3. **Cook:** if Sam is signed in, return to `/restaurant/home` and click **Log out**.
4. On `/login`, enter **Email:** `restaurant2.demo@example.com`, enter the demo password, then click **Login**.
5. Confirm the home page identifies **Robin's Sunday Kitchen** and shows **Accepted: 3**. Click **View Orders** and confirm the **Active Orders** column contains the three seeded accepted orders.
6. Do not click **Start preparing** yet. Preparing an order would reduce the count we are about to grow.

### B Place and accept the first additional order

1. **Customer — `customer.demo@example.com`:** confirm the cart is empty, return home and click **Browse restaurants**.
2. Click **Robin's Sunday Kitchen**.
3. Find **Handmade Spinach Ravioli**, priced at **$17.25**, and click **Add to cart** once.
4. Click **View cart (1)** and confirm quantity **1**. Click **Continue to Checkout**.
5. Fill the successful test payment form, click **Pay and Place Order**, then dismiss the success alert if shown.
6. Record the full summary URL as **Rush order A**. Click **Back Home** when ready to create the next order.
7. **Cook — `restaurant2.demo@example.com`:** reload **View Orders**, find Rush order A under **Incoming Orders**, and click **Accept**. Leave it accepted; do not start preparing.
8. **Operator:** return to the home list and allow it to refresh. Expect Robin's waiting count to increase **3 → 4**, still in the amber/steady band.

### C Place and accept two more separate orders

1. **Customer:** repeat the exact clicks in section B for one more **Handmade Spinach Ravioli**, quantity **1**. Save this new summary URL as **Rush order B**.
2. **Cook:** reload the board and click **Accept** on Rush order B. Do not prepare it.
3. **Operator:** verify Robin's waiting count increases **4 → 5** and remains amber.
4. **Customer:** repeat once more for a third separate Ravioli order, quantity **1**. Save its URL as **Rush order C**.
5. **Cook:** reload the board and click **Accept** on Rush order C. Leave it accepted.
6. **Operator:** verify Robin's waiting count increases **5 → 6**. Use three separate orders; one order with quantity three does not create three queue entries.

Order A: ____________________ Order B: ____________________ Order C: ____________________

### D Observe the busy state and pause the kitchen

1. **Operator — `operator.demo@example.com`:** check Robin's row on the home page. Expect **6 orders waiting**, red busy text and the warning icon beside the queue count. Status should still be **Open**.
2. Click **View live map**. Expect Robin's marker to be red in the **Busy (6+ orders)** band. Select Robin in **On the map** and verify **6 orders** in its details.
3. **Customer:** before pausing, prepare a fourth Ravioli cart by clicking **Back Home → Browse restaurants → Robin's Sunday Kitchen → Add to cart → View cart (1) → Continue to Checkout**. Leave it open without clicking **Pay and Place Order**. This cart is not an additional order.
4. **Operator:** click **Back to home**, then **Pause** on Robin's row. Expect **Paused** and **Reopen**, with **6 orders waiting** still visible.
5. On **View live map**, expect Robin's marker to use the separate paused terracotta colour. The operator home queue indicator should still show its busy workload.
6. **Customer:** return to the fourth checkout and allow availability to refresh. Expect **This restaurant is not accepting orders.** and disabled **Pay and Place Order**. No fourth order should be created.

### E Process existing work while paused and check FIFO

1. **Cook — `restaurant2.demo@example.com`:** reload **View Orders**. The header should show **Paused** and **Reopen orders**. Leave the kitchen paused.
2. In **Active Orders**, find the accepted order whose **Start preparing** button is enabled. This should be the oldest waiting accepted order, one of the seeded orders rather than Rush A, B or C.
3. Check a later accepted order. Its **Start preparing** button should be disabled; its hover text explains **Orders are prepared in the order they were accepted.** Do not mistake the shared first six characters on fixture IDs for unique IDs; use the enabled action, item and time to identify the queue head.
4. Click the enabled **Start preparing** on the oldest order.
5. Expect **In preparation** and **Mark ready**, even though the kitchen remains paused. Pausing blocks new orders; it should not stop existing kitchen work.
6. **Operator:** refresh the home list. Expect Robin's waiting preparation count to drop **6 → 5** while status remains **Paused**. The Cook board can still show six Active Orders because it includes the one now being prepared.
7. **Cook:** click **Mark ready** on the order just started. Expect it in **Ready**. The next-oldest accepted order should now have the enabled **Start preparing** action.
8. **Operator:** on the map, Robin remains in the paused terracotta colour while paused, regardless of the reduced queue.
9. Back on the operator home, click **Reopen** on Robin's row. Expect **Open**, **5 orders waiting**, and an amber/steady map marker after refresh.
10. **Customer:** return to the fourth checkout and refresh availability. Confirm ordering would be enabled again. Click **Back to Cart**, then **Clear cart** without placing this order.

### F Reset the dinner rush

1. **Operator:** click **Reset demo** on home and wait for **Demo data restored.**
2. Confirm all three kitchens are Open and the preparation counts return to **Sam 0 / Robin 3 / Casey 6**.
3. Click **View live map** and confirm green/amber/red markers for Sam/Robin/Casey respectively.
4. **Cook — Robin:** reload **View Orders**. Expect exactly the three original accepted orders, no Incoming orders, no **In preparation** tag and an empty **Ready** column. Rush A, B and C must be gone. Click **Back to home** and confirm Handmade Spinach Ravioli is restored to **10 in stock** at **$17.25**.
5. **Customer:** refresh **Order history → All**. Expect the thirteen fixture orders and no Rush A, B or C. The prepared fourth cart must also be empty after your explicit Clear cart action.
6. **Courier:** reload the home page and confirm **Offline** with no offer or delivery.

Result: [ ] Pass [ ] Fail. Queue growth: __________ Pause and FIFO: __________ Reset: __________

## Additional Reset checks

These checks verify restoration reliability. They are not additional required stakeholder walkthrough paths. Run them after the four walkthroughs above, using only agreed disposable test data.

### Reset during a pending offer

1. Reset, then use the Happy Path accounts and buttons through **Cook → Mark ready** with the courier online.
2. Wait until **Courier → New offer** appears. Do not click **Accept**.
3. **Operator:** click **Reset demo** and wait for success.
4. Reload the Courier, Cook and Customer pages. Expect no old offer or order, Courier **Offline**, and kitchen counts **0 / 3 / 6**.
5. Click Courier **Start Driving** and confirm the deleted offer is not offered again. Reset once more before the next check.

### Reset after acceptance but before pickup

1. Repeat Happy Path through **Courier → Accept**. Stop when **Pick Up Order** is shown.
2. **Operator:** Reset and wait for success.
3. Reload the Courier page. Expect **Offline**, no active delivery and no **Pick Up Order** action for the deleted order.
4. Verify the new order is gone from customer history and the kitchen counts return to **0 / 3 / 6**.

### Reset after pickup but before delivery

1. Repeat Happy Path through **Courier → Pick Up Order**. Stop when **Deliver** is shown.
2. **Operator:** Reset and wait for success.
3. Reload the Courier page. Expect **Offline**, no active delivery and no **Deliver** action for the deleted order.
4. Verify the new order is gone and **0 / 3 / 6** is restored.
5. Complete a fresh Happy Path afterward to confirm the courier can receive and finish another order.

### Reset twice without changing anything

1. **Operator:** Reset and record the baseline counts.
2. Click **Reset demo** a second time after the first has finished.
3. Confirm the second operation also succeeds, does not duplicate fixture orders, and leaves the same **0 / 3 / 6** counts and thirteen fixture orders.

Auth deletion and registration reuse are separately covered by isolated tests. If manually checking them, use a team-approved disposable account and verify both its application profile and Auth identity are removed; do not interpret fixture-account login as proof that extra accounts were deleted. A queue-rebuild failure after database commit must report failure and keep ordinary requests blocked until Reset succeeds on retry; deliberately inducing that failure belongs in isolated tests, not this shared browser demo.

## If a step does not match

1. Stop progressing that order and note the walkthrough section and step number.
2. Open **Output console** at the bottom of the page. Copy the relevant command and error text, without passwords, tokens or payment details.
3. Record the account email, page URL, full order ID if available, expected result and actual result. Screenshot the visible state if useful.
4. Refresh pages that are meant to be refreshed, allowing the documented polling interval. Do not repeatedly submit **Pay and Place Order** while waiting.
5. If the problem persists, mark that check **Fail** or **Blocked**. A later Reset does not turn the failed check into a pass.
6. If Reset itself fails, stop the next walkthrough. Capture its error. The backend may keep ordinary requests unavailable after a queue-rebuild failure until a successful Reset retry.

A browser checklist verifies visible availability controls and normal workflow behaviour. It does not by itself prove forged-request rejection, concurrency safety, payment-provider confirmation, Auth cascades or cross-process coordination. Use the relevant backend tests and provider checks for those claims.

## Record the results

Check off each result only after executing it. The database baseline audit does not substitute for the browser runs below.

| Check | Commit tested | Result | Order IDs and notes |
|---|---|---|---|
| Reset baseline | | Not run in browser | |
| Happy Path and Reset | | Not run | |
| Happy Path repeated after Reset | | Not run | |
| No Courier Available and Reset | | Not run | Note operator visibility separately |
| Sold Out and Paused and Reset | | Not run | |
| Dinner Rush and Reset | | Not run | Record A, B and C |
| Reset during pending offer | | Not run | |
| Reset after courier acceptance | | Not run | |
| Reset after pickup | | Not run | |
| Repeated Reset without changes | | Not run | |

For submission, repeat the required scenarios on the team's final `phase3` tagged revision when available. This guide reflects the checked commit; recheck button labels and behaviour if later UI, dispatch or pricing changes merge. Manual courier Swap and deeper payment-failure testing are outside these four walkthrough scripts.
