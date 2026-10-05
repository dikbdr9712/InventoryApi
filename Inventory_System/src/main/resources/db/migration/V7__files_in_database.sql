-- =====================================================================================
-- DK/Phar Inventory: database version 7 - uploaded files inside the database (5 Oct 2026).
--
-- stored_files  product photos (area "uploads") and sellers' / drivers' documents (area "partners") when the
--               application runs with app.files.store=database: for hosting where the server's disk does not last
--               (a free Render web service loses every file on disk when it restarts). Empty with the default
--               disk store.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table stored_files (
    size_bytes bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    area varchar(20) not null,
    content_type varchar(100) not null,
    name varchar(120) not null,
    data longblob not null,
    primary key (id)
) engine=InnoDB;

alter table stored_files add constraint uk_stored_file unique (area, name);
