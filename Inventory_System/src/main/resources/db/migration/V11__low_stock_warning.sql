-- =====================================================================================
-- DP DrukBazaars: database version 11 - low-stock warnings (7 Oct 2026).
--
-- item_master.low_stock_threshold  "Warn me when stock reaches" from the product form. When a sale, a write-off or
--                                  a count takes the stock down to this number (or below), the people who restock
--                                  are told once (the seller, for a seller's product). Empty = no warning.
--                                  Products made before this version have no warning until it is set.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

alter table item_master add column low_stock_threshold integer;
