-- apply alter tables
alter table recipes add column if not exists revision bigint default 0 not null;
