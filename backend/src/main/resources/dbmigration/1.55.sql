-- apply alter tables
alter table ingredients alter column calories drop not null;
alter table ingredients alter column cholesterol drop not null;
alter table ingredients alter column saturated_fat drop default;
alter table ingredients alter column saturated_fat drop not null;
alter table ingredients alter column unsaturated_fat drop default;
alter table ingredients alter column unsaturated_fat drop not null;
alter table ingredients alter column carbohydrates drop default;
alter table ingredients alter column carbohydrates drop not null;
alter table ingredients alter column sugars drop default;
alter table ingredients alter column sugars drop not null;
alter table ingredients alter column fibers drop not null;
alter table ingredients alter column proteins drop not null;
alter table ingredients add column if not exists salt float;
alter table ingredients add column if not exists ciqual_code integer;
alter table ingredients add column if not exists ciqual_edition integer;

-- Data migration: `sodium` is read as salt from here on, which is what the French label always
-- called it (and what Ciqual states, in g/100 g). The values are carried over as they stand.
-- The column is retired the way this schema retires one -- a new column, a copy, and the drop
-- left to a later pendingDropsFor(1.55) migration -- so until then this is reversible.
update ingredients set salt = sodium;

-- Nothing writes `sodium` any more, and it was created `not null` with no default, so every
-- insert would fail on it until it is dropped.
alter table ingredients alter column sodium drop not null;

-- The nutrition columns are nullable now, null meaning unknown. The zeros already there stay:
-- nothing tells a 0 that was typed from one that was the old default.

-- A Ciqual code without its edition is a citation that cannot name its year.
alter table ingredients add constraint ck_ingredients_ciqual_source
  check ((ciqual_code is null) = (ciqual_edition is null));
