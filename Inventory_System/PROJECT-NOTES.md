# DP DrukBazaars: project notes

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

## 20. Ready for a live server: database files, secrets, Flyway, emails, notifications, online payments (2 Oct 2026)
Database (D:\Inventory\database, read README.md there):
- 01-create-database-and-user.sql (new server: database utf8mb4 + app account with only the rights it needs),
  02-first-admin.sql (promote the owner's signed-up account), upgrade-existing-database.sql (databases made before
  Flyway: adds 4 tables + 29 columns, only adds, safe to run twice), backup.sh / backup.ps1 (database + uploads, 14 days).
- Flyway now builds the tables: Inventory_System/src/main/resources/db/migration/V1__initial_schema.sql (generated from
  the entities for MySQL 8). ddl-auto=validate: Hibernate only checks. New table changes = new V2__...sql file.
  Tested on 2 Oct: empty database -> V1 applied, app started, roles/agreements/settings created by the app;
  copy of inventorydb's structure + upgrade script -> baselined at V1, started; without the upgrade -> refuses to
  start with "Schema validation: missing table [delivery_areas]". Temporary test databases were dropped.
- The dev inventorydb still needs upgrade-existing-database.sql once (it was not restarted after the delivery work).
Secrets:
- The MySQL root password was in application.properties in the PUBLIC GitHub repo. Moved to
  Inventory_System/secrets.properties (git-ignored, imported by spring.config.import). CHANGE the MySQL password
  (it is still in the git history) and put the new one in secrets.properties. Server: environment variables
  (deploy/drukbazaars.env.example). application-prod.properties: secure cookie, forwarded headers, no test payments, INFO logs.
- CORS origins: app.cors.allowed-origins / APP_CORS_ALLOWED_ORIGINS (not needed behind Nginx on one address).
Deploy: D:\Inventory\DEPLOY.md + deploy/nginx-drukbazaars.conf (HTTPS, /api and /uploads to 8080) + deploy/drukbazaars.service.
Frontend: environment imageBase '' everywhere; proxy.conf.json now also forwards /uploads.
Fixes and features (backend tests: 13, all pass, incl. GoLiveFeaturesTest):
- Product photos: ProductPhotos gives each photo its own name (item-12-ab12cd34.jpg; before, products with the same
  name shared one file), checks the real type from the first bytes (JPG/PNG/WEBP/GIF), 5 MB, deletes the product's old one.
- Forgot password: /forgot-password and /reset-password pages; link by email, SHA-256 of the token stored, 30 min,
  works once, 3 per account and 10 per IP per hour, same answer for unknown emails. Any password change (reset,
  change-password, admin reset) signs the account out everywhere else (users.password_changed_at + session stamp).
- Email: real SMTP when app.mail.enabled=true + spring.mail.*; otherwise written to the log (dev). Sent in the
  background after the transaction commits. SMS: SmsService with a provider URL template, off by default.
- Notifications: table notifications, bell in the top bar (unread count every minute), NotificationService hooks:
  order placed / paid / rider assigned / on the way (SMS with the delivery code) / delivered / cancelled, payment
  refused or question, payment to check (staff), new order to pack (seller or staff), new job (riders whose vehicle
  fits), earnings, payouts, applications and decisions. Old ones deleted after 90 days.
- Online payments: PaymentGateway interface + SandboxGateway (test page /pay/test, on in dev, off in prod) +
  OnlinePaymentService (attempts in payment_intents, server-side amount, idempotent, a paid attempt confirms the order
  exactly like a verified transfer; wrong amount / cancelled order / sold out -> staff are told). Payment page offers
  "Pay online now" or "Bank transfer"; /payment/result asks the server how it went; order details show "Pay now" for
  unpaid orders. A real gateway (RMA or a bank) needs a merchant account + its documents + one class (see PaymentGateway).

## 21. Old and new stock: batches, expiry, real cost and profit (2 Oct 2026)
Before: one stock counter per product; a restock only added to it; the cost price never changed; no expiry.
Now (backend StockService, the ONLY place stock moves):
- Every delivery is a batch (stock_batches): its own cost, batch/lot number, expiry date, supplier.
- Sales take the batch that expires first (FEFO; undated last, oldest first). Each sold line records which batches
  (order_item_batches) and its real cost (order_items.unit_cost). Returns and cancelled orders go back into the
  same batches. Recall question "who bought batch X?" can be answered from order_item_batches.
- Expired stock is never sold: checked before every sale, and every night 00:05 (Asia/Thimphu) expired batches are
  taken off sale (transaction EXPIRED) and staff with stock.restock are told; Mondays 08:00 they hear what expires
  within 30 days. Receiving an already expired batch is refused.
- Restock page: batch number + expiry (optional), the margin at the new cost vs the last cost, a warning when a sale
  would lose money, and "change the selling price" (suggested price keeps the old margin). The product's cost price
  follows the latest purchase; older stock keeps its own cost. Fixed: a new product's selling price from the restock
  page was ignored (sellingPrice vs pricePerUnit).
- Stock page /admin/stock (Products > Stock & expiry): on the shelf / expiring soon / expired, stock value at cost,
  edit batch number or date, write off (with a reason, transaction WRITE_OFF), count a product (more = new batch,
  fewer = taken first-to-expire first).
- Sales dashboard: profit on our own products = sold (before tax) - exact batch cost - returns - expired/written off.
  Marketplace sellers' products are not counted (not ours).
- Existing stock: at start-up every product's stock without batches becomes one OPENING batch at its cost price
  (StockService.reconcile); a sale that finds stock without a batch books it the same way instead of failing.
- Database: V2__stock_batches.sql (Flyway applies it). Tested: empty database (V1+V2) and a full copy of inventorydb
  (upgrade script -> baseline 1 -> V2 -> 7 opening batches = 175 units, 0 mismatches). Copies dropped.
- Tests: StockBatchTest (FEFO and cost, expiry, counts/write-offs/opening, restock at a new price, exact profit).
  Backend: 18 tests, all pass.
Fix (2 Oct 2026, evening): IntelliJ runs the backend with the working folder D:\Inventory (not Inventory_System),
so uploads/ and private-uploads/ live in D:\Inventory and "./secrets.properties" was not found ("Access denied ...
using password: NO"). application.properties now imports ./secrets.properties AND ./Inventory_System/secrets.properties.
Tested by starting the jar from D:\Inventory. database/backup.ps1 now saves the upload folders of both places.

## 22. Fewer sign-outs, POS fixes (3 Oct 2026)
- Sign-ins were kept in the server's memory: every backend restart signed everyone out, and 30 minutes in a
  background tab did too. Now Spring Session JDBC keeps them in MySQL (V3__sessions_in_database.sql: SPRING_SESSION,
  SPRING_SESSION_ATTRIBUTES; expired ones removed every minute) and the idle limit is 4 hours
  (server.servlet.session.timeout=${SESSION_TIMEOUT:4h}). Tested: a fresh MySQL database got V1-V3 and started.
  The browser cookie is now called SESSION (was JSESSIONID): everyone signs in once more after this update.
  4 Oct 2026 fix: an old config/SessionConfig.java (a CookieSerializer bean) took over the cookie once Spring Session
  was on: JSESSIONID with SameSite=None but no Secure, which Chrome refuses, so every sign-in was lost at once
  ("Welcome back" then "Your session has ended"). Removed: the cookie now follows server.servlet.session.cookie.*
  (SESSION, HttpOnly, SameSite=Lax, Secure on the live site). Do not add a CookieSerializer bean again; change the
  properties instead (SecurityHttpTest.theSignInCookieIsOneABrowserKeeps checks it).
  The sign-in page never "returns" to itself (returnUrl=/login) and its button no longer stays on "Signing in...".
- Sales dashboard and My orders: no more browser pop-ups (alert/confirm); normal messages, and nothing extra
  when the sign-in ended (the app already goes to the sign-in page).
- POS: marketplace sellers' products are no longer offered at the counter (the server refused them anyway:
  they are in the seller's shop, sold online only). A saved sale holding one has it taken out, with a message.
- POS on phones: "Current sale" button at the top as well; Charge stays at the bottom of the open sale.
Payments with bank + account number + OTP: built on 4 Oct 2026, see section 23.

## 23. Paying from a bank account with a code on the phone (4 Oct 2026)
The flow: Payment page -> "Pay from your bank account" -> /pay/bank?ref=PI-... ->
  1. choose the bank, type the account number -> "Send the code to my phone": the customer's bank texts a one-time
     code to the phone registered with that account (valid 5 minutes)
  2. type the code -> "Pay Nu. X": the money moves to our account, the order is confirmed at once (stock taken,
     sellers told to pack, customer told), and the customer lands on /payment/result.
Limits: 3 wrong codes, or a 4th code, end the attempt (nothing taken; the customer starts again from the order);
"send again" after 30 seconds; at most 10 code requests per customer per hour (sent or refused), so nobody can
flood a stranger's phone or try account numbers one by one.

Backend (Inventory_System, package com.api.inventory):
- entity/BankPayment (table bank_payments): one row per payment from a bank account. Keeps the bank, the LAST 4
  digits of the account, the gateway's transaction number, codes sent, wrong codes, how it ended.
  STARTED -> CODE_SENT -> PAID | FAILED | CANCELLED | EXPIRED.
- entity/PaymentEvent (table payment_events): every step with time and who (STARTED, CODE_REQUESTED, CODE_SENT,
  CODE_REFUSED, WRONG_CODE, CODE_EXPIRED, PAID, REFUSED, TOO_MANY_CODES). For "what happened to my payment?".
- The full account number and the code are never stored or logged (the request classes print them as ****).
- service/payments/BankGatewayClient: the 3 steps the gateway offers (start, requestCode, debit).
  TestBankGatewayClient = TEST MODE: no bank contacted, no money, no text; code 123456; account ending 0000 =
  "not found", ending 9999 = "not enough money". Refuses to start with the prod profile.
- service/payments/BankAccountGateway: the "BANK" choice on the payment page (shown while a client is on).
- service/BankPaymentService + controller/BankPaymentController: /api/online-payments/bank (banks, {ref},
  {ref}/code, {ref}/pay). Paid money goes through OnlinePaymentService.complete like every online payment.
  Cancelling/replacing/expiring the attempt closes the bank row too.
- Settings: app.payments.bank.mode = test (this computer) | rma (real gateway, once connected) | off (live default,
  APP_PAYMENTS_BANK_MODE). app.payments.bank.banks = the bank list. The older one-click test page
  (app.payments.sandbox.enabled) is now off here too.
- db/migration/V4__bank_payments.sql (Flyway applies it on the next start; tested on a fresh MySQL database:
  V1-V4 applied and the table check passed).
- Tests: BankPaymentTest (9): right code pays and confirms; only last 4 digits stored; 3 wrong codes; unknown
  account and refusal; 4th code; account guessing limit; cancel; other people refused; the web calls. 27 in all.

Frontend: Components/pay-bank (route pay/bank, signed in): bank cards, account number, code box with countdown,
send again, use another account, cancel. services/online-payments.ts: banks, bankView, bankRequestCode, bankPay.
Payment page shows the bank choice with its own explanation. Disabled green buttons no longer turn Bootstrap blue.

To go live with real money: register DP DrukBazaars as a merchant with the RMA Payment Gateway, get their kit (test
address, merchant id, keys, message format), write RmaBankGatewayClient implementing BankGatewayClient
(@ConditionalOnProperty app.payments.bank.mode=rma), try it on their test address, then set
APP_PAYMENTS_BANK_MODE=rma on the server. Nothing else changes.

## 24. Bank transfer, journal numbers in the POS, receipts, the real RMA connection (4 Oct 2026)
Checkout: "Bank transfer" holds both ways. "Pay from my bank account" (bank + account number + code on the phone,
confirmed at once, page /pay/bank) is chosen first when it is switched on; "Scan our QR in my banking app" is the
QR + account details + journal number as before (the only way when bank-account payments are off).

Journal numbers (service/JournalNumbers): tidied (trimmed, upper case), 4-40 letters/digits, and never accepted
twice anywhere: checkout transfers (payments.journal_number) and counter sales (orders.payment_reference).
- POS: Bank transfer needs the journal number from the customer's banking app (field in the payment step,
  Enter completes); Card approval code / UPI number optional. Printed on the A4 invoice and the narrow receipt,
  shown and searchable in Sales history, and listed per shift ("Paid without cash: match with the bank statement")
  in Close drawer, the end-of-shift report and Cash drawers.
- Payments from a bank account record the BANK's journal number (bank_payments.bank_reference); the payment is
  recorded as "ONLINE BANK <journal>".

Receipt (Components/receipt, route receipt/:orderId; GET /api/orders/{id}/receipt, ReceiptService): for paid
orders, online and counter, the customer's own or any for staff (orders.view / pos.use). Shows the payment method,
journal number, bank account (last 4), items, tax, delivery, total. Responsive by container width (phone: one
column, compact lines). Download PDF (utils/pdf.ts) renders a copy at desktop width, so the PDF is the same from
a phone; Print uses utils/print-area. Links: payment result (paid), order page (paid), Sales history (each sale).

Real money (RmaBankGatewayClient, app.payments.bank.mode=rma): the RMA Payment Gateway's merchant API (AR register,
AE account enquiry -> the bank texts the OTP, DR debit -> bfs_debitAuthNo = journal), every message signed
SHA1withRSA with DP DrukBazaars' key, RMA's answers checked with RMA's key. Settings and steps: DEPLOY.md. Bank ids are
RMA's (1010 BoB ... 1060 DK). Needs the merchant registration and kit before it can run; check field names against
the kit and test on RMA's UAT first.
When the bank's answer to the debit never arrives (timeout, broken connection, an answer failing its signature
check) the payment is CHECK_BANK: never "failed", the customer is told not to pay again, new attempts and
cancelling are refused, it does not expire, staff are notified, and Order verification -> "Bank payments to check"
settles it (Money arrived + journal number -> order confirmed; Nothing was taken -> customer can pay again).
Test mode: account ending 5555 acts this out.

Backend: V5__journal_numbers.sql. Tests: JournalAndReceiptTest (3), RmaBankGatewayClientTest (5, a pretend RMA
that checks signatures), BankPaymentTest (+1: bank never answers, staff settle). 37 in all, passing.

## 25. Checkout: bank account through the RMA Payment Gateway only (4 Oct 2026)
The QR code, the shop's account details and the journal-number field are gone from checkout (they were for
testing). The one way to pay online orders: "Pay with your bank account", secure online payment through the RMA
Payment Gateway (Royal Monetary Authority of Bhutan), with the banks shown (BoB, BNB, DPNB, TBank, BDBL, DK); button
"Place order and pay" -> /pay/bank. When bank-account payment is off (app.payments.bank.mode=off, the live default
until RMA is connected) checkout says "Online payment is not available right now" and the button is disabled.
Wording updated: home and services pages, payment result, order success, receipt ("Bank account (RMA Payment
Gateway)"), the payment option label. Staff "Verify payments" still handles transfers sent before this change;
the counter (POS) still records journal numbers for bank transfers at the till.

## 26. Order management: the order board (5 Oct 2026)
/admin/orders is now the ORDER BOARD (Components/order-board); the old Order list and Deliveries pages are gone
(/admin/deliveries redirects here). Staff menu: Orders -> "Orders" and "Verify payments".

The steps, in order (each online order is in exactly one; from packing on, each PACKAGE - one per seller):
  Awaiting payment -> Verify payment -> To pack -> Packed, needs a driver -> Driver coming to collect -> On the way
  -> Delivered (Cancelled on the side).
- Pipeline at the top: how many in each step (click = only that step), "N not started", "N need a driver".
- Tabs: Needs action (verify, pack, packed without a driver, anything late), Mine (what I am packing), Late,
  All in progress, Drivers, Done. Search: order #, customer, phone, journal, driver, packer, item, address.
- Period: Today / This week (from Monday) / This month (default) / This year / Custom range, by the day the order was
  PLACED. Orders placed earlier that are still not delivered are never hidden silently: a banner counts them and
  "Show them" adds them, marked "older". The period stays in the address (?period=..&from=..&to=..).
- Late: longer in a step than the target (app.orders.target.*: check payment 2 h, pack 4 h after payment, collect
  2 h after packing, deliver 3 h after collecting). Late work comes first; a red banner counts it.
- Who has it (per card and in the side panel with the full history): who confirmed the payment (a person, or "the
  bank (RMA Payment Gateway)"), who is packing / packed it and since when, which driver has it (and whether a manager
  gave the job or the driver took it from the job board), or which staff member delivered it.
- Route: every packed package shows FROM (shop or seller, pickup address) -> TO (customer, drop address), distance.
  Drivers tab: every package a driver (or our staff) took in the period, filterable by driver (click a driver in
  the team panel): driver, vehicle, phone, from -> to with times, size, distance, driver pay.
- Team panel: packers (packing now, packed in the period), drivers (jobs now x/5, delivered in the period, free /
  busy / full), the targets.
- Buttons by step: Call / Cancel unpaid order (after a day) · Check payment / Money arrived · Take it / Packed /
  Give back / Give to... (packer) · Mark packed for seller · Give to a driver... (only drivers whose vehicle fits the
  size) / We deliver it · Collected / Change driver / Take off driver · Delivered · Receipt. Packing slip (print).
  Refreshes itself every minute.

Backend: OrderBoardService + OrderBoardController (GET /api/admin/order-board?from&to&older, take, release, packer,
rider, rider/remove). PackageService: takePacking, releasePacking, assignPacker, assignRider, removeRider (with
notifications to the people concerned); packed records the packer, "we deliver it" records the courier.
New permission orders.assign "Plan and assign orders" (MANAGER by default, ADMIN always; CATALOG_VERSION 3 gives it
to existing MANAGER roles once). Taking work yourself needs orders.fulfil; giving it to others needs orders.assign.
V6__order_handling.sql: order_packages.packer_email, packing_started_at, rider_assigned_by, courier_email;
orders.payment_verified_by, payment_verified_at. Tests: OrderBoardTest (3). 40 in all.

## 27. Free hosting: Render + Aiven (5 Oct 2026)
Guide: D:\Inventory\DEPLOY-RENDER.md (Aiven free MySQL, Render Docker web service for the API, Render static site
for the website with rewrites /api/* and /uploads/* to the API and /* to /index.html, so everything is one address
and the session cookie stays first-party).
- Uploaded files go through FileStore (service/files): DiskFileStore (default, folders) or DatabaseFileStore
  (app.files.store=database, table stored_files, V7). Render's free disk is wiped on restart, so hosting sets
  APP_FILES_STORE=database. On start, files still in the folders are copied in once. ProductPhotos is now a bean;
  PartnerDocumentService and /uploads/{name} (UploadsController, database mode) use the store.
- Inventory_System/Dockerfile (maven:3.9-eclipse-temurin-26 build, eclipse-temurin:26-jre run, prod profile, memory
  capped for 512 MB, Bhutan time zone, non-root). InventoryWeb/.node-version = 24 (Angular 22 needs 22.22+/24.15+).
- app.payments.bank.test-on-live-site=true allows TEST MODE bank payments under the prod profile, for a demo copy only.
- Tests: FilesInDatabaseTest (1). 41 in all.

## 28. Ratings and reviews; help when a password is forgotten (7 Oct 2026)
Forgotten password (the self-service part is now section 29):
- GET /api/auth/forgot-password (public) answers which ways can send a reset code (see 29).
- With e-mail off (Render free blocks SMTP ports 25/465/587), the page shows "Ask us to reset it" instead of the form:
  call the shop, or "Send a message" (/contact?subject=Forgot my password, text filled in). Staff reset the password
  in People & access (shows a temporary password); the customer changes it in My profile.
- To turn e-mail on when hosted: Brevo SMTP relay on port 2525 (smtp-relay.brevo.com), env APP_MAIL_ENABLED=true,
  SPRING_MAIL_HOST, SPRING_MAIL_PORT=2525, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD (the SMTP key), APP_MAIL_FROM.

Ratings (only for what was delivered to the customer: the order COMPLETED, or that seller's package DELIVERED):
- Each product of an order: 1-5 stars + optional comment (1000), one per customer per product (rating it again
  from a later order updates it). The order's service and delivery: 1-5 stars each + comment, one per order; the
  driver of the order is kept with it.
- Customer: "Rate your order" panel at the bottom of /orders/:id (Components/order-rating), Change after saving.
  The "delivered" notification invites them to rate.
- Public: stars on product cards (GET /api/reviews/summary), rating line + "Customer reviews" section with the
  5..1 bars on the product page, /reviews page (service, delivery and product averages, what customers say, best
  rated products). Names shown as "First L."; hidden reviews are not shown or counted.
- Staff /admin/reviews (Staff menu > Customers > Reviews, permission reviews.manage "Manage reviews"; MANAGER by
  default, ADMIN always; CATALOG_VERSION 4 gives it to existing MANAGER roles once): tabs Products / Service &
  delivery / Drivers (average per driver, lowest first); filters All / Low (1-2 stars) / Not answered / Hidden;
  Answer (shown publicly, the customer gets a notification) and Hide / Show again. A rating of 1-2 stars notifies
  everyone with reviews.manage.
- Components/stars (app-stars): shows an average (halves) or, with "pick", lets the person choose; the word next to
  the stars has a fixed width so the stars do not move under the mouse.

Backend: ProductReview, OrderFeedback (+ repositories), ReviewService, ReviewController /api/reviews (public GET
products/{itemId}, summary, service; signed in: GET orders/{orderId}, POST orders/{orderId}/products/{itemId},
POST orders/{orderId}/service; reviews.manage: GET admin, POST admin/{products|service}/{id}/hidden and /reply).
V8__reviews.sql: product_reviews (unique user_email + item_id), order_feedback (unique order_id).
Tests: ReviewTest (2). 43 in all.

## 29. Forgot password: the customer resets it with a code by email or text message (7 Oct 2026)
/forgot-password (Components/forgot-password, 3 steps):
  1. Choose Email or Text message, type the email or phone (+975, 8 digits; spaces and +975 are fine). "Email me a
     code" / "Text me a code". The answer is the same whether or not the account exists.
  2. Type the 6-digit code (checked as soon as 6 digits are in; works with the phone's code autofill). "Send a new
     code" after 60 s; "Use another email, or my phone". The email also holds a link (/reset-password, 30 minutes).
  3. New password twice (at least 6), then "Password changed" -> Sign in with the email filled in (passed in the
     browser's history state, not the address). Every other device is signed out; an email tells the owner.
  Always shown: "No access to that email or phone any more? Call / send us a message".
- GET /api/auth/forgot-password -> {email, sms}: a way is offered when it really sends (APP_MAIL_ENABLED; SMS on and
  its provider filled in) or, on a developer's computer, when unsent messages go to the log (app.mail.log-body,
  app.sms.log-text: true by default, false in the prod profile). A way not offered shows "Not available yet";
  with neither, the old "Ask us to reset it" panel.
- POST /api/auth/forgot-password {method: email|sms, email | phone} -> {message, resendAfter: 60}
  POST /api/auth/forgot-password/verify {method, email | phone, code} -> {token} (one-time ticket, 15 minutes)
  POST /api/auth/reset-password {token, newPassword} -> {message, email} (ticket, or the token from the email link)
- Safety: only a BCrypt hash of the code is stored; 10 minutes, 5 wrong tries, a new code cancels the old ones;
  1 code a minute and 3 an hour per account and way; 10 requests and 30 tries an hour per internet address; same
  answers (and about the same time) for unknown accounts; after the reset every other link, ticket and code stops.
  Codes are never written to the server's log.
- SmsService: app.sms.provider=url (any bulk SMS web address with {to} {text}, e.g. B-Mobile / TashiCell business
  SMS) or twilio (app.sms.twilio.account-sid / auth-token / from; numbers sent as +975XXXXXXXX). A Twilio trial
  cannot send our own text, so it needs an upgraded account. See DEPLOY-RENDER.md.
- PasswordResetService (sendCode, verifyCode, reset), PasswordResetCode + repository, V9__password_reset_codes.sql.
  Audit: PASSWORD_RESET_CODE_OK, PASSWORD_RESET_SELF. Tests: PasswordResetCodeTest (3). 46 in all.

## 30. Renamed to DP DrukBazaars, an online shopping platform (7 Oct 2026)
The shop is now "DP DrukBazaars" (was DK/Phar Inventory Management System), and the "natural products" wording is
gone: it is an online shopping platform / marketplace for Bhutan.
- Website: header and sign-in pages "DP DrukBazaars / Online shopping", footer, page title and description, home,
  About (mission, vision), receipts and invoices (SHOP.name in utils/shop-info.ts and pos.ts), "Sold by",
  "replied:", partner pages, payment pages, cash drawer report.
- Server: emails, text messages, notifications, delivery pickup names, the RMA payment description
  ("DP DrukBazaars order N"), the default sender (app.mail.from), the legal texts in resources/legal.
- Technical names are renamed too (section 33). Only the applied migrations V1-V9 keep "DK/Phar" in their comments:
  Flyway checks them on every start, so changing them would stop the server.
- The live database keeps version 1 of the Terms, Seller and Driver agreements with the old name: publish a new
  version in Marketplace > Terms (people are asked to accept it once).

## 31. Our own pictures, and real wording (7 Oct 2026)
- The test photos are replaced by pictures drawn for DP DrukBazaars (SVG, public/Images/art, made by
  scripts/draw-art.js: change it and run `node scripts/draw-art.js` to redraw):
  banner-market, banner-delivery, banner-pay, banner-track (the home banner, each with its own description for
  screen readers), together (home, next to the "one drop / ocean" quote), about (About page, top).
  The old test photos (cors-*.jpg, alternative-medicine-capsules.jpg, ...) are no longer used by any page.
- Services page: real points only (shop online, products from us and checked local sellers, follow every order;
  riders and the delivery code; paying from your bank account; reviews from customers who received their order).
- Still "test" on purpose: the live site's bank payments run in TEST MODE (APP_PAYMENTS_BANK_MODE=test with
  APP_PAYMENTS_BANK_TEST_ON_LIVE_SITE=true), so the payment pages say "TEST MODE: no real money". That wording
  goes away by itself when the RMA Payment Gateway is connected (APP_PAYMENTS_BANK_MODE=rma, see DEPLOY.md).

## 32. The About page, managed by staff (7 Oct 2026)
/about (Components/about) is now built from the server (GET /api/site/about, open to everyone):
  "Online shopping, made in Bhutan" + the introduction, Start shopping / Get in touch; the live numbers (products
  customers can see, approved sellers, delivered orders, the average service rating; a 0 is never shown); mission and
  vision; "How DP DrukBazaars works" (Shop / Sell / Deliver, linking to /products, /sell, /deliver); Meet the team
  (photo or initials, name, role, a short introduction); "Need help?" (call, send a message, find my order).
  If the server cannot be reached, the built-in wording shows and the team is left out.
Staff: Website > About page (/admin/about, Components/about-admin, permission site.manage "Edit the website pages":
  ADMIN always; give it to other roles in People & access):
  - Texts: introduction (600), mission (800), vision (800), each with "Use the original wording"; show or hide the
    live numbers; "Last changed ... by ...". A text never changed is not stored: SiteService.DEFAULTS is used.
  - Team: add (name, role, about them up to 400, photo JPG/PNG/WEBP/GIF up to 5 MB, shown or hidden), edit, change
    or remove the photo, hide / show, move up / down, remove (asks first; the uploaded photo is deleted).
    Photos go to the file store as /uploads/team-{id}-{random}.{ext} (in the database on Render).
- Backend: SiteService, SiteController (/api/site/about; /api/site/admin/about GET/PUT, /admin/team POST,
  /admin/team/{id} PUT/DELETE, /admin/team/order PUT). V10__about_page.sql: site_texts, team_members (starts with the
  two people who were on the page). Audit: SITE_ABOUT_CHANGED, TEAM_MEMBER_ADDED / CHANGED / REMOVED.
  Tests: SiteAboutTest (1). 47 in all.

## 33. Every name is DP DrukBazaars (7 Oct 2026)
- Shop email everywhere: dpdrukbazaars@gmail.com (website footer and Contact page, receipts and invoices, the legal
  texts' contact line). The phone stays 77269712.
- Technical names: dkphar -> drukbazaars. deploy/drukbazaars.env.example, deploy/drukbazaars.service,
  deploy/nginx-drukbazaars.conf (server folders /opt/drukbazaars, /etc/drukbazaars, /var/www/drukbazaars, Linux user
  drukbazaars, the jar drukbazaars-api.jar, example domain drukbazaars.bt), database users drukbazaars_app and
  drukbazaars_backup (database/ scripts, for a NEW server), the container user, the example Render names
  (drukbazaars-db / -api / -web), spring.application.name=drukbazaars, the pom description.
- Kept on purpose: the applied migrations V1-V9 (Flyway checksums), V10's starting team data (already applied; names
  are changed in Website > About page), the Java package com.api.inventory, the Angular project name inventory-project
  (Render's static site publishes dist/inventory-project/browser), the GitHub repositories and the Render addresses
  (inventoryapi-qqjz / inventoryweb-a461: an onrender.com address cannot be renamed; a domain of your own can be added).

## 34. Documentation (7 Oct 2026)
The documentation is in the server repository, D:\Inventory\docs (README.md is the index):
01-system-overview (simple language, everyone), 02-user-guide (customers, sellers, drivers: every step),
03-staff-guide (counter, order board, payments, products and stock, customers, reports), 04-admin-guide (setup,
roles and permissions table, marketplace, website, payments, email/SMS, hosting, backups, settings, security
checklist, problems and fixes), 05-technical-reference (architecture, running locally, settings, code, security,
database, API by area with permissions, business rules, scheduled jobs, tests, build), 06-glossary-and-faq.
D:\Inventory\README.md is the server repository's front page; the website's README.md now describes the website
and points to the docs. Keep the guides in step with the screens: change the document in the same commit.
