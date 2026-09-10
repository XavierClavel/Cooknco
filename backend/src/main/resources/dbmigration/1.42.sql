-- apply alter tables
alter table devices add column if not exists app_version varchar(23) default '' not null;
