-- =====================================================================================
-- DP DrukBazaars: database version 12 - "Pick up myself" (7 Oct 2026).
--
-- orders.fulfilment             DELIVERY (a driver brings it, as before) or PICKUP (the customer collects it).
-- order_packages.self_pickup    the customer collects this package where it is packed (our shop, or the seller's
--                               pickup point): no delivery fee, never on the drivers' job board.
-- order_packages.handed_over_by who gave a collected package to the customer (a staff member, or the seller).
--
-- Every existing order and package stays a delivery. Flyway applies this by itself on the next start.
-- =====================================================================================

alter table orders add column fulfilment varchar(10) not null default 'DELIVERY';
alter table order_packages add column self_pickup bit not null default b'0';
alter table order_packages add column handed_over_by varchar(255);
