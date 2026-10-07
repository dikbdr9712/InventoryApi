# DP DrukBazaars: documentation

DP DrukBazaars is an online shop and marketplace for Bhutan. Customers order online and pay from their own bank
account; local sellers sell through it; drivers deliver; staff run the shop counter, the stock and the orders.

This folder explains the whole system. Pick the document for what you need:

| Document | For whom | What is in it |
|---|---|---|
| [1. System overview](01-system-overview.md) | Everyone (owner, staff, partners, new developers) | What the system is, who uses it, its parts, how an order travels, how money and stock work, safety and privacy. Simple language. |
| [2. User guide](02-user-guide.md) | Customers, sellers, delivery drivers | Every step: sign up, shop, pay, follow and rate an order; apply as a seller or driver, pack, deliver, get paid. |
| [3. Staff guide](03-staff-guide.md) | Shop staff (cashiers, packers, managers) | Day-to-day work: the counter (POS), cash drawers, returns, the order board, checking payments, products and stock, customers, messages, reviews, the dashboard. |
| [4. Admin guide](04-admin-guide.md) | The owner and administrators | Setting up and running the system: people, roles and permissions, the marketplace, agreements, the website, payments, email and SMS, hosting, backups, problems and fixes. |
| [5. Technical reference](05-technical-reference.md) | Developers | Architecture, running it on a computer, settings, code layout, security, database, every API address, business rules, tests, building. |
| [6. Glossary and FAQ](06-glossary-and-faq.md) | Everyone | The words used in the system and their meaning, every status, and common questions. |

Other documents that already exist:

| Document | What is in it |
|---|---|
| [DEPLOY-RENDER.md](../DEPLOY-RENDER.md) | Free hosting step by step: Aiven (database) and Render (server and website), email (Brevo or Mailjet), text messages. |
| [DEPLOY.md](../DEPLOY.md) | Hosting on your own Linux server (Nginx, HTTPS, systemd), and connecting the real RMA Payment Gateway. |
| [database/README.md](../database/README.md) | The database scripts: a new server, the first admin, upgrades, backups, and the list of database versions. |
| `Inventory_System/PROJECT-NOTES.md` | The history of the project, step by step, with the reasons behind decisions (a copy is in the website project). |

## Where things are

| | Server (API) | Website |
|---|---|---|
| GitHub | `dikbdr9712/InventoryApi` | `dikbdr9712/InventoryWeb` |
| Folder on the development computer | `D:\Inventory\Inventory_System` | `D:\Angular\inventory-project` |
| Live address (free hosting) | `https://inventoryapi-qqjz.onrender.com` | `https://inventoryweb-a461.onrender.com` |

## Keeping this up to date

When something changes in the system, change the document that describes it in the same commit. The staff and user
guides describe what people see, so update them whenever a screen or a button changes.
