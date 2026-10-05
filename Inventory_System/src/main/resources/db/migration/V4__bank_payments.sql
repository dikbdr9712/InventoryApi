-- =====================================================================================
-- DK/Phar Inventory: database version 4 - paying from a bank account (4 Oct 2026).
--
-- bank_payments   one row per payment from a customer's bank account (page /pay/bank): the bank, the LAST 4
--                 digits of the account, the gateway's transaction number, how many codes were sent and
--                 how it ended. The full account number and the one-time code are never stored.
-- payment_events  every step of an online payment as it happened (code sent, wrong code, paid, refused...),
--                 for answering customers and matching our records with the gateway's.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

create table bank_payments (
    amount decimal(12,2) not null,
    codes_sent integer not null,
    test_mode bit not null,
    wrong_codes integer not null,
    code_expires_at datetime(6),
    code_sent_at datetime(6),
    completed_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    order_id bigint not null,
    account_last4 varchar(4),
    bank_code varchar(12),
    status varchar(12) not null,
    intent_reference varchar(40) not null,
    bank_name varchar(80),
    gateway_transaction_id varchar(80),
    failure_reason varchar(300),
    primary key (id)
) engine=InnoDB;

create unique index idx_bankpay_intent on bank_payments (intent_reference);
create index idx_bankpay_status on bank_payments (status);

create table payment_events (
    at datetime(6) not null,
    id bigint not null auto_increment,
    result varchar(30),
    step varchar(30) not null,
    intent_reference varchar(40) not null,
    actor varchar(120),
    message varchar(300),
    primary key (id)
) engine=InnoDB;

create index idx_payevent_ref on payment_events (intent_reference, at);
