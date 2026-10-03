-- =====================================================================================
-- DK/Phar Inventory: database version 2 - stock by batch (2 Oct 2026).
--
-- The same product bought at different prices (or with different expiry dates) is kept as separate
-- batches, sales take from the batch that expires first, and every sold line records its real cost.
-- Flyway applies this by itself on the next start, on every database (new or upgraded).
--
-- Stock that already exists becomes one "opening" batch per product at its cost price: the application
-- does that when it starts (StockService.reconcile), so no data has to be loaded here.
-- =====================================================================================

create table stock_batches (
    expiry_date date,
    quantity_left integer not null,
    quantity_received integer not null,
    unit_cost decimal(12,2) not null,
    id bigint not null auto_increment,
    item_id bigint not null,
    received_at datetime(6) not null,
    source varchar(12) not null,
    status varchar(12) not null,
    batch_no varchar(60),
    supplier varchar(100),
    received_by varchar(120),
    note varchar(300),
    primary key (id)
) engine=InnoDB;

create index idx_batch_item on stock_batches (item_id, status);
create index idx_batch_expiry on stock_batches (expiry_date);

create table order_item_batches (
    quantity integer not null,
    returned_quantity integer not null,
    unit_cost decimal(12,2) not null,
    batch_id bigint not null,
    id bigint not null auto_increment,
    order_item_id bigint not null,
    primary key (id)
) engine=InnoDB;

create index idx_oib_line on order_item_batches (order_item_id);
create index idx_oib_batch on order_item_batches (batch_id);

-- what one sold unit cost us (empty for sales made before batches were kept)
alter table order_items add column unit_cost decimal(12,2);
