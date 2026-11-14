# Phase III demo walkthroughs

This is the manual verification checklist for issue #56. The steps and expected results are prepared; the result table below is intentionally unfilled until the team runs them.

Phase III instructions, section 9.1 (page 11), require a live run from Reset showing **Happy Path Order** and **No Courier Available**. The demonstrator may then request **Sold Out / Paused** or **Dinner Rush**. Prepare all four. The detailed scenario wording is reproduced in [issue #56](https://github.com/E-tin30/SOEN343_HI_The-Mosby-Boys/issues/56).

## Setup and starting state

1. Use the merged `phase-3` code, start the backend/frontend, and use the [demo accounts](../README.md#demo-accounts). Run the initial seeder only if setup has not already provisioned those accounts. Seeding inserts missing data; Reset restores the complete fixture starting state for everyone.
   Reset deletes all orders, lines, events and bills before restoring fixture history. Accounts and menus not defined in the fixtures are removed, including non-fixture `auth.users` rows and orphaned Auth accounts without an application profile. This affects the whole configured application database, regardless of who created the records. The six fixture Auth accounts retain their IDs and logins; deleted accounts' emails can be registered again. Auth deletion shares the application restoration transaction, so a failure rolls both back.
2. Use separate browser profiles for the customer, cook, courier and operator. Tabs in the same profile share login storage. Keep the customer's order-summary tab after checkout so it can be refreshed after delivery.
3. Use one backend for all demonstration sessions and stop other backends/writers. Database and queue rebuilding are coordinated within one backend; other backend processes would retain their own stale in-memory queues.
4. After Reset, verify that the two fixture couriers (Jo and Riley) are the only application couriers and are offline. Other couriers and their assignments are removed by global restoration. Keep other writers stopped during the walkthrough so new orders/couriers cannot interfere with dispatch.
5. Sign in as `operator.demo@example.com` and choose **Reset demo**, immediately to the right of **Log out**. Wait for **Demo data restored.** Use **View live map** on the operator home to observe the map.

| Kitchen | Cook account | Preparation count | Map colour after Reset |
|---|---|---:|---|
| Sam's Family Kitchen | `restaurant.demo@example.com` | 0 | Green |
| Robin's Sunday Kitchen | `restaurant2.demo@example.com` | 3 | Amber |
| Casey's Home Kitchen | `restaurant3.demo@example.com` | 6 | Red |

All three kitchens start open. There are 12 dishes (9 available, 3 sold out), and both couriers start offline at their fixture locations. Jo is nearer every kitchen than Riley. The 13 fixture orders include nine accepted orders and one each delivered, refunded, rejected and cancelled, with 13 lines and 35 events. Delivered/refunded fixture history remains after every Reset.

Allow a dashboard refresh (normally five seconds while visible) or refresh/revisit other open pages after Reset. Clear a remaining customer cart before starting another scenario; the cart belongs to the browser session, not the database fixture.

Deleting an Auth user removes its sessions and prevents new login/refresh, but an already-issued access token can remain valid until expiry. Reset does not force every browser to log out immediately. See [Supabase user deletion](https://supabase.com/docs/guides/auth/managing-user-data#deleting-users).

## 1. Happy Path Order

1. Reset and check the starting state above. As the demo courier, select **Start Driving**; expect an idle courier.
2. As the customer, browse Sam's kitchen, add one available dish, and use **Pay and Place Order** with the team's configured test payment setup. Record the new order and retain its summary page.
3. As Sam, open **View orders**. Accept the new order, then choose **Start preparing** and **Mark ready** in order.
4. As the courier, wait for the offer. To show the nearest-courier rule (R-DLV-02), put Riley on duty as well: the offer still goes to Jo, who is nearer. Select **Accept**, then **Pick Up Order**, then **Deliver**. The courier should move through idle, assigned, carrying and idle.
5. Refresh the customer's summary page. Check the delivered state, dish/quantity and price breakdown against the configured fees. To show the small-order surcharge, order a dish under the threshold, such as Cinnamon Churros. Check that the delivered order also appears in order history.
6. Reset. Confirm that this new order is gone, fixture history remains, the courier is offline at the initial location, and the preparation counts return to 0 / 3 / 6. Repeat the path once to check repeatability.

## 2. No Courier Available

1. Reset and confirm every courier is offline. Use Sam's kitchen so existing preparation work does not hide the new order.
2. As the customer, place one available-dish order. As Sam, accept it, start preparing and mark it ready.
3. Check that the order remains ready while no courier is eligible. No courier should receive an offer yet.
4. As the operator, expect the alert above the restaurant list: "1 ready order is waiting for a courier."
5. As the demo courier, choose **Start Driving**. Expect the waiting order to be offered automatically, without an operator assignment command. Accept, pick up and deliver it.
6. Reset and verify the starting state and absence of the walkthrough order. The global dispatch queue must not retain its deleted ID.

## 3. Sold Out / Paused

1. Reset. As the customer, open a kitchen's menu and verify that its seeded sold-out dish is visibly unavailable and cannot be ordered.
2. Add an available dish from Sam's kitchen to the cart while Sam is open. Leave the customer on that cart page.
3. As the operator, pause Sam from the restaurant list. Expect **Paused** with **Reopen**, and a red map marker even with a short preparation queue.
4. Return to the customer cart and allow availability to refresh. Expect **Pay and Place Order** to be disabled with an explanation. A stale submission must also be refused by the backend, without creating an order.
5. Reopen Sam as the operator, clear the test cart, then Reset. Verify open kitchens, fixture availability/stock and 0 / 3 / 6 queues.

## 4. Dinner Rush

1. Reset. Open the operator list and map, and sign the cook profile into Robin's kitchen (three accepted orders).
2. Place and accept three more customer orders at Robin's, without starting preparation. Expect the preparation count to grow from 3 to 6. Count accepted orders, rather than incoming orders or orders already being prepared.
3. Expect Robin's map marker to turn red, its operator-list queue count to show the busy colour and warning icon, and the operator alert "Robin's Sunday Kitchen has 6 orders queued. Consider pausing it."
4. Pause Robin as the operator. Verify that new customer orders are blocked while the cook can continue processing already accepted orders in FIFO order.
5. Process enough waiting work to reduce the preparation count below six. The marker remains red while paused; reopen the kitchen to see the colour follow the reduced count.
6. Reset. Confirm Robin returns to three accepted orders, all kitchens are open, test orders disappear and fixture stock is restored.

## Additional Reset checks

These check restoration reliability; they are not extra required demonstration scenarios.

- Repeat Reset while a demo order has a pending offer, after acceptance, and after pickup. Refresh the courier page and verify the old delivery/offer disappears, the courier is offline, and the next walkthrough can start normally.
- Place or progress orders using any accounts, then Reset. All added orders and billing/history records must disappear, including orders between accounts outside the fixtures. Fixture order history is reinstated.
- Create or edit application profiles or menu items, then Reset. Only the six fixture Auth accounts/profiles and twelve fixture dishes remain, with their configured roles, prices, availability, stock and locations. Register again with a deleted account's email to confirm it is reusable, then Reset again. Use the fixture accounts for the next walkthrough.
- Repeat Reset without changing anything. The same fixture state, 0 / 3 / 6 preparation counts and empty dispatch queue must result.
- A queue-rebuild failure after the database restores must report failure and keep normal requests blocked until Reset succeeds on retry. This failure/recovery behavior is covered by isolated tests; do not deliberately interrupt the shared demonstration backend.

## Not shown from Reset

Reset always seeds restaurants, so the empty-map handling (R-DASH-06) cannot be run from it. If asked, show where the map handles an empty dataset in the code.

## Manual execution record

Record the exact commit tested and any remaining gaps. Do not equate automated checks with a browser demonstration. Once the team creates the `phase3` submission tag, demonstrate that tagged commit.

| Scenario | Commit | Result | Observed gaps / notes |
|---|---|---|---|
| Happy Path + Reset + repeat | | Not run | |
| No Courier Available + Reset | | Not run | |
| Sold Out / Paused + Reset | | Not run | |
| Dinner Rush + Reset | | Not run | |
| Reset during offer / accepted / carrying | | Not run | |

Pending team features such as manual courier Swap/reassignment and menu editing should be checked after they merge, but are separate from this Reset safeguard. Reassignment is not an additional requirement of these four scenario paths. The current fixture data does not need expanding to stage the four paths.
