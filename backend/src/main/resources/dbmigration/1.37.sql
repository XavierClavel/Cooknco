-- apply alter tables
alter table ingredients add column if not exists default_unit varchar(11);
-- apply post alter
alter table ingredients add constraint ck_ingredients_default_unit check ( default_unit in ('NONE','UNIT','GRAM','KILOGRAM','POUND','MILLILITERS','CENTILITER','LITER','TEASPOON','TABLESPOON','CUP'));
