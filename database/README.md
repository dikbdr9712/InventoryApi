# DK/Phar Inventory: the database

MySQL 8.0 or newer. One database, `inventorydb`, in `utf8mb4`.

The tables are defined in **`Inventory_System/src/main/resources/db/migration/`** and built by
**Flyway** when the application starts. Nobody creates or changes tables by hand any more.

| File | What it is | When |
|---|---|---|
| `01-create-database-and-user.sql` | Creates the empty database and the application's own MySQL account | New server, once |
| `../Inventory_System/src/main/resources/db/migration/V1__initial_schema.sql` | Every table (30 with Flyway's own) | Runs **by itself** on first start |
| `../Inventory_System/src/main/resources/db/migration/V2__stock_batches.sql` | Stock by batch: `stock_batches`, `order_item_batches`, `order_items.unit_cost` | Runs **by itself** (new and upgraded databases) |
| `../Inventory_System/src/main/resources/db/migration/V3__sessions_in_database.sql` | Sign-ins kept in the database (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`), so a restart signs nobody out | Runs **by itself** |
| `../Inventory_System/src/main/resources/db/migration/V4__bank_payments.sql` | Paying from a bank account: `bank_payments` (last 4 digits only) and `payment_events` | Runs **by itself** |
| `../Inventory_System/src/main/resources/db/migration/V8__reviews.sql` | Ratings: `product_reviews` (stars and comment per customer and product) and `order_feedback` (service and delivery stars per order) | Runs **by itself** |
| `../Inventory_System/src/main/resources/db/migration/V7__files_in_database.sql` | `stored_files`: photos and documents kept in MySQL when `app.files.store=database` (free hosting) | Runs **by itself** |
| `../Inventory_System/src/main/resources/db/migration/V6__order_handling.sql` | Who handles each order: packer, who gave the job to a driver, staff courier, who confirmed the payment | Runs **by itself** |
| `../Inventory_System/src/main/resources/db/migration/V5__journal_numbers.sql` | Journal numbers: `orders.payment_reference` (counter sales) and `bank_payments.bank_reference` (the bank's journal) | Runs **by itself** |
| `02-first-admin.sql` | Turns the owner's signed-up account into the first admin | New server, once |
| `upgrade-existing-database.sql` | Brings a database made **before** Flyway up to version 1 (4 tables, 29 columns) | Existing databases, once |
| `backup.sh` / `backup.ps1` | Nightly backup of the database and the uploaded files, 14 days kept | Every night |

## A new server

1. Install MySQL 8 and Java 26.
2. Run `01-create-database-and-user.sql` as MySQL root, **after** replacing the password in it with a long random one:
   ```
   mysql -u root -p < 01-create-database-and-user.sql
   ```
3. Put the same password in the server's settings as `SPRING_DATASOURCE_PASSWORD` (see `../DEPLOY.md`).
4. Start the application. Flyway creates every table; the application then adds what it needs to work:
   the roles and their permissions (ADMIN, MANAGER, CONTROLLER, SELLER, RIDER, USER), the seller, driver
   and customer agreements, and the marketplace settings. Nothing else has to be loaded.
5. Open the website, **Sign up** with the owner's email, then put that email in `02-first-admin.sql` and run it:
   ```
   mysql -u root -p inventorydb < 02-first-admin.sql
   ```
6. Sign out and in again: you are the admin. Give other people their roles in **People**.
7. Set up the nightly backup (`backup.sh`, instructions inside).

## The existing development database (inventorydb on this computer)

It was built by the application before Flyway existed, and it is missing what was added on 2 Oct 2026
(delivery prices, notifications, password reset links, online payments). Once:

```
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p inventorydb < D:\Inventory\database\upgrade-existing-database.sql
```

or open the file in MySQL Workbench, choose `inventorydb`, and run it all. It only **adds** (nothing is
deleted or changed) and it is safe to run twice. Then start the application: Flyway records the database
as version 1, applies version 2 (stock batches) at once, and from then on applies new versions by itself.
The stock you already have becomes one "opening" batch per product at its cost price (tested on a full copy of
inventorydb: 7 products, 175 units, every total matched).

If the application is started without it, it stops at once with a message like
`Schema validation: missing table [delivery_areas]`: run the file and start again.

This was tested on a copy of inventorydb's structure on 2 Oct 2026: the upgraded copy started and
passed the check of every table.

## Changing tables later (for developers)

1. Change the entity class in Java.
2. Add a **new** file next to V1: `V2__short_description.sql` with the `ALTER TABLE ...` (or `CREATE TABLE ...`).
   Never edit a version that has already run anywhere: Flyway notices and refuses to start.
3. Start the application: Flyway applies V2 everywhere it has not run yet, and Hibernate checks that the
   tables now match the code (`spring.jpa.hibernate.ddl-auto=validate`).

## Backups and restoring

`backup.sh` (Linux, cron) and `backup.ps1` (Windows, Task Scheduler) save the database and the
`uploads` and `private-uploads` folders. The private uploads hold identity documents: keep backups
where only the owner can read them, and copy them off the server every day.

Restore into an empty database made with step 2:

```
gunzip -c inventorydb-2026-10-02.sql.gz | mysql -u root -p inventorydb      # Linux
mysql -u root -p inventorydb < inventorydb-2026-10-02.sql                   # Windows
```

Try a restore on a spare database now and then: a backup you have never restored is only a hope.

## The password that was in git

Until 2 Oct 2026 the MySQL root password was written in `application.properties`, and that file is in
the public GitHub repository, including its history. It is now in `Inventory_System/secrets.properties`
(git ignores it). **Change the MySQL password** (the old one stays readable in the git history), for example in MySQL Workbench
or with:

```
ALTER USER 'root'@'localhost' IDENTIFIED BY 'a-new-long-password';
```

then put the new password in `Inventory_System/secrets.properties` as `spring.datasource.password=...`.
