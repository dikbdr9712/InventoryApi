-- =====================================================================================
-- DP DrukBazaars: database version 15 - deals, more photos, size/colour options (8 Oct 2026).
--
-- item_master.highlight     DEAL or FEATURED: shown on the home page (staff with "Run offers" choose)
-- item_master.deal_ends_at  when a deal stops showing (empty = until changed)
-- item_master.variant_of    this product is an option (a size, a colour) of that main product; the website shows
-- item_master.variant_name  one card for all options, with the choices on the product page ("Size M", "Red")
-- item_photos               more photos of a product (besides its main photo), in order
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

alter table item_master add column highlight varchar(10);
alter table item_master add column deal_ends_at datetime(6);
alter table item_master add column variant_of bigint;
alter table item_master add column variant_name varchar(60);
create index idx_item_variant_of on item_master (variant_of);

create table item_photos (
    position integer not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    item_id bigint not null,
    path varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create index idx_item_photo_item on item_photos (item_id, position);
