-- =====================================================================================
-- DP DrukBazaars: database version 14 - saved addresses, back-in-stock alerts, return requests (8 Oct 2026).
--
-- customer_addresses    a customer's saved delivery addresses (Home, Office, ...): the address, the phone, and
--                       where on the map (a delivery area, or a phone location). One is the default.
-- stock_alerts          "Notify me" on a sold-out product: the customer is told once when it is back
--                       (notified_at), and can ask again later.
-- return_requests       a customer asks to return items of a completed order (within the return window):
-- return_request_items  REQUESTED -> APPROVED or DECLINED by staff -> DONE when staff record the return.
--
-- Every table has a primary key: the hosted MySQL (Aiven) refuses tables without one (sql_require_primary_key).
-- All by email, like reviews, so an admin changing a person's email moves them along.
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table customer_addresses (
    is_default bit not null,
    latitude float(53),
    longitude float(53),
    area_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    label varchar(30) not null,
    phone varchar(20) not null,
    point_label varchar(120),
    user_email varchar(120) not null,
    address varchar(300) not null,
    primary key (id)
) engine=InnoDB;

create index idx_address_user on customer_addresses (user_email);

create table stock_alerts (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    item_id bigint not null,
    notified_at datetime(6),
    user_email varchar(120) not null,
    primary key (id)
) engine=InnoDB;

alter table stock_alerts add constraint uk_stock_alert_user_item unique (user_email, item_id);
create index idx_stock_alert_item on stock_alerts (item_id, notified_at);

create table return_requests (
    created_at datetime(6) not null,
    decided_at datetime(6),
    id bigint not null auto_increment,
    order_id bigint not null,
    status varchar(12) not null,
    reason varchar(20) not null,
    decided_by varchar(120),
    user_email varchar(120) not null,
    staff_note varchar(300),
    details varchar(500),
    primary key (id)
) engine=InnoDB;

create index idx_return_request_order on return_requests (order_id);
create index idx_return_request_status on return_requests (status, created_at);

create table return_request_items (
    quantity integer not null,
    item_id bigint not null,
    order_item_id bigint not null,
    request_id bigint not null,
    item_name varchar(255) not null,
    primary key (request_id, order_item_id) -- one line per product of the order (hosted MySQL requires a primary key)
) engine=InnoDB;

alter table return_request_items add constraint fk_return_request_items_request foreign key (request_id) references return_requests (id);
