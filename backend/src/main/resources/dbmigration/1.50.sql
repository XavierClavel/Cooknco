-- Which units an account reads amounts in. Not nullable, unlike `users.locale`: nothing
-- reports a ladder the way a client reports its language, so there is no report to keep
-- apart from a choice. 'METRIC' for everyone because that is what every recipe was written
-- in while there was nothing to choose, so no account's recipes change on deploy.
--
-- The two check constraints below are rebuilt only because `AmountUnit` gained the ounce
-- and the fluid ounce -- imperial has no unit under a pound otherwise, and a 20 g amount
-- would have nowhere to land. No existing row names either, so nothing is rewritten.

-- drop dependencies
alter table ingredients drop constraint if exists ck_ingredients_default_unit;
alter table recipe_ingredients drop constraint if exists ck_recipe_ingredients_unit;
-- apply alter tables
alter table users add column if not exists unit_system varchar(8) default 'METRIC' not null;
-- apply post alter
alter table ingredients add constraint ck_ingredients_default_unit check ( default_unit in ('NONE','UNIT','GRAM','KILOGRAM','OUNCE','POUND','MILLILITERS','CENTILITER','LITER','FLUID_OUNCE','TEASPOON','TABLESPOON','CUP'));
alter table recipe_ingredients add constraint ck_recipe_ingredients_unit check ( unit in ('NONE','UNIT','GRAM','KILOGRAM','OUNCE','POUND','MILLILITERS','CENTILITER','LITER','FLUID_OUNCE','TEASPOON','TABLESPOON','CUP'));
alter table users add constraint ck_users_unit_system check ( unit_system in ('METRIC','IMPERIAL'));
