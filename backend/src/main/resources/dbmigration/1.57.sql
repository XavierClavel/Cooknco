-- apply alter tables
alter table users add column if not exists apple_id varchar(255);
-- Written by hand: the generator records the constraint in the model but leaves it out of the
-- SQL. Every existing row is null, which a unique constraint allows any number of.
alter table users drop constraint if exists uq_users_apple_id;
alter table users add constraint uq_users_apple_id unique (apple_id);
