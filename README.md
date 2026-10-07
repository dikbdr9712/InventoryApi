# DP DrukBazaars: the server (API)

The server of DP DrukBazaars, an online shop and marketplace for Bhutan. It keeps the products, stock, orders,
payments, deliveries, people and permissions, and answers the website (repository **InventoryWeb**).
Built with Spring Boot 4.1 (Java 26) and MySQL 8.

## Documentation

Start with [docs/README.md](docs/README.md).

| Document | For whom |
|---|---|
| [1. System overview](docs/01-system-overview.md) | Everyone: what the system is and how it works, in simple language |
| [2. User guide](docs/02-user-guide.md) | Customers, sellers, delivery drivers: every step |
| [3. Staff guide](docs/03-staff-guide.md) | Shop staff: counter, orders, stock, customers, reports |
| [4. Admin guide](docs/04-admin-guide.md) | The owner: people and permissions, marketplace, payments, email, backups, problems |
| [5. Technical reference](docs/05-technical-reference.md) | Developers: architecture, settings, security, database, API |
| [6. Glossary and FAQ](docs/06-glossary-and-faq.md) | Everyone |
| [DEPLOY-RENDER.md](DEPLOY-RENDER.md) | Free hosting step by step (Render and Aiven) |
| [DEPLOY.md](DEPLOY.md) | Your own Linux server, and the real RMA Payment Gateway |
| [database/README.md](database/README.md) | Database scripts, backups, database versions |

## Running it on your computer

1. Create the database: `CREATE DATABASE inventorydb CHARACTER SET utf8mb4;`
2. Create `Inventory_System/secrets.properties` with `spring.datasource.password=YOUR_PASSWORD` (this file is never committed).
3. From `Inventory_System`:

```bash
mvn spring-boot:run
```

The server listens on port 8080 and creates the tables by itself (Flyway). Run the tests with `mvn test`.
See the [Technical reference](docs/05-technical-reference.md#3-running-it-on-your-computer) for details.
