#!/bin/bash
# Applies the pre-change migrations, seeds realistic legacy data, then applies 1.34-1.37 and checks
# the data migration did what it claims. Tests build the schema from the entity model, so this is
# the only thing that exercises the migration SQL.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
MIG="$REPO/backend/src/main/resources/dbmigration"
CT=mig_verify_pg
PORT=6733

cleanup() { docker rm -f $CT >/dev/null 2>&1 || true; }
cleanup

docker run -d --name $CT -e POSTGRES_PASSWORD=test -e POSTGRES_DB=verify -p $PORT:5432 postgres:17.2 >/dev/null
# Waited for inside the container, not out here. The host's sleep is not always available -
# some shells this is run from block it - and without it the loop spins through its sixty
# turns in milliseconds and psql then talks to a server that has not finished starting. The
# error it gives blames a missing socket, which reads like Docker is broken rather than like
# a race.
if ! docker exec $CT bash -c 'for i in $(seq 1 60); do pg_isready -U postgres -d verify >/dev/null 2>&1 && exit 0; sleep 1; done; exit 1'; then
  echo "postgres did not come up"; exit 1
fi

psql() { docker exec -i $CT psql -v ON_ERROR_STOP=1 -U postgres -d verify "$@"; }

psql -q -c "CREATE EXTENSION IF NOT EXISTS unaccent; CREATE EXTENSION IF NOT EXISTS pg_trgm;"

# Migrations in version order, split at the first one we authored.
ALL=($(ls "$MIG" | grep '\.sql$' | sort -t. -k2 -n))
OLD=(); NEW=()
for f in "${ALL[@]}"; do
  v=$(echo "$f" | sed -E 's/^1\.([0-9]+).*/\1/')
  if [ "$v" -ge 34 ]; then NEW+=("$f"); else OLD+=("$f"); fi
done

echo "== applying ${#OLD[@]} pre-change migrations =="
for f in "${OLD[@]}"; do psql -q -f "/mig/$f" 2>/dev/null || docker exec -i $CT psql -v ON_ERROR_STOP=1 -U postgres -d verify < "$MIG/$f" >/dev/null; done

echo "== seeding legacy data =="
psql -q <<'SQL'
insert into dietary_restrictions (id) values (1);
insert into users (id, role, token_end_validity, join_date, last_activity_date,
                   dietary_restrictions_id, username, bio, token)
values (1, 0, now(), now(), now(), 1, 'cook', '', 'tok');

-- Two ingredients exercising the old capability flags.
insert into ingredients (id, type, calories, cholesterol, fibers, proteins, sodium,
                         allow_amount, allow_weight, allow_volume, volumic_mass, weight_per_unit)
values
  (1, 0, 52, 0, 2, 0.3, 0.001, true,  true,  false, 1.0,  182),  -- countable apple
  (2, 0, 42, 0, 0, 3.4, 0.05,  false, true,  true,  1.03, 1.0);  -- milk, volume only

insert into recipes (id, dish_class, creation_date, modification_date, owner_id, title, description)
values
  (1, 0, now(), now(), 1, 'apple cake', ''),
  (2, 0, now(), now(), 1, 'plain',      '');

-- Legacy units are enum ordinals: 2=GRAM, 1=UNIT, 4=MILLILITERS, 6=TABLESPOON, 0=NONE.
insert into recipe_ingredients (id, recipe_id, ingredient_id, amount, unit, complement) values
  (1, 1, 1, 3,   1, 'peeled'),
  (2, 1, 2, 200, 4, null),
  (3, 1, 1, 250, 2, 'for the crumble'),
  (4, 2, 1, null, 0, null);

insert into custom_ingredients (id, recipe_id, amount, unit, name) values
  (1, 1, 2,  6, 'yuzu zest'),
  (2, 1, 30, 2, 'candied ginger'),
  (3, 2, null, 0, '');          -- blank name: must not be carried over

-- Steps, as the old element collection held them: a recipes_id and a value, and no column
-- saying which comes first. 1.48 has to reconstruct the order from physical row order, so
-- these go in one INSERT at a time -- a single multi-row VALUES would also work, but one
-- statement per row is the only way to be unambiguous about what order they were written in.
--
-- Deliberately neither alphabetical nor grouped by recipe: alphabetical would be
-- 'Bake,Melt,Rest' for recipe 1, so a backfill that sorted by text instead of position would
-- pass unnoticed, and interleaving the two recipes is what proves the partitioning.
insert into recipes_steps (recipes_id, value) values (1, 'Melt the butter');
insert into recipes_steps (recipes_id, value) values (2, 'Boil water');
insert into recipes_steps (recipes_id, value) values (1, 'Rest the dough 30 mn');
insert into recipes_steps (recipes_id, value) values (2, 'Add salt');
insert into recipes_steps (recipes_id, value) values (1, 'Bake each side');

select setval(pg_get_serial_sequence('recipe_ingredients','id'), 100);
SQL

echo "== applying ${#NEW[@]} new migrations: ${NEW[*]} =="
for f in "${NEW[@]}"; do
  echo "-- $f"
  docker exec -i $CT psql -v ON_ERROR_STOP=1 -U postgres -d verify < "$MIG/$f" >/dev/null
done

echo
echo "== recipe_ingredients after migration =="
psql -c "select recipe_id, ingredient_id, custom_name, amount, unit, complement, sort_order
         from recipe_ingredients order by recipe_id, sort_order;"

echo "== recipe_steps after migration =="
psql -c "select recipe_id, sort_order, text, duration_seconds from recipe_steps order by recipe_id, sort_order;"

echo "== ingredients after migration =="
psql -c "select id, grams_per_unit, grams_per_milliliter, measurable_by_weight, default_unit from ingredients order by id;"

echo "== assertions =="
psql -tA <<'SQL'
\set ON_ERROR_STOP on
do $$
declare n int; s text;
begin
  -- units converted from ordinals to names
  select string_agg(unit, ',' order by id) into s from recipe_ingredients where id <= 4;
  if s <> 'UNIT,MILLILITERS,GRAM,NONE' then raise exception 'unit conversion wrong: %', s; end if;

  -- the two named custom ingredients folded in, the blank one dropped
  select count(*) into n from recipe_ingredients where custom_name is not null;
  if n <> 2 then raise exception 'expected 2 custom rows, got %', n; end if;

  -- recipe 1: 3 referenced rows keep order 0..2, customs appended at 3..4
  select string_agg(coalesce(custom_name, 'ref'), ',' order by sort_order) into s
    from recipe_ingredients where recipe_id = 1;
  if s <> 'ref,ref,ref,yuzu zest,candied ginger' then raise exception 'ordering wrong: %', s; end if;

  -- no duplicate sort_order within a recipe
  select count(*) into n from (
    select recipe_id, sort_order from recipe_ingredients group by 1,2 having count(*) > 1
  ) d;
  if n <> 0 then raise exception 'duplicate sort_order in % recipes', n; end if;

  -- capabilities derived from the old flags
  select count(*) into n from ingredients
   where id = 1 and grams_per_unit = 182 and grams_per_milliliter is null and measurable_by_weight;
  if n <> 1 then raise exception 'apple conversions not backfilled'; end if;
  select count(*) into n from ingredients
   where id = 2 and grams_per_unit is null and grams_per_milliliter = 1.03 and measurable_by_weight;
  if n <> 1 then raise exception 'milk conversions not backfilled'; end if;

  -- 1.48: the steps became a table of their own, and kept the order they were written in.
  select string_agg(text, '|' order by sort_order) into s from recipe_steps where recipe_id = 1;
  if s <> 'Melt the butter|Rest the dough 30 mn|Bake each side' then
    raise exception 'step order lost for recipe 1: %', s;
  end if;
  select string_agg(text, '|' order by sort_order) into s from recipe_steps where recipe_id = 2;
  if s <> 'Boil water|Add salt' then raise exception 'step order lost for recipe 2: %', s; end if;

  -- every step carried over, none invented
  select count(*) into n from recipe_steps;
  if n <> 5 then raise exception 'expected 5 steps, got %', n; end if;

  -- sort_order is per recipe, zero-based and gapless
  select string_agg(sort_order::text, ',' order by sort_order) into s from recipe_steps where recipe_id = 1;
  if s <> '0,1,2' then raise exception 'recipe 1 sort_order is %', s; end if;
  select string_agg(sort_order::text, ',' order by sort_order) into s from recipe_steps where recipe_id = 2;
  if s <> '0,1' then raise exception 'recipe 2 sort_order is %', s; end if;

  -- the column did not exist before 1.48, so nothing may arrive with a timer already set
  select count(*) into n from recipe_steps where duration_seconds is not null;
  if n <> 0 then raise exception '% migrated steps came out with a duration', n; end if;

  -- the old table is left standing on purpose: until its drop migration, this is undoable
  select count(*) into n from recipes_steps;
  if n <> 5 then raise exception 'the old steps were destroyed: % rows left', n; end if;

  raise notice 'ALL ASSERTIONS PASSED';
end $$;
SQL

echo "== constraints present on recipe_ingredients =="
psql -c "select conname, pg_get_constraintdef(oid) from pg_constraint where conrelid = 'recipe_ingredients'::regclass;"

# psql exits non-zero on a rejected insert, so capture the message instead of piping (pipefail).
expect_rejected() {
  local label="$1" sql="$2" out
  out=$(docker exec -i $CT psql -U postgres -d verify -c "$sql" 2>&1 || true)
  case "$out" in
    *ck_recipe_ingredients_ref_xor_custom*) echo "OK: $label rejected" ;;
    *) echo "FAIL: $label was accepted"; echo "$out"; exit 1 ;;
  esac
}

echo "== xor constraint =="
expect_rejected "row that is both referenced and custom" \
  "insert into recipe_ingredients (recipe_id, ingredient_id, custom_name, amount, unit, sort_order)
   values (1, 1, 'both', 1, 'GRAM', 9);"
expect_rejected "row that is neither" \
  "insert into recipe_ingredients (recipe_id, amount, unit, sort_order) values (1, 1, 'GRAM', 9);"

echo
echo "ALL MIGRATION CHECKS PASSED"
docker rm -f $CT >/dev/null 2>&1
