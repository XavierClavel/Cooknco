-- apply alter tables
alter table recipes add column if not exists deleted boolean default false not null;
