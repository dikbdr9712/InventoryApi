-- =====================================================================================
-- DK/Phar Inventory: database version 5 - journal numbers (4 Oct 2026).
--
-- orders.payment_reference         the journal number (bank transfer / mobile banking), approval code (card) or
--                                  transaction number (UPI) of a counter sale, typed by the cashier. Printed on
--                                  the receipt, searchable in Sales history, listed in the shift report.
-- bank_payments.bank_reference     the journal (authorisation) number the customer's bank gives for a payment
--                                  from a bank account: what shows on the bank statement.
--
-- Flyway applies this by itself on the next start. Nothing has to be loaded.
-- =====================================================================================

alter table orders add column payment_reference varchar(80);
create index idx_order_payment_ref on orders (payment_reference);

alter table bank_payments add column bank_reference varchar(80);
