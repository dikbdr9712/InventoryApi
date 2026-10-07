-- =====================================================================================
-- DK/Phar Inventory: database version 9 - "forgot password" by a code (7 Oct 2026).
--
-- password_reset_codes  a 6-digit code sent by email or text message (channel EMAIL or SMS) to someone who forgot
--                       their password. Only a BCrypt hash of the code is kept. A code works for 10 minutes and
--                       for 5 tries; asking for a new one cancels the older ones. The right code gives a one-time
--                       "choose a new password" ticket (password_reset_tokens), valid for 15 minutes.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table password_reset_codes (
    attempts integer not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    used_at datetime(6),
    user_id bigint not null,
    channel varchar(10) not null,
    request_ip varchar(64),
    code_hash varchar(100) not null,
    primary key (id)
) engine=InnoDB;

create index idx_reset_code_user on password_reset_codes (user_id, channel, created_at);
create index idx_reset_code_created on password_reset_codes (created_at);
