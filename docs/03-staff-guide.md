# 3. Staff guide

*For cashiers, packers and managers. Admin tasks (people, marketplace, website, settings) are in the
[Admin guide](04-admin-guide.md).*

## 3.1 The staff bar

When you sign in with a staff account, a green **Staff** bar appears under the top menu. It only shows what your role
may do. The groups are:

| Group | Pages | Permission needed |
|---|---|---|
| **Counter** | Point of sale, Sales history, Cash drawers | Use the point of sale |
| **Orders** | Orders (the order board), Verify payments | See orders / Verify payments |
| **Products** | Add product, Restock, Stock & expiry | Add and edit products / Restock |
| **Customers** | Customers, Customer messages, Reviews | See customers / Read customer messages / Manage reviews |
| **Reports** | Sales dashboard | See the sales dashboard |
| **Marketplace**, **People**, **Website** | Admin pages | See the Admin guide |

If a page you need is missing, ask the admin to give your role that permission.

The **bell** (top right) shows your notifications: payments to check, orders to pack, expiring stock, low ratings
and so on. Tap one to go to the right page.

## 3.2 The counter (point of sale)

### Open your cash drawer

1. Open **Counter > Point of sale**.
2. If your drawer is closed, type the starting cash (the float) and open it. Every sale you make is tied to your
   drawer and your name.

### Make a sale

1. Add products: type in the search box (name, code or category) or **scan the barcode**. **F2** jumps to the search.
   Tap a product to add it; change the quantity in the sale.
2. Discounts: the shop price is always allowed. A bigger discount needs the "Give extra discounts" permission and is
   never more than the product's maximum discount. You can also give a discount on the whole sale (percent or
   amount).
3. Customer (optional): type their phone number (8 digits). If they bought before, their name and history appear.
4. **Hold** puts a sale aside (up to 10) so you can serve someone else; recall it later. An unfinished sale survives
   a refresh or a crash.
5. Tap **Charge** (or **F9**) and choose how they pay:
   - **Cash**: type what they gave you; the change is shown. It must be at least the total.
   - **Bank transfer**: type the **journal number** from the customer's banking app (required, never accepted twice).
   - **Card** (approval code) or **UPI** (reference number): optional reference.
6. Complete the sale. Print the receipt (narrow printer), the **A4 invoice**, or save a **PDF**.

Products of marketplace sellers are not sold at the counter; they are sold online only.

### Close your cash drawer

1. At the end of your shift, close your drawer and count the cash in it.
2. The system shows what should be there: float + cash sales - cash refunds. If your count is different, write a note.
3. Print the end-of-shift report. It also lists the payments without cash (bank transfers, cards) to check against the
   bank statement.

Managers with "Manage all cash drawers" see every cashier's drawers in **Counter > Cash drawers**.

### Sales history and receipts

**Counter > Sales history** lists counter sales. Choose a period (today, this week, this month, or a custom range),
search by number, customer or journal number, open the receipt, or start a **Return**. Refunded sales show
"Refunded Nu. X" or "Fully returned". The total at the top shows net sales (sales minus refunds).

### Returns and refunds

Needs the "Take returns and refund" permission.

1. Find the sale: a counter sale in **Sales history**, an online order on the **order board** (a delivered card, or
   the side panel). Tap **Return** (within **7 days** of the sale; an online order must be fully delivered).
2. Choose the items and quantities coming back. You can return line by line, over several visits.
3. Mark damaged items as damaged: they do not go back into stock.
4. The refund is what the customer actually paid for those items (with their tax), never the list price. The delivery
   fee of an online order is not refunded.
5. Print the credit note (A4 or receipt).
6. For a seller's product, the seller's share of the returned items is taken off their earnings automatically, and the
   seller is told.

**Return requests** (Orders > Return requests, same permission): customers ask from their order page. Each request
shows the order, the products, the reason and the customer's words.
- **Approve**: tell the customer how the items come back (ready answers: our rider collects it, or bring it to the
  shop). **Decline**: the customer sees your reason.
- When the items are back, **Record the return** (the usual return window) does the refund. The request then shows
  **Returned and refunded** and the customer is told the amount.
- An order that used a coupon refunds only what was paid: each item's price less its part of the coupon.

## 3.3 Online orders: the order board

**Orders > Orders** shows every online order from payment to the customer's door.

```mermaid
flowchart LR
    A["Awaiting payment"] --> B["Verify payment"] --> C["To pack"] --> D["Packed,<br/>needs a driver"]
    D --> E["Driver coming<br/>to collect"] --> F["On the way"] --> G["Delivered"]
```

From packing on, the board works per **package**: one package per seller (our own products are one package).

**Pick up myself**: a customer may collect the order instead. Such a package has a blue **Pick up** badge. After
**Packed** it goes to **Waiting for the customer to collect** (counted in the Packed step as "to collect"), never to
drivers. The customer brings a 4-digit **collection code**. It is late after 3 days: call the customer.

**At the top**
- The **pipeline**: how many orders are in each step. Tap a step to see only those.
- The **period**: Today, This week, This month (default), This year, or a custom range, by the day the order was
  placed. Orders placed earlier that are still open are never hidden: a banner counts them and **Show them** adds them.
- **Late** work comes first and a red banner counts it. Targets: check payment within 2 hours, pack within 4 hours
  of payment, collect within 2 hours of packing, deliver within 3 hours of collecting.

**Tabs**: **Needs action** (verify, pack, packed without a driver, anything late), **Mine** (what you are packing),
**Late**, **All in progress**, **Drivers** (every package a driver took in the period, with the route and pay),
**Done**. Search by order number, customer, phone, journal number, driver, packer, item or address.

**Each card** shows who has the order: who confirmed the payment, who is packing it and since when, which driver has
it (and whether a manager gave the job or the driver took it), and the route FROM the shop or seller TO the customer
with the distance. Tap a card for the side panel with the full history.

**Buttons, by step**

| Step | What you can do |
|---|---|
| Awaiting payment | Call the customer; **cancel** an unpaid order after a day |
| Verify payment | Check the payment (managers) |
| To pack | **Take it** (you pack it), **Packed**, **Give back**, **Give to...** another packer (managers), print the packing slip, mark a seller's package packed |
| Packed, needs a driver | **Give to a driver...** (only drivers whose vehicle fits the size), or **We deliver it** (our staff deliver, no driver pay) |
| Waiting for the customer to collect | **Call customer**, **Call seller** (a seller's package), **Handed over**: type the customer's collection code, or, if they do not have it, check their name and phone and tap "No code: I checked who the customer is" |
| Driver coming | **Collected**, **Change driver**, **Take off driver** |
| On the way | **Delivered** (staff do not need the customer's code) |
| Delivered (or Collected) | **Receipt**, **Return** (with "Take returns and refund") |

Taking and packing work needs "Pack, send and cancel orders"; giving work to others needs "Plan and assign orders".
The board refreshes itself every minute.

## 3.4 Checking payments

**Orders > Verify payments** needs the "Verify payments" permission (Admin and Manager by default; never the person
who packs).

- **Bank payments to check**: the bank never answered a payment (time-out or broken connection). Check with the
  bank, then choose **Money arrived** (type the bank's journal number; the order is confirmed) or **Nothing was taken**
  (the customer can pay again). Until you decide, the customer cannot pay twice.
- **Older bank transfers** (sent before payments went through the RMA Payment Gateway): compare the amount and the
  journal number with the bank statement, then confirm, **ask for more information**, or **reject** with a reason.
  The customer is told.

## 3.5 Products and stock

### Add or edit a product

**Products > Add product** (or **Edit** on a product page) needs "Add and edit products":
product name, description, category, cost price, selling price (or a markup %), MRP, opening stock,
**Warn me when stock reaches** (the low-stock level; 0 = warn when sold out; empty = no warning), **Delivery size**
(small, medium, large, bulky), barcode, supplier item code, and a photo (JPG, PNG, WEBP or GIF, up to 5 MB).

- **Size or colour**: add each size or colour as its own product (full name: "Gho, size M"; its own price, stock and
  photo), then **This is an option of** the main product, with a short **Option name** ("Size M"). The shop shows one
  card and the choices. After saving, **Add another product** keeps the same main product, for the next size.
- **More photos** (when editing a saved product): up to 4 more; the star makes one the main photo.

### Offers: deals, featured products, coupons

Needs **Run offers: deals and coupons** (Admin and Manager at the start).
- On a product's page, **On the home page**: **Featured**, or **Deal** until a date and time (empty = until you change
  it), or **Not shown**. Deals show under **Today's deals** with when they end; an ended deal disappears by itself.
- **Products > Offers**: **coupon codes**. A code (DRUK10) takes a percent (with an optional most, for example at most
  Nu. 50) or an amount off the items, from a smallest order, between optional start and end times, with uses in all and
  per customer. A cancelled order gives its use back. DP DrukBazaars pays the discount: sellers keep their full share.
  **Switch off** stops a code; a code never used can be deleted. The same page lists what is on the home page.

### Restock (stock that arrived)

**Products > Restock** needs "Restock":

1. Find the product (or add a new one).
2. Type the quantity, the **cost of one**, and if known the **batch / lot number**, **expiry date** and supplier.
3. The page shows the margin at the new cost and warns you if a sale would lose money. You can set a **new selling
   price** (a suggested price keeps the old margin).
4. Save. The stock arrives as a new batch. An already expired batch is refused.

### Stock & expiry

**Products > Stock & expiry** has four tabs: **On the shelf**, **Expiring soon**, **Expired** and **Low stock**
(products at or below their warning level, with a **Restock** button). It shows the stock value at cost. You can:

- correct a batch's number or expiry date,
- **write off** stock (broken, lost, expired) with a reason,
- **count** a product: more than expected adds a batch, fewer takes from the batch that expires first.

The system sells the batch that expires first, takes expired stock off sale every night, tells you every Monday what
expires within 30 days, and tells you when a product's stock goes down to its warning level.

## 3.6 Customers, messages and reviews

- **Customers** (See customers): search by name, phone or email; see how many orders and how much each customer
  spent, and their history. With "Edit customer details" you can correct details and keep staff notes.
- **Customer messages** (Read customer messages): messages from the Contact page. Answer by email from the message.
- **Reviews** (Manage reviews): tabs **Products**, **Service & delivery** and **Drivers** (average rating per driver,
  lowest first). Filters: All, Low (1-2 stars), Not answered, Hidden.
  - **Answer**: your answer is shown in public under the review and the customer is told.
  - **Hide**: only for abusive reviews or ones not about the product or service, never for honest low ratings.
  - A 1 or 2 star rating sends a notification to everyone who manages reviews.

## 3.7 Sales dashboard

**Reports > Sales dashboard** (See the sales dashboard) shows, for a period: total sales, orders, average order,
tax collected, discount given, returns, sold before tax, **what it cost us** (the real batch cost) and **profit on
our products**, and sales by channel (counter and online). Marketplace sellers' sales are not counted as our profit.

## 3.8 Good habits

- On the counter computer or a tablet, install the website as an app (**Get the app** in the footer): it opens in
  its own window, without the browser's bars, and can be pinned to the taskbar. Packers and managers can do the same
  for the order board on their phones.
- If the app shows **A new version is ready**, finish the sale in progress, then tap **Update now**.
- Sign out on shared computers.
- Never give your password to anyone; the admin can always reset it.
- Use **Give back** when you cannot finish a package, so it does not sit with your name on it.
- Check **Needs action** and the **Late** banner several times a day.
