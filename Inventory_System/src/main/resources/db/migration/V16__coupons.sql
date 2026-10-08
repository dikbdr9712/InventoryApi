-- =====================================================================================
-- DP DrukBazaars: database version 16 - coupon codes (8 Oct 2026).
--
-- coupons             a code (DRUK10) for a percent or an amount off the items of an online order: from when, until
--                     when, the smallest order, the most it can take off, how many times in all and per customer.
--                     DP DrukBazaars pays for it: sellers earn their full share.
-- coupon_redemptions  which order used which coupon, for how much (an order that is cancelled does not count).
-- orders.coupon_code, orders.coupon_discount   the code an order used and what it took off (the total is after it).
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table coupons (
    active bit not null,
    per_customer_limit integer not null,
    usage_limit integer,
    created_at datetime(6) not null,
    ends_at datetime(6),
    id bigint not null auto_increment,
    starts_at datetime(6),
    max_discount decimal(12,2),
    min_order decimal(12,2) not null,
    discount_value decimal(12,2) not null,
    kind varchar(10) not null,
    code varchar(30) not null,
    created_by varchar(120),
    description varchar(200),
    primary key (id)
) engine=InnoDB;

alter table coupons add constraint uk_coupon_code unique (code);

create table coupon_redemptions (
    amount decimal(12,2) not null,
    coupon_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    order_id bigint not null,
    user_email varchar(120) not null,
    primary key (id)
) engine=InnoDB;

alter table coupon_redemptions add constraint uk_coupon_redemption_order unique (order_id);
create index idx_coupon_redemption_coupon on coupon_redemptions (coupon_id, user_email);

alter table orders add column coupon_code varchar(30);
alter table orders add column coupon_discount decimal(12,2);
