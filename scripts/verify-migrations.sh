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
for i in $(seq 1 60); do
  docker exec $CT pg_isready -U postgres -d verify >/dev/null 2>&1 && break
  sleep 1
done

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
