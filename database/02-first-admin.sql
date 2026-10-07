-- =====================================================================================
-- DP DrukBazaars: step 3 on a NEW server: make the first administrator.
--
-- A new database has no accounts. Nobody can be made admin from the website until there is
-- one admin, so the first one is made here:
--
--   1. Start the application, open the website and create a normal account with Sign up
--      (use the owner's real email: password reset links go there).
--   2. Put that email below instead of owner@example.com, then run this file:
--         mysql -u root -p inventorydb < 02-first-admin.sql
--   3. Sign out and in again on the website: the staff menu and the admin pages appear.
--      From then on, give other people their roles in People (Admin > Users), not here.
--
-- No password is stored in this file: the person chose it on the website.
-- =====================================================================================

SET @owner_email = 'owner@example.com';

UPDATE users
   SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN'),
       active  = b'1'
 WHERE email = @owner_email;

-- Check: should show one row with the role ADMIN. No row = the email is wrong or not signed up yet.
SELECT u.id, u.name, u.email, r.name AS role
  FROM users u JOIN roles r ON r.id = u.role_id
 WHERE u.email = @owner_email;
