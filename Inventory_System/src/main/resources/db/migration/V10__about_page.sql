-- =====================================================================================
-- DP DrukBazaars: database version 10 - the About page, managed by staff (7 Oct 2026).
--
-- site_texts    the About page's texts (introduction, mission, vision) and whether the live numbers show.
--               A text that was never changed has no row: the page then uses the wording built into the program.
-- team_members  the people on "Meet the team": name, role, a short introduction, photo, order, shown or hidden.
--               Starts with the two people who were on the page before.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table site_texts (
    updated_at datetime(6),
    text_key varchar(40) not null,
    updated_by varchar(120),
    text_value varchar(2000),
    primary key (text_key)
) engine=InnoDB;

create table team_members (
    sort_order integer not null,
    visible bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    name varchar(80) not null,
    role varchar(80) not null,
    photo varchar(200),
    bio varchar(400),
    primary key (id)
) engine=InnoDB;

create index idx_team_order on team_members (sort_order);

insert into team_members (sort_order, visible, created_at, name, role, photo, bio) values
    (1, b'1', now(6), 'Dik Bdr Galley', 'System designer and developer', 'Images/Dik(me1).jpg', null),
    (2, b'1', now(6), 'Pharmith Lepcha', 'System designer and counsellor', 'Images/Pharmith.jpg', null);
