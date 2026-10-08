# 4. Admin guide

*For the owner and administrators: setting up and running DP DrukBazaars.*

- [1. First setup](#1-first-setup)
- [2. Roles and permissions](#2-roles-and-permissions)
- [3. People & access](#3-people--access)
- [4. The marketplace](#4-the-marketplace)
- [5. The website pages](#5-the-website-pages)
- [6. Payments](#6-payments)
- [7. Email and text messages](#7-email-and-text-messages)
- [8. Hosting, updates and backups](#8-hosting-updates-and-backups)
- [9. Settings on the server](#9-settings-on-the-server)
- [10. Security checklist](#10-security-checklist)
- [11. Problems and fixes](#11-problems-and-fixes)

## 1. First setup

1. Put the system online: follow [DEPLOY-RENDER.md](../DEPLOY-RENDER.md) (free hosting) or
   [DEPLOY.md](../DEPLOY.md) (your own server). The database tables are created automatically on the first start.
2. On the website, **sign up** with your own email.
3. Make that account the first admin, once, with the SQL in `database/02-first-admin.sql` (or step 5 of
   DEPLOY-RENDER.md), run in MySQL Workbench against the live database. Sign out and in again.
4. From then on everything is done in the website: **People & access** for staff, **Marketplace** for sellers and
   drivers. No more SQL is needed.
5. Then set up, in this order:
   - **Marketplace > Settings**: commission, delivery fees, the shop's location and address, delivery areas.
   - **Products**: add your products, or restock them with batches and expiry dates.
   - **People & access**: add your staff and give them roles.
   - **Marketplace > Agreements**: have the Terms, Seller Agreement and Driver Agreement checked by a legal adviser
     and publish your own versions.
   - **Website** (staff bar): your contact details and links, the About page texts and your team.
   - **Email** (and SMS if you want it), see section 7.

## 2. Roles and permissions

A **role** is a group of **permissions** (tick boxes). Each person has one role. The built-in roles and what they
can do at the start:

| Permission | Admin | Manager | Controller | Seller | Driver | Customer |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Use the point of sale | ✓ | ✓ | ✓ | | | |
| Give extra discounts | ✓ | ✓ | | | | |
| Manage all cash drawers | ✓ | ✓ | | | | |
| Take returns and refund | ✓ | ✓ | | | | |
| See orders | ✓ | ✓ | ✓ | | | |
| Pack, send and cancel orders | ✓ | ✓ | ✓ | | | |
| Plan and assign orders | ✓ | ✓ | | | | |
| Verify payments | ✓ | ✓ | | | | |
| Add and edit products | ✓ | ✓ | | | | |
| Restock | ✓ | ✓ | ✓ | | | |
| Read customer messages | ✓ | ✓ | ✓ | | | |
| See the sales dashboard | ✓ | ✓ | | | | |
| See customers | ✓ | ✓ | ✓ | | | |
| Edit customer details | ✓ | ✓ | | | | |
| Manage reviews | ✓ | ✓ | | | | |
| Run offers: deals and coupons | ✓ | ✓ | | | | |
| Manage users and roles | ✓ | | | | | |
| Run the marketplace | ✓ | | | | | |
| Edit the website pages | ✓ | | | | | |
| Seller dashboard, own products, pack orders, earnings | | | | ✓ | | |
| Driver dashboard, take and deliver jobs, earnings | | | | | ✓ | |

- **Admin** always has every shop permission; it cannot be taken away.
- **Manager** runs the shop day to day. **Controller** handles stock and sends orders out, but does not approve money.
- **Seller** and **Driver** are given only by approving an application (or by choosing it for a person, see 3).
- You can change the tick boxes of any role, and create your own roles (for example **Cashier**) in
  **People & access > Roles & permissions**. Choose a permission marked **Sensitive** with care.

Rules the system keeps, whatever you tick:

- Nobody can change their own account or their own role.
- Only an Admin can give "Manage users and roles", "Run the marketplace" or the Admin role.
- There is always at least one active Admin.
- The person who packs should not be the one who approves payments: by default Controllers cannot verify payments.

## 3. People & access

**People > People & access** has three parts.

**People**
- **Add a person**: name, email, phone, role and a starting password (at least 6 characters). Give it to them and
  ask them to choose their own in **My profile** at their first sign-in.
- **Change role**: choose another role. Choosing **Seller** or **Delivery driver** approves their application if they
  sent one; otherwise you fill in their shop (or vehicle and licence), CID and bank details yourself.
  Leaving the seller or driver role pauses that account.
- **Edit details**: change a person's name, sign-in **email** or **phone number** (8 digits). An email or phone
  already used by another account is refused. A new email signs them out; they sign in with the new email and the
  same password. Their orders, payments, reviews, notifications and agreements move with them, and both the old and
  the new address get an email about it. The Activity list keeps the old and the new values. You cannot edit your
  own account here (ask another admin), and only an admin can edit another admin or a person who manages people.
- **Switch off / on**: a switched-off account is signed out at once and cannot sign in.
- **Reset password**: gives a one-time temporary password to pass on. The person chooses their own in My profile.
  (Customers can also reset it themselves with a code, see the User guide.)

**Roles & permissions**: tick boxes per role, new roles, delete unused roles.

**Activity**: who did what and when: role changes, changed emails or phones, accounts switched off, password resets, approvals, payouts,
website changes, cash drawers, and more.

## 4. The marketplace

**Marketplace > Sellers & drivers** (Run the marketplace).

### Applications

1. The **Sellers & drivers** tab lists applications waiting for a decision (the number shows on the tab).
2. Open one: check the details and open the documents (CID, trade licence, driving licence). Documents are private.
3. **Approve**, or **Reject** with a reason. Later you can **Suspend** a seller or driver. The person is told.
   A driver with an expired licence cannot be approved.

### Commission

Set the default commission (percent of the seller's sale) in **Settings**, and a different one for one seller on
their card. A change applies to new orders only.

### Delivery prices (Settings)

| Setting | Meaning | Starting value |
|---|---|---|
| Base fee per size | small / medium / large / bulky | Nu. 50 / 80 / 200 / 500 |
| Per km per size | for each km beyond the included km | Nu. 10 / 12 / 20 / 35 |
| Included km | km included in the base fee | 2 km |
| Driver share | % of the delivery fee paid to the driver | 80% |
| Maximum distance | further away cannot be ordered | 30 km |
| Unknown distance | used when a location is missing (marked "estimated") | 5 km |
| Shop location and address | where our own packages are collected | |
| Delivery areas | named areas customers can choose at checkout instead of sharing their location | |

Road distance is worked out from the straight line between the two points × 1.35; no map service is needed.
The fee is rounded up to the next Nu. 5.

### Payouts, adjustments and returns

- The **Payouts** tab lists sellers and drivers you owe money to.
- Pay them by bank transfer, then record the payout with the **journal number**. You cannot pay more than is owed.
- **Adjust**: take money back or add money for any other reason, always with a reason.
- **Returns**: when staff take back a seller's product, the seller's share of it (the price minus the commission) is
  taken off their earnings automatically, never more than that package earned. It shows as "Return" in their book.

### Agreements

**Agreements**: the Terms of Use and Privacy (customers), the Seller Agreement and the Driver Agreement.

- Publishing a new version never changes an old one; the system keeps who accepted which version and when.
- After a new version, customers, sellers and drivers are asked to accept it before they continue.
- The first versions are templates. Have them checked by a legal adviser before you go live.

## 5. The website pages

**Website** in the staff bar (Edit the website pages):

- **Contact details and links**: the phone, more phone numbers (Contact page only), email and address, and the shop's
  pages on Facebook, Instagram, YouTube and TikTok. They show in the footer, on the Contact, About and Forgot password
  pages, and are printed on receipts, invoices and credit notes. A link left empty has no icon. Links must start with
  `https://`.

- **Texts**: the introduction, our mission and our vision, with **Use the original wording**; and **Show the live
  numbers** (products, local sellers, orders delivered, average rating; a 0 is never shown).
- **Team**: **Add a person** (name, role, a short introduction, photo), **Edit**, **Hide** / **Show**, move up or down,
  remove. Changes show on the website at once.

### The app

The website is also an installable app (a "progressive web app"): customers, sellers, drivers and staff install it
from the website (**Get the app**, `/app`). There is nothing to set up and nothing to pay. Each website update
reaches the app by itself.

- Share the link `https://<your website>/app` (on social media, on a poster with a QR code) so people find the steps.
- The icon is made from the shop logo (`public/icons` in the website project). To change it, replace those
  pictures (keep the same names and sizes) and push.

**In Google Play (later)**: once the website has its own domain, the same app can be listed in Google Play:
1. Create a Google Play developer account (a one-time fee of US$25).
2. Open [pwabuilder.com](https://www.pwabuilder.com), type the website's address, choose **Package for stores >
   Android**, and download the package (keep its signing key safe; every update needs it).
3. Put the `assetlinks.json` file it gives you on the website at `/.well-known/assetlinks.json` (it proves the
   app and the website belong together), then upload the package in the Play Console.

**In Apple's App Store**: Apple asks US$99 a year and a Mac to build the app, and refuses apps that only show a
website. iPhone users install from Safari instead (above).

## 6. Payments

Online orders are paid from the customer's bank account through the **RMA Payment Gateway**.
The server setting `APP_PAYMENTS_BANK_MODE` decides how:

| Mode | What happens |
|---|---|
| `off` | Online payment is switched off; checkout says it is not available. |
| `test` | **Test mode**: no bank is contacted and no money moves. The pages say "TEST MODE: no real money". Code `123456`; an account ending in 0000 = "not found", 9999 = "not enough money", 5555 = "the bank never answers". On the live site it also needs `APP_PAYMENTS_BANK_TEST_ON_LIVE_SITE=true` (only for a demo). |
| `rma` | **Real money** through the RMA Payment Gateway. |

To take real money:

1. Register DP DrukBazaars as a merchant with the RMA Payment Gateway (you need a business bank account). RMA gives
   you a merchant (beneficiary) id, a test address, the security keys and their documents.
2. Follow the steps in [DEPLOY.md](../DEPLOY.md) (section on the RMA Payment Gateway): the keys, the address and the
   merchant id. On Render, upload the key files as **Secret Files**.
3. Try it on RMA's test address first, then their live address.
4. Set `APP_PAYMENTS_BANK_MODE=rma` and remove `APP_PAYMENTS_BANK_TEST_ON_LIVE_SITE`. The "TEST MODE" wording
   disappears by itself.

When the bank never answers a payment, it waits in **Verify payments > Bank payments to check** (see the Staff guide).

## 7. Email and text messages

### Email

Needed for: password reset codes by email, order updates, notices. Without it, the "Forgot password?" page offers
only the ways that work and otherwise tells customers to call the shop.

Render's free plan blocks the usual email ports, so use **Brevo** or **Mailjet** on port **2525**. The step-by-step is
in [DEPLOY-RENDER.md](../DEPLOY-RENDER.md). The settings:

| Setting | Brevo | Mailjet |
|---|---|---|
| `APP_MAIL_ENABLED` | `true` | `true` |
| `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` | `in-v3.mailjet.com` |
| `SPRING_MAIL_PORT` | `2525` | `2525` |
| `SPRING_MAIL_USERNAME` | the SMTP login | the API key |
| `SPRING_MAIL_PASSWORD` | the SMTP key | the secret key |
| `APP_MAIL_FROM` | `DP DrukBazaars <dpdrukbazaars@gmail.com>` (a sender you confirmed with the service) | same |

### Text messages (SMS)

Optional, and never free. Used for: the delivery code, and password reset codes by text message.

- A bulk SMS account from **B-Mobile** or **TashiCell**: `APP_SMS_ENABLED=true` and `APP_SMS_URL` = the address they
  give, with `{to}` for the 8-digit number and `{text}` for the message.
- **Twilio** (an upgraded account; the free trial cannot send our texts): `APP_SMS_ENABLED=true`,
  `APP_SMS_PROVIDER=twilio`, `APP_SMS_TWILIO_ACCOUNT_SID`, `APP_SMS_TWILIO_AUTH_TOKEN`, `APP_SMS_TWILIO_FROM`
  (for example `DrukBazaars`).

## 8. Hosting, updates and backups

### Updates

1. Push the changes to GitHub (both repositories if both changed).
2. Render builds and deploys by itself. Database changes (new files in `db/migration`) are applied automatically on
   the next start. Nothing has to be run by hand.
3. After a deploy, check `https://inventoryweb-a461.onrender.com/api/auth/forgot-password` or open the website.

### Backups

- **Free hosting (Aiven)**: in MySQL Workbench, connect to the Aiven database and use **Server > Data Export**
  (all tables, "Export to Self-Contained File"). Do it at least once a week and keep copies in two places. On Render
  the photos and documents are inside the database, so this one file holds everything.
- **Your own server**: `database/backup.sh` (Linux) or `database/backup.ps1` (Windows) saves the database and the
  uploaded files every night and keeps 14 days. See [database/README.md](../database/README.md).
- Test a backup now and then by restoring it into a spare database.

### Free plan limits

- The server sleeps after 15 minutes without visitors and takes 1 to 3 minutes to wake. A free monitor such as
  UptimeRobot opening `/actuator/health` every 10 minutes keeps it awake.
- The Aiven free database has 1 GB; photos count. Use photos under 1 MB.

## 9. Settings on the server

Set on Render under the server's **Environment** page (or in `/etc/drukbazaars/drukbazaars.env` on your own server).
Secrets go there only, never in the code.

| Setting | What it is |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | The database |
| `APP_PUBLIC_URL` | The website's address (used in links in emails) |
| `APP_CORS_ALLOWED_ORIGINS` | The website's address, when the website and the server have different addresses |
| `APP_FILES_STORE` | `database` on Render (photos and documents in the database), `disk` on your own server |
| `APP_MAIL_*`, `SPRING_MAIL_*` | Email (section 7) |
| `APP_SMS_*` | Text messages (section 7) |
| `APP_PAYMENTS_BANK_MODE` and `APP_PAYMENTS_BANK_RMA_*` | Payments (section 6) |
| `SESSION_TIMEOUT` | How long people stay signed in without activity (default `4h`) |

The full list, with the values used on a developer's computer, is in the [Technical reference](05-technical-reference.md#4-settings).

## 10. Security checklist

- [ ] Change the MySQL password if it was ever shared or shown on screen, and update it on Render.
- [ ] Every staff member has their own account; nobody shares passwords.
- [ ] Give each role only the permissions it needs. Review **People & access > Activity** now and then.
- [ ] Switch off the accounts of people who leave, the same day.
- [ ] Keep the RMA keys and the email/SMS keys only in Render's Environment and Secret Files.
- [ ] Make and test backups (section 8).
- [ ] Have the agreements checked by a legal adviser.

## 11. Problems and fixes

| Problem | Why | What to do |
|---|---|---|
| The website opens but products do not load | The free server is waking up | Wait 1 to 3 minutes and reload. A monitor keeps it awake (section 8). |
| "Invalid CORS request" when signing up or saving | The server does not know the website's address | Set `APP_CORS_ALLOWED_ORIGINS` to the website's address, then redeploy. |
| "Forgot password?" says "Ask us to reset it" | No email (or SMS) set up on the server | Set up email (section 7). Meanwhile reset passwords in People & access. |
| Emails do not arrive | Wrong login or key, sender not confirmed, IP blocking at Brevo | Render > Logs, search for `Email to`: it shows the reason. |
| The payment pages say "TEST MODE" | Not yet connected to the RMA Payment Gateway | Section 6. Do not hide this text while payments are pretend. |
| A customer paid but the order is not confirmed | The bank never answered | **Verify payments > Bank payments to check**: Money arrived / Nothing was taken. |
| Photos disappeared after a restart | Files were kept on Render's disk | Set `APP_FILES_STORE=database` and upload the photos again. |
| The server does not start after a change | Often: an old file in `db/migration` was edited | Never change V1 to V10 once applied; put changes in a new file (V11, ...). Restore the old file from Git. |
| A staff member cannot see a page | Their role lacks the permission | People & access > Roles & permissions. |
| The footer shows a wrong phone number or link | Contact details changed | **Website > Contact details and links**. |
| Nobody was warned about low stock | No warning level on the product | Edit the product: **Warn me when stock reaches**. |
| A seller sees no orders | Only paid orders appear; the application must be approved and the agreement accepted | Check their status in Marketplace. |
| A driver sees no jobs | Their vehicle is too small for the packages, their licence expired, or they already have 5 jobs | Check their card in Marketplace; they send a renewed licence from My deliveries. |
| Nobody can sign in as admin | The only admin was locked out | Another admin resets the password; or run `database/02-first-admin.sql` for a signed-up account. |
| Everyone was signed out | The server restarted with a changed session setting, or a password was changed | Sign in again. Sign-ins normally survive restarts. |
