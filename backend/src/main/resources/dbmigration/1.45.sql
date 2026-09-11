-- apply alter tables
alter table users add column if not exists mail_notifications_enabled boolean default false not null;
