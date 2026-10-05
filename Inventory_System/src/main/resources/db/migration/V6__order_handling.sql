-- =====================================================================================
-- DK/Phar Inventory: database version 6 - who handles each order (4 Oct 2026).
--
-- order_packages.packer_email        the staff member packing (or who packed) a package from our own shop
-- order_packages.packing_started_at  when they took it
-- order_packages.rider_assigned_by   the manager who gave the job to a rider (empty when the rider took it)
-- order_packages.courier_email       the staff member who delivered it themselves (no rider)
-- orders.payment_verified_by         who confirmed the payment ("online:BANK" when the bank confirmed it)
-- orders.payment_verified_at         when
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

alter table order_packages add column packer_email varchar(120);
alter table order_packages add column packing_started_at datetime(6);
alter table order_packages add column rider_assigned_by varchar(120);
alter table order_packages add column courier_email varchar(120);
create index idx_pkg_packer on order_packages (packer_email);

alter table orders add column payment_verified_by varchar(120);
alter table orders add column payment_verified_at datetime(6);
