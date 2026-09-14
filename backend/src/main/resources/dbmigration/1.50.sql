-- apply alter tables
alter table recipe_steps add column if not exists image_version bigint default 0 not null;
