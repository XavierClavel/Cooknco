-- apply alter tables
alter table app_versions add column if not exists blocked_versions varchar[] default '{}' not null;
