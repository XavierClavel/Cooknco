-- apply alter tables
alter table ingredients add column if not exists grams_per_unit float;
alter table ingredients add column if not exists grams_per_milliliter float;
alter table ingredients add column if not exists measurable_by_weight boolean default true not null;

-- Data migration: a non-null conversion now *is* the capability, replacing the allow_* booleans.
-- A conversion of 0 would make the per-unit and per-volume nutrition columns divide by zero, so it
-- is treated as "unknown" rather than carried over.
-- Ingredients whose conversion is exactly 1.0 kept the old column default and were never given a
-- real value; they show up as `grams_per_unit = 1 or grams_per_milliliter = 1` and are worth an
-- admin review pass.
update ingredients set
  grams_per_unit       = case when allow_amount then nullif(weight_per_unit, 0) end,
  grams_per_milliliter = case when allow_volume then nullif(volumic_mass, 0) end,
  measurable_by_weight = allow_weight;

-- The allow_* / volumic_mass / weight_per_unit columns are dropped by a later
-- pendingDropsFor(1.36) migration, once no deployed backend writes them any more.
