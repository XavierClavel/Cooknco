-- drop dependencies
alter table recipe_ingredients drop constraint if exists ck_recipe_ingredients_unit;
-- apply alter tables
-- Units used to be stored as the enum ordinal, which froze the declaration order of AmountUnit.
-- The mapping below is the ordinal order as of migration 1.11, the last one to widen the old
-- check constraint: 0=NONE 1=UNIT 2=GRAM 3=POUND 4=MILLILITERS 5=TEASPOON 6=TABLESPOON 7=CUP.
alter table recipe_ingredients alter column unit type varchar(11) using (
  case unit
    when 0 then 'NONE'
    when 1 then 'UNIT'
    when 2 then 'GRAM'
    when 3 then 'POUND'
    when 4 then 'MILLILITERS'
    when 5 then 'TEASPOON'
    when 6 then 'TABLESPOON'
    when 7 then 'CUP'
    else 'NONE'
  end
);
-- apply post alter
alter table recipe_ingredients add constraint ck_recipe_ingredients_unit check ( unit in ('NONE','UNIT','GRAM','KILOGRAM','POUND','MILLILITERS','CENTILITER','LITER','TEASPOON','TABLESPOON','CUP'));
