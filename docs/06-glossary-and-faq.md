# 6. Glossary and FAQ

## 6.1 Words used in the system

| Word | Meaning |
|---|---|
| **Admin** | The owner or a person who runs the whole system. Always has every shop permission. |
| **Agreement** | The Terms of Use and Privacy (customers), the Seller Agreement or the Driver Agreement. Each has versions; people accept the current one. |
| **Audit / Activity log** | The list of sensitive actions: who did what and when. In People & access > Activity. |
| **Batch** | One delivery of a product's stock, with its own cost, batch number, expiry date and supplier. |
| **Cash drawer (shift)** | A cashier's till from opening (with the float) to closing (with the count). |
| **Commission** | The part of a seller's sale DP DrukBazaars keeps, as a percentage. |
| **Credit note** | The printed proof of a return and its refund. |
| **Delivery code** | The 4-digit code a customer gives the driver at the door. It proves the package reached the right person. |
| **Delivery size** | Small (any driver), medium (motorbike), large (car), bulky (pickup truck). Decides the delivery fee and which drivers can take the job. |
| **FEFO** | First expired, first out: the batch that expires first is sold first. |
| **Float** | The cash in a drawer when it is opened. |
| **Journal number** | The bank's reference number of a transfer, shown in the customer's banking app. Never accepted twice. |
| **Ledger (earnings)** | The record of what a seller or driver earned and was paid. Their balance is what is still owed. |
| **Marketplace** | Selling other local sellers' products through DP DrukBazaars, with our drivers. |
| **MRP** | The maximum retail price printed on a product. |
| **Notification** | A message in the bell at the top (and sometimes by email or SMS). |
| **OTP / code** | A one-time number sent to a phone or email: by the bank to approve a payment, or by DP DrukBazaars to reset a password. |
| **Order** | What a customer buys in one checkout. It can hold several packages. |
| **Order board** | The staff page that follows every online order from payment to the door. |
| **Package** | The part of an order that one seller (or our shop) sends. Each package is packed and delivered on its own. |
| **Payout** | A payment from DP DrukBazaars to a seller or driver, recorded with the journal number. |
| **Permission** | One thing a person may do (for example "Verify payments"). Roles are groups of permissions. |
| **Pickup point** | Where a driver collects a package: the shop or the seller's address. |
| **POS** | Point of sale: the counter screen for selling in the shop. |
| **RMA Payment Gateway** | The Royal Monetary Authority of Bhutan's service that moves money from customers' bank accounts to the shop. |
| **Role** | Admin, Manager, Controller, Seller, Driver, Customer, or a role the admin made (for example Cashier). |
| **Test mode** | Payments that only pretend: no bank is contacted and no money moves. The pages say so. |
| **Write-off** | Taking stock out because it is broken, lost or expired, with a reason. |

## 6.2 What each status means

**Order** (what the customer sees as Placed, Confirmed, Shipped, Delivered)

| Status | Meaning |
|---|---|
| Created / Pending | Placed, waiting for payment. The customer can still cancel. |
| Confirmed | Paid. Stock is taken and packing starts. |
| Shipped | At least one package was collected by a driver. |
| Completed | Every package was delivered. |
| Cancelled | Cancelled by the customer or the shop. |

**Package** (on the order board)

| Status | Board step | Meaning |
|---|---|---|
| Pending payment | Awaiting payment | The order is not paid yet. |
| To pack | To pack | Paid; the seller or our staff must pack it. |
| Ready for pickup | Packed, needs a driver | Packed; waiting for a driver. |
| Assigned | Driver coming to collect | A driver has the job. |
| Picked up | On the way | The driver collected it. The customer has the delivery code. |
| Delivered | Delivered | The customer gave the code (or our staff delivered it). |
| Cancelled | Cancelled | The order was cancelled. |

**Payment from a bank account**

| Status | Meaning |
|---|---|
| Started | The customer chose a bank and an account. |
| Code sent | The bank sent the one-time code to the customer's phone. |
| Paid | The money moved; the order is confirmed. |
| Failed / Cancelled / Expired | Nothing was taken. The customer can pay again. |
| Check bank | The bank never answered. Staff check with the bank and settle it. The customer must not pay again. |

**Seller or driver application**: Pending (waiting for the admin), Approved, Rejected (with a reason), Suspended.

## 6.3 Questions and answers

**Customers**

- **I paid but my order still shows "waiting".** If the page said staff are checking with the bank, wait: you will be
  told the result. Do not pay again. Otherwise refresh the order page; if it still shows unpaid, contact the shop.
- **I did not get the bank's code.** Wait 30 seconds and tap **Send the code again**. Check that the account number
  is right and that your phone number is registered with your bank.
- **Where is my delivery code?** On your order page while the package is on its way (and by SMS when text messages
  are on).
- **Can I pay cash on delivery?** No. Online orders are paid before delivery, so drivers never carry cash.
- **Why is the delivery fee different for two products?** It depends on the size of the products and the distance,
  and each seller sends their own package.
- **I forgot my password.** Use **Forgot password?** on the sign-in page, or ask the shop for a temporary password.
- **Who can see my reviews?** Everyone, with your first name and the first letter of your last name.

**Sellers**

- **When do I see an order?** Only after the customer has paid.
- **When am I paid?** Your earning is recorded when the package is delivered. The admin pays out by bank transfer.
- **Can I sell at DP DrukBazaars' counter?** No, marketplace products are sold online only.

**Drivers**

- **Why do I see no jobs?** Your vehicle may be too small for the waiting packages, your licence may have expired, or
  you already have 5 jobs in progress.
- **The customer does not know their code.** They find it on their order page in the app. Do not mark it delivered
  without the code; call the shop if needed.

**Staff**

- **I cannot see a page.** Your role does not have that permission. Ask the admin.
- **My cash count is different.** Close the drawer with the real count and write a note; the manager sees it.
- **A customer wants a refund after 8 days.** Returns are taken within 7 days. Ask the manager.

**Admin**

- **How do I add a staff member?** People & access > Add a person, then choose their role.
- **How do I change what managers can do?** People & access > Roles & permissions.
- **How do I take real payments?** See the Admin guide, section 6: register with the RMA Payment Gateway first.
- **Where are the backups?** See the Admin guide, section 8.
