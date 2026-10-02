# DK/Phar Inventory: project notes

Keep this file in the root of BOTH projects (or one shared notes folder) and update it after each step.
To continue in a new chat, paste this file first. It saves explaining everything again.

## 1. The two projects

| | Frontend | Backend |
|---|---|---|
| Folder | `D:\Angular\inventory-project` | `D:\Inventory\Inventory_System` |
| Tech | Angular 22, standalone components, signals, `@if/@for` | Spring Boot 4.1.1, Java 26, Lombok, Hibernate 7, MySQL |
| Runs on | `ng serve` on port 4200 | port 8080, database `inventorydb` |
| Link | dev proxy: `/api` goes to `localhost:8080` (proxy.conf.json) | Angular is served separately in development |

Backend package: `com.api.inventory` (config, controller, dto, entity, exception, repository, service).
Frontend: pages in `src/app/Components/<name>/`, services in `src/app/services/`, shared helpers in `src/app/utils/`,
guards in `src/app/guards/`, interceptors in `src/app/interceptors/`. Pages load lazily (`app.routes.ts`).

## 2. How we work

- Small steps. One change at a time, with exact "find this, replace with that" edits. Brand-new files are given whole.
- Each step is tested before the next (money logic is tested with real numbers).
- Do not create or change database tables by hand. Entities define tables (Hibernate `ddl-auto=update` for now).
- After restarting the backend, sign in again (sessions are kept in the server's memory).

## 3. Sign-in, roles and permissions (current state)

- Login stores `userEmail` and `userRole` in the HTTP session (AuthController). The browser also remembers the login in local storage.
- `sessionExpiredInterceptor` signs the browser out when the server says 401.
- Roles table: 1 ADMIN, 2 MANAGER, 3 CONTROLLER, 4 USER (customer).
- Permissions live in `src/app/utils/permissions.ts` (the single table for the screens: menu, pages, buttons).
  The returns endpoints also read `app.returns.roles` (default ADMIN,MANAGER).
- IMPORTANT: Spring Security is `permitAll`. Only the returns endpoints check the role on the server.
  Everything else is hidden in the browser only, not protected on the server.

## 4. What is built

Customer side: home, products (search, categories, add to cart), product detail, cart, checkout (cash on delivery or bank transfer),
order success, my orders, order details with progress tracker, login, signup, profile, contact, about, services, reviews.

Staff side (staff bar under the header, by permission): point of sale, sales history (periods, custom range, return button),
order list (confirm payment, confirm, ship, deliver), verify payments (ask for info, reject with reason), add/edit product,
restock, customer messages, sales dashboard, users (change role, add user).

Point of sale: product tiles, sale panel (items left, checkout right), exact discounts (counter price equals shop price),
discount on the whole sale, GST, customer phone (8 digits), cash change, invoice (A4, receipt, PDF), full-screen mode.

Returns (backend + screen): `GET /api/orders/{id}/returnable`, `GET` and `POST /api/orders/{id}/returns`.
Refund per unit = order_items.unit_price x (1 + that sale's tax rate). Window 7 days. Items can be returned line by line,
in several visits. Stock goes back up (unless damaged). Logged in `transactions` as RETURN / RETURN_DAMAGED.
Credit note prints as A4 or receipt.
Sales history shows refunds: `GET /api/returns/summary` (ReturnSummaryController) lists every sale with returns and whether it is
fully returned. The list shows a "Refunded Nu. X" or "Fully returned" tag, hides Return on fully returned sales, and the top box
shows Net sales (sales minus refunds on those sales).

## 5. Database (inventorydb)

Tables: contact_messages, inventory_stock, item_master, order_items, orders, payments, roles, shipments, tax_details,
transactions, users, sales_returns, sales_return_items.

- `orders`: order_id, address, created_at, customer_email, customer_name, customer_phone, discount_amount, note, order_status,
  payment_method, payment_status, shipment_id, source (NOT NULL, e.g. POS), tax_amount, total_amount, updated_at, updated_by
- `order_items`: order_item_id, item_id, order_id, quantity, unit_price (unit_price is the price AFTER discount, before tax)
- `inventory_stock`: stock_id, current_quantity, item_id (unique), last_updated, status (always 'Available')
- `transactions` (the stock/sales ledger): created_at, customer_or_supplier, item_id, notes, quantity, reference_id,
  reference_type (POS_SALE, ORDER, SALES_RETURN), transaction_type (SALE, PURCHASE, RETURN, RETURN_DAMAGED), unit_price
- Times are stored in UTC. New entities use `Instant` so the screen shows the right local time.

## 6. Rules that must not be broken

- Money is `DECIMAL`, added in whole cents. Discounts use 8 decimals so totals match to the cent.
- A refund is what the customer actually paid for that item (never the list price) and never more than the sale total.
- Never trust the browser for permissions or prices. The server decides.

## 7. Known issues and to-do

1. Server does not enforce roles (except returns). Must be done before other businesses use the system.
2. AuthController: signup returns the User (including the password hash); a wrong password gives a 500 error, not 401.
3. Sessions are lost when the backend restarts.
4. Sales history shows refunds and net sales. The DASHBOARD still counts refunded sales in full (needs the ReportController).
5. Customer lookup by phone (`/api/customers/lookup`) is not built. The POS screen is ready for it.
6. No change-password or forgot-password. People cannot change a password an admin gave them.
7. Old static HTML/CSS/JS still sits in the backend's `static` folder. Commit both projects to Git. angular.json budgets raised (see 17).
8. Tests written during development are not stored in the projects yet.

## 8. Plan: other shopkeepers (sellers), delivery people, commission

Decisions still open: marketplace model, commission % (2-3%) and what it is worked out on, who collects the money,
delivery fee and rider pay, one seller per order or mixed carts, payouts, messaging.

Planned order (foundations first): server security, one home for permissions (database), sessions that survive restarts,
versioned database changes (Flyway), then sellers and roles, products owned by sellers, commission ledger, delivery, payouts.

## 9. Server security review (controllers read on 1 Oct 2026)

Finding: nothing enabled method security, so every @PreAuthorize was ignored, and "who did this" was always "system"
(getCurrentUserEmail() asked Spring Security, which never knew the person). The role CASHIER does not exist.

STATUS: 1A and 1B are written and tested (1B needs 1A installed first). Not yet confirmed running on the real backend.

Plan (small steps, each tested):
- 1A  Tell Spring Security who is calling (SessionAuthenticationFilter), switch on @PreAuthorize (MethodSecurityConfig),
      clear 401/403 answers (SecurityExceptionAdvice), server permission table (Permissions.java = permissions.ts).
      Fix the 3 existing annotations (pos.use, users.manage).
- 1B  Protect every other open endpoint with @PreAuthorize("hasAuthority('...')"):
      items: add/update/delete = items.manage; search = stock.restock or items.manage
      orders: confirm/ship/complete/cancel = orders.fulfil; verify/confirm-payment/status list = payments.verify;
      admin orders: list = orders.view, actions = orders.fulfil; payments: status/confirm = payments.verify;
      contact list = messages.view; reports = reports.view; returns = sales.return (+ view roles).
- 1C  Owner checks for customers: my orders, order details/items, cancel, payment of own order (stop reading other people's orders).
- 1D  AuthController: signup must not return the password hash; wrong password = 401; change password.
- 1E  Controllers not yet seen: restock (/api/transactions/purchase) and /api/reports/sales(+summary).
      Hide cost price from the public items API. Check OrderService.createOrder recomputes prices on the server.
Also: remove old @CrossOrigin on Item/Contact controllers, the test endpoint /api/orders/api/test-auth, and whitelist
the statuses accepted by PUT /api/orders/{id}/verify.

Step 1B result (35 endpoints audited, 4 public on purpose: items list, item page, item stock, contact form):
- security/CurrentUser.java added (who is calling, from inside a controller).
- Public items API no longer shows costPrice, markupPercent, supplierItemCode unless the caller has items.manage.
- Admin cannot change their own role (server rule).
- Removed: old @CrossOrigin lines, test endpoint /api/orders/api/test-auth.
- Still to do: 1C owner checks (customers can still read/cancel other customers' orders by number), 1D AuthController + cookie
  settings (CSRF is switched off, so session cookies must be SameSite), safety net (move the session filter inside SecurityConfig's
  chain, then default-deny for anything without a rule), migrate SalesReturnController/ReturnSummaryController to @PreAuthorize,
  find the controller behind /api/reports/sales and /api/reports/sales/summary (dashboard).

Step 1C (part 1) written and tested: security/OrderAccess.java. A customer can read, list and cancel only THEIR OWN orders
(same email); staff with orders.view / pos.use can read any; customers can cancel only while the order is CREATED or PENDING.
Used by OrderController (cancel, customer/{email}, {id}/items, {id}).
Still open in 1C: POST /api/orders (the email and the prices come from the browser) and POST /api/payments (any order id).
These need OrderServiceImpl.java, OrderRequestDTO.java, PaymentRequestDTO.java (OrderService.java is only the interface).

## 10. Order flow facts found in OrderServiceImpl (1 Oct 2026)

- Online order: the server uses the price from item_master (the price sent by the cart is ignored). Good.
- Counter (POS) sale: the browser sends mrp, discountPercent and the tax types/rates. The server used them as sent.
- Order statuses: CREATED, PENDING, CONFIRMED, SHIPPED, COMPLETED, CANCELLED. Payment statuses: PENDING, PAID, PARTIALLY_PAID, PENDING_INFO, REJECTED, FAILED.
- Two ways to confirm: "Confirm payment" (OrderServiceImpl.confirmPayment, used by the Order list) sets CONFIRMED + PAID and takes stock but wrote
  NO SALE row to the transactions ledger; "Confirm order" (processOrderConfirmation) takes stock AND writes the SALE row.
- The checkout calls PUT /api/orders/{id}/status after paying. That endpoint does not exist (the app ignores the error), so orders stay CREATED
  and the customer "Cancel order" button (shown only for PENDING) never appears.

Step 1C (part 2), written and tested against a stand-in database (21 checks):
- OrderServiceImpl edits: quantity must be at least 1 (online and counter), an order total must be above zero, counter MRP must match the shop's
  MRP (else "price has changed, reload"), SHIPPED/COMPLETED orders cannot be cancelled (use Return). Optional: write the SALE row in confirmPayment.
- OrderController: a customer can only place an order with their own email (OrderAccess.requireOwnEmail).

Still open: POST /api/payments (amount and status come from the browser; needs PaymentServiceImpl and PaymentRequestDTO), POS tax rate and
discount limits are decided by the browser, PUT /api/orders/{id}/verify accepts any status text (Manager/Admin only).

Correction (after reading the real OrderRequestDTO): an online order with NO items was already refused (getNormalizedItems() throws), so that was
never a hole. The negative / zero quantity hole (items array) was real.

Step 1C (part 3), written and tested (14 checks): PaymentController.createPayment
- only the customer who placed the order can create its payment (staff cannot); no order number = bad input
- the server sets the amount (the order's real total) and the status ("pending"); the browser's values are ignored
- pressing Pay twice returns the same pending payment (recorded once); a processed or rejected payment cannot be created again
Needs OrderAccess (Step 1C) installed first.

Found: the payment page takes the total from the URL (/payment?orderId=12&total=450), which comes from prices stored in the browser's cart.
The recorded amount is now the server's, but the page can still SHOW a stale number. Planned fix: the page loads the order total from the server.
Next: 1D (AuthController: signup must not return the password hash, wrong password = 401; cookie settings in application.properties),
then the safety net in SecurityConfig, then the sellers / riders / commission plan.

## 11. Done on 1-2 Oct 2026 (Claude Code session)

Security (both compiled, checked against the running backend with curl):
- 1C part 3 installed: PaymentController/PaymentServiceImpl. Owner only, server sets amount + "pending", double Pay returns the same payment.
  Online orders must pay by bank transfer with a journal number (no cash on delivery: riders never carry cash).
- 1D installed: AuthController. Signup validates on the server (name, email, 8-digit phone, password >= 6), never returns the hash,
  duplicate = 409; wrong login = 401 (same message for unknown email); new session id at sign-in; POST /api/auth/change-password
  (profile page has the form). GET /me refreshes the role in the session (a newly approved seller/rider does not need to sign in again).
- PUT /api/orders/{id}/verify now only accepts known statuses. Marking an order PAID also confirms it (stock taken once).
- CorsConfig allows http://localhost:[*] and 127.0.0.1:[*] (the preview dev server may not get port 4200).
- Order page now really shows name/phone/address/shop note (OrderResponseDTO was missing them).

## 12. Marketplace (sellers, riders, commission) - BUILT

Decisions (from the owner): customer pays first (bank transfer), then delivery; sellers and riders register themselves and an admin approves;
commission is set by the admin (default + per seller); riders are paid by us per delivery; mixed carts are split per seller.

Roles: SELLER (seller.portal), RIDER (rider.portal). ADMIN also has marketplace.manage. MarketplaceInitializer creates the roles
and the settings row on start-up (no manual SQL).

New tables (Hibernate creates them): seller_profiles, rider_profiles, marketplace_settings (1 row), order_packages, earnings_ledger.
New columns: item_master.seller_id (empty = our shop), order_items.package_id, orders.delivery_fee.

Flow of one online order:
1. Checkout: server checks stock/active/seller approved, prices from item_master, splits lines into one package per seller,
   freezes commission %, delivery fee and rider pay on each package, makes a 4-digit delivery code. Total = items + fee x packages.
2. Customer pays by bank transfer (journal number). Staff mark it Paid in Verify payments -> stock taken, packages TO_PACK.
3. Seller (or staff for our own products) marks the package packed -> READY_FOR_PICKUP (riders see the job).
4. Rider accepts (row lock: only one rider gets it, max 5 active jobs) -> ASSIGNED, picks up -> PICKED_UP (order SHIPPED),
   delivers with the customer's code -> DELIVERED. All packages delivered -> order COMPLETED.
   Staff can do any step (deliver ourselves = no rider pay; staff do not need the code).
5. On delivery the earnings_ledger gets: seller SALE = subtotal - commission; rider DELIVERY = rider pay.
   Admin records payouts (never more than owed) with the bank journal number. Balance = sum of the ledger.

Money rule: commission = round(subtotal x rate / 100, 2 places, half up); seller gets subtotal - commission.
Changing settings or a seller's rate affects NEW orders only.

Screens: /sell and /deliver (apply), /seller (overview, packages, products, money), /rider (open jobs, in progress, money),
/admin/marketplace (applications, commission, payouts, settings), /admin/deliveries (all packages, staff actions).
Order page shows each package, the rider, and the delivery code. Cart and payment show the delivery fee. Products show "Sold by".
POS refuses seller products (they are sold online only).

Tests: src/test/java/com/api/inventory/MarketplaceFlowTest.java (H2 in memory, never the real database). Full flow with real numbers:
3 x 249.99 at 2.5% -> commission 18.75, seller 731.22; +120 shop item +2 x 40 delivery = 949.97; rider 55; part payout 700 leaves 31.22.
Also refuses: wrong code, seller touching another package, packing before payment, payout above balance, overselling.
Run: mvn test   (SalesReportDTO got an extra constructor so H2 accepts the MySQL DATE() report query.)

Still to do (marketplace):
- Returns/refunds on seller packages do not yet reverse the seller's ledger entry (record an ADJUSTMENT by hand until then).
- Old orders (before packages) still use the old Ship/Complete buttons; new orders go through Deliveries.
- Notifications (SMS/email) to sellers and riders when a job appears.
- Safety net in SecurityConfig (default-deny) is still open; sessions still lost on restart.

## 13. Done on 2 Oct 2026: access control in the database, safety net, production POS

Permissions now live in the database (role_permissions). security/Permissions.java is only the CATALOG (what can be
given, with labels) and the first-time defaults. AccessControlService creates the built-in roles (ADMIN, MANAGER,
CONTROLLER, SELLER, RIDER, USER) and seeds permissions once (roles.permissions_initialized); admin choices are never overwritten.
ADMIN always has everything. New permissions: pos.discount, pos.shifts.manage.

People & access screen (/admin/users): People (add person, change role, switch account off/on, reset password with a
one-time temporary password), Roles & permissions (tick boxes per role, create custom roles such as CASHIER, delete unused),
Activity (audit_log). Server rules: nobody changes their own account or own role; only an ADMIN hands out users.manage /
marketplace.manage or the ADMIN role; always one active ADMIN; SELLER/RIDER only via Marketplace approval
(leaving those roles pauses the seller/rider account).

Security safety net: SecurityConfig puts SessionAuthenticationFilter INSIDE the chain and requires sign-in for every /api
address except: auth login/signup/logout/me, GET items list/item/stock, GET marketplace settings, POST contact.
The filter reads the user from the database on every request: role changes work on the next click, switched-off
accounts are signed out. Login/me return "permissions"; the Angular app uses them (AuthService.can).
Fixed: /api/reports/sales and /summary were open to anyone. Returns endpoints now use permissions (not role lists).
GlobalExceptionHandler: same JSON shape everywhere; faults return a reference number and are logged, details never leak.

POS:
- Cash drawer shifts (pos_shifts): open with a float, every sale is tied to the shift and the cashier, close with a count.
  Expected cash = float + cash sales - cash refunds (returns this cashier took during the shift). A difference needs a note.
  End-of-shift report prints on the receipt printer. Sales history has a "Cash drawers" view (all cashiers with pos.shifts.manage).
- Server checks: payment method list, phone, tax types/rates (0-50%, each once), discounts (shop price always allowed;
  more needs pos.discount and never above the product's max discount), cash received >= total.
- Duplicate protection: each sale has a clientRef; sending it again returns the first sale (no double stock or money).
- Stock is taken atomically (takeIfAvailable): two tills cannot sell the last unit twice. Same for online confirmations.
- Customer lookup by phone (/api/customers/lookup): name, visits, total spent.
- Till: hold/recall up to 10 sales, unfinished sale survives refresh/crash, exact barcode match first,
  discount boxes locked without pos.discount.

Tests (mvn test, 7 tests, H2 only): MarketplaceFlowTest, PosAndAccessTest (roles rules, shift numbers, discounts,
duplicates, cash short Nu. 10), SecurityHttpTest (real HTTP through the security chain).

Next ideas (not built): sessions that survive a restart (spring-session-jdbc), forgot-password by email/SMS,
returns on marketplace packages reversing seller earnings, low-stock alerts, backups, HTTPS + secure cookies when online.

## 14. Agreements (terms) and seller/driver registration (2 Oct 2026)

Agreements: legal_terms (versioned, never edited after publishing), terms_acceptances (who, version, time, IP, browser).
Types: SELLER (Seller Agreement), RIDER (Driver Agreement), CUSTOMER (Terms of Use and Privacy). Version 1 of each is loaded on first
start from src/main/resources/legal/*.txt (a template: have it checked by a legal adviser). Admins edit and publish new versions in
Marketplace > Agreements. Public pages: /terms, /terms/seller, /terms/driver (?v=N for an older version).
- Signup requires ticking the current customer terms (recorded).
- Applying as seller/driver requires ticking the current agreement + "information is true, I am 18+" (recorded).
- A new SELLER/RIDER version: the dashboard answers 428 TERMS_REQUIRED until the person accepts it (terms gate screen).

Registration (/sell, /deliver): account > 1 Details > 2 Documents > 3 Agreement.
- Seller: CID (11 digits, unique per seller), trade licence no. and TPN (optional), CID copy required, trade licence copy optional.
- Driver: CID (unique), licence number + expiry (must be in the future) for motor vehicles, emergency contact (different phone),
  CID copy and licence copy required (bicycle / on foot: no licence).
- Documents: partner_documents, files in private-uploads/partners (NOT public, in .gitignore), JPG/PNG/PDF checked by content,
  max 5 MB; admins open them from the application card (GET /api/marketplace/admin/documents/{id}).
- Drivers with an expired licence cannot be approved or take jobs; they send the renewed licence from My deliveries
  (reminder from 30 days before expiry).
- Ledger adjustments (Marketplace > Payouts > Adjust): minus to take money back (refund for a faulty product), plus to add, with a reason.
- Upload limits set: 6MB per file, 25MB per request (Spring's default was 1MB).

## 15. Customers and choosing Seller / Driver in People (2 Oct 2026)

Customers: table customers (one per person: account first, then email, then phone; a phone is only joined when it
cannot belong to someone else). orders.customer_id links every order. Linked on: online order, counter sale with a phone,
signup, admin-created customer. On start-up all older orders are linked once (orders.updated_at is NOT touched).
Staff page /admin/customers: search, orders count, money spent, history, staff notes. Permissions customers.view
(Manager, Controller) and customers.manage (Manager). Till phone lookup uses the same records.

Permission catalog version (Permissions.CATALOG_VERSION / ADDED_IN, roles.catalog_version): new permissions reach
existing roles once (only those in the role's defaults); admin choices are kept.

People & access: Seller and Delivery driver can be chosen. With an application: it is approved. Without: the admin fills
in shop or vehicle/licence + CID + bank details (POST /api/admin/users/{id}/make-seller | make-driver), approved at once;
the person accepts the agreement on first dashboard visit. "Add person" cannot create sellers/drivers directly
(add as Customer, then choose the role). Leaving the seller/driver role pauses that account.

No manual SQL is needed: Hibernate (ddl-auto=update) creates every new table and column at start-up.

Fix (2 Oct 2026): legal_terms.body was created as TINYTEXT (255 chars) on MySQL because @Lob without a length maps to
TINYTEXT in Hibernate 7. Entity now has length = 1_000_000 (MEDIUMTEXT). config/SchemaRepair runs first at start-up and
widens a too-small existing column (ddl-auto=update never changes column types). Lesson: tests run on H2, so check new
text/LOB columns on MySQL too. Verified on inventorydb: body = mediumtext, 3 agreements loaded, roles have their permissions
(catalog_version 2), 3 customers, 10 of 15 orders linked (the other 5 are counter sales without a phone number).

## 16. Who checks payments (2 Oct 2026)
Payments are checked by people with "Verify payments" (payments.verify): by default ADMIN and MANAGER, never CONTROLLER
(the person who packs is not the person who approves money). They use Verify payments (ask for info / reject) or the
Order list ("Payment received", which shows method, journal number and amount from the payments table).
Order list now: online orders only (counter sales are in Sales history); "Needs action" depends on the viewer
(payments to check for verifiers; orders to confirm/send for fulfilers; package orders are followed in Deliveries);
non-verifiers see "Customer has paid. An Admin or Manager checks the payment". Confirming a payment also marks the
payment record "confirmed". AdminOrderResponseDTO has customerPhone, source, paymentSubmitted, paymentMethod,
journalNumber, paymentAmount, hasPackages.

## 17. Page speed (2 Oct 2026)
Lighthouse on `ng serve` (dev mode) gave Performance 42: dev mode ships unminified code (about 5.4 MB on one page).
Always measure the production build: `npx ng serve --configuration production` (keeps the /api proxy), then Lighthouse
in an Incognito window. Done:
- Every page except Home is lazy (loadComponent in app.routes.ts). First download: 1.00 MB -> 367 kB (97 kB compressed);
  what is left is Angular itself plus the frame (navbar, staff bar, footer, home). `ng build` passes again.
- Logo: website-logo.png was 370 kB (500x500) shown at 38-56 px on every page. Now Images/logo-112.png (24 kB).
  The original stays in public/Images.
- Home banner: only the first photo loads with the page (fetchpriority high); the others are fetched just before they show.
- index.html: description and theme-color meta (SEO), preconnect to the CDNs, FontAwesome kit is `defer`
  (it was a blocking script). Bootstrap CSS stays blocking on purpose (layout needs it). The production build copies
  the Google Fonts rules into the page itself.
- angular.json: component style budget 32 kB warning / 48 kB error (POS is a full screen, 30 kB); CommonJS modules of the
  PDF library (html2canvas, canvg, core-js, raf, rgbcolor) are allowed, they load only when an invoice PDF is made.
- .claude/launch.json has "start-prod" to preview the production build on port 4300.
Next ideas if needed: serve Bootstrap from npm instead of the CDN; resize product photos on upload (server side).

## 18. Phones and tablets (2 Oct 2026)
- theme.css (end): phones (under 576px) use a 15px root size, so text, buttons, icons and spacing shrink together
  (nearly all sizes are rem). Form boxes stay 16px so iPhones do not zoom when typing. Page titles 1.45rem on phones,
  1.6rem on tablets; less padding; 12px side gutter under 360px.
- Product cards (product-list): `container-type: inline-size` + `@container (max-width: 210px)` makes a narrow card
  compact (8px padding, 2-line names, smaller price). With the staff Restock button the button says "Add" (`:has`).
  Category chips are one sideways-scrolling row on phones (shop and POS).
- Home: smaller hero on phones, 2 featured products per row, why-us cards with the icon on the left, bigger dot buttons.
- POS on phones: products are a compact list (56px photo, name, stock, price) instead of 287px-tall tiles.
- Cart bug: the line used class `row`, which is Bootstrap's grid row (-15px margins), so line totals were cut off.
  Renamed to `cart-line`. Avoid Bootstrap class names (row, col, card, media, toast...) for own styles.
- Terms page now puts the browser tab title back when you leave it.

## 19. Delivery priced by size and distance, POS on phones and tablets (2 Oct 2026)
Delivery (backend: DeliveryPricingService, entity/DeliverySize, DeliveryController):
- Every product has a delivery size: SMALL (bag, any rider), MEDIUM (motorbike), LARGE (car), BULKY (pickup truck).
  Empty (old products) = SMALL. A package takes the size of its biggest product.
- fee = base fee + (road km beyond the included km) x per-km rate of that size, rounded UP to the next Nu. 5.
  Rider pay = riderSharePercent of the fee (whole Nu.). Defaults (until the admin saves): base 50/80/200/500,
  per km 10/12/20/35, 2 km included, rider 80%, max 30 km, 5 km assumed when no location.
- Road km = straight line (haversine) x 1.35. No map API or key is needed.
- Points: the seller's pickup point (seller sets it in My shop), our shop's point (Marketplace > Settings), and the
  customer's point (phone GPS at checkout, or a delivery area the admin added). A missing point = the "unknown"
  distance, marked estimated. Further than the max distance = the order is refused with a clear message.
- Price check before ordering: POST /api/delivery/quote (public). The order itself is priced again on the server.
- Riders only see and can take jobs their vehicle carries (bicycle/on foot = small, motorbike/scooter = medium,
  car/taxi = large, pickup/van/truck = bulky). The drop point is hidden until a rider takes the job.
- New columns are added by Hibernate (ddl-auto=update): items.delivery_size, orders.drop_*, order_packages size,
  distance and points, seller_profiles.pickup_*, marketplace_settings rates; new table delivery_areas.
  The old flat delivery_fee / rider_pay_per_delivery columns stay but are no longer used.
- Tests: MarketplaceFlowTest (4.5 km honey = Nu. 65, rider Nu. 52; estimated soap Nu. 70; scooter cannot take bulky)
  and deliveryPriceGrowsWithSizeAndDistance. All 9 backend tests pass.
POS:
- Tablets (768-991px): products left, sale right (340px), till fills the screen like on a desktop.
- Phones (under 768px): a bar fixed at the bottom (search, items + total, Charge); the sale opens full screen over
  the products (back arrow / Esc to close). The cashier never scrolls past the products to reach the sale.
- On touch screens the search box is no longer focused automatically after each tap (that kept popping the
  keyboard up); it still is with a mouse, with F2, and when typing or scanning.
- Removed 82 unused CSS rules of the old sale panel (pos.css 47 kB -> 41 kB raw).
