-- drop dependencies
alter table if exists custom_ingredients drop constraint if exists fk_custom_ingredients_recipe_id;
-- apply alter tables
alter table recipe_ingredients add column if not exists custom_name varchar(50);
alter table recipe_ingredients add column if not exists sort_order integer default 0 not null;

-- Data migration: fold custom_ingredients into recipe_ingredients.
-- The custom_ingredients table itself is dropped by a later pendingDropsFor(1.34) migration.

-- Backfill ordering. No order was ever stored, so insertion order is the best approximation.
update recipe_ingredients ri
set sort_order = sub.rn
from (
  select id, row_number() over (partition by recipe_id order by id) - 1 as rn
  from recipe_ingredients
) sub
where ri.id = sub.id;

-- Rows with neither an ingredient reference nor a name cannot be rendered (the previous code
-- dereferenced ingredient_id unconditionally), so any that exist are already broken.
delete from recipe_ingredients where ingredient_id is null and custom_name is null;

-- Custom ingredients land after the referenced ones, keeping their relative order.
insert into recipe_ingredients (recipe_id, custom_name, amount, unit, sort_order)
select
  ci.recipe_id,
  left(ci.name, 50),
  ci.amount,
  ci.unit,
  coalesce((select max(ri.sort_order) + 1 from recipe_ingredients ri where ri.recipe_id = ci.recipe_id), 0)
    + row_number() over (partition by ci.recipe_id order by ci.id) - 1
from custom_ingredients ci
where ci.recipe_id is not null
  and ci.name <> '';

alter table recipe_ingredients
  add constraint ck_recipe_ingredients_ref_xor_custom
  check ((ingredient_id is null) <> (custom_name is null));
