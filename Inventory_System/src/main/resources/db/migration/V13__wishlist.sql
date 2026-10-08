-- =====================================================================================
-- DP DrukBazaars: database version 13 - the wishlist (8 Oct 2026).
--
-- wishlist_items  products a customer saved for later (the heart on a product): one row per customer per
--                 product. By email, like reviews, so an admin changing a person's email moves them along.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table wishlist_items (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    item_id bigint not null,
    user_email varchar(120) not null,
    primary key (id)
) engine=InnoDB;

alter table wishlist_items add constraint uk_wishlist_user_item unique (user_email, item_id);
