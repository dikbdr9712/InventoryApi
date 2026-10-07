-- =====================================================================================
-- DP DrukBazaars: step 1 on a NEW server (run once, as the MySQL root user).
--
-- Creates the empty database and the account the application uses to reach it.
-- The application builds every table by itself the first time it starts (Flyway runs
-- src/main/resources/db/migration/V1__initial_schema.sql), so there is nothing else to load.
--
-- BEFORE RUNNING: replace  CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD  below with a long random password
-- (at least 20 characters, for example from a password manager). Put the same password in the
-- server's environment as SPRING_DATASOURCE_PASSWORD (see DEPLOY.md). Never commit it to git.
--
-- How:  mysql -u root -p < 01-create-database-and-user.sql
-- =====================================================================================

-- utf8mb4: every language and symbol (Dzongkha, Nepali, emoji) is stored correctly
CREATE DATABASE IF NOT EXISTS inventorydb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- The application's own account: it can only reach this one database, and only from this server.
-- If MySQL runs on a different machine than the application, use 'drukbazaars_app'@'APP-SERVER-ADDRESS' instead.
CREATE USER IF NOT EXISTS 'drukbazaars_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD';

-- What the application needs: read and write data, and (for Flyway) create and change tables.
-- Not given: DROP, FILE, PROCESS, SUPER, GRANT ... (it cannot delete the database or read server files).
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON inventorydb.* TO 'drukbazaars_app'@'localhost';

FLUSH PRIVILEGES;

-- Check: should list the grants above
SHOW GRANTS FOR 'drukbazaars_app'@'localhost';
