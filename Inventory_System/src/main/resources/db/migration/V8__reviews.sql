-- =====================================================================================
-- DK/Phar Inventory: database version 8 - ratings and reviews (7 Oct 2026).
--
-- product_reviews  a customer's stars (1-5) and comment for a product delivered to them: one per customer per
--                  product (rating again changes it). Staff can hide one and reply publicly.
-- order_feedback   the customer's stars for the shop's service and for the delivery of one delivered order, and a
--                  comment. The delivery stars count for the driver who delivered it (rider_id).
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table product_reviews (
    hidden bit not null,
    rating integer not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    item_id bigint not null,
    order_id bigint not null,
    replied_at datetime(6),
    updated_at datetime(6),
    display_name varchar(60) not null,
    replied_by varchar(120),
    user_email varchar(120) not null,
    reply varchar(500),
    comment varchar(1000),
    primary key (id)
) engine=InnoDB;

alter table product_reviews add constraint uk_review_user_item unique (user_email, item_id);
create index idx_review_item on product_reviews (item_id, hidden);

create table order_feedback (
    delivery_rating integer not null,
    hidden bit not null,
    service_rating integer not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    order_id bigint not null,
    replied_at datetime(6),
    rider_id bigint,
    updated_at datetime(6),
    display_name varchar(60) not null,
    replied_by varchar(120),
    user_email varchar(120) not null,
    reply varchar(500),
    comment varchar(1000),
    primary key (id)
) engine=InnoDB;

alter table order_feedback add constraint uk_feedback_order unique (order_id);
create index idx_feedback_rider on order_feedback (rider_id);
