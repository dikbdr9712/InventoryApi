-- =====================================================================================
-- DP DrukBazaars: database version 17 - profile photos (8 Oct 2026).
--
-- users.photo_path   the person's own profile photo (/uploads/user-12-ab12cd34.jpg); empty = their initials.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

alter table users add column photo_path varchar(255);
