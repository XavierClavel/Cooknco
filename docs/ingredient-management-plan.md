# Ingredient management overhaul — implementation plan

Branch: `improve-ingredient-units`

## Implementation status

Backend and `shared` are implemented and verified: the full `:backend:test` suite is green (117 tests),
and `scripts/verify-migrations.sh` exercises migrations 1.32–1.35 with legacy data on a throwaway
Postgres. Web frontend and Android app follow the same contract.

Migrations added, in order: **1.32** unify custom ingredients into `recipe_ingredients` + `sort_order`
+ the xor check constraint; **1.33** `unit` from enum ordinal to varchar; **1.34** nullable conversion
fields + backfill; **1.35** `default_unit`. Two sets of pending drops are queued and must ride later
releases: the `custom_ingredients` table (`pendingDropsFor 1.32`) and the
`allow_*` / `volumic_mass` / `weight_per_unit` columns (`pendingDropsFor 1.34`).

### Deviations from the plan as written below

- **Unit storage is varchar**, via JPA `@Enumerated(EnumType.STRING)` on `RecipeIngredient.unit` —
  no ebean annotation dependency in `shared` was needed after all. Migration 1.33 converts with an
  explicit ordinal→name `CASE`; the generator's default `using unit::varchar` would have written
  `'0'` instead of `'NONE'`.
- **Phase 6's new units (KILOGRAM, LITER, CENTILITER) were folded into phase 2.** Once units are
  stored by name, adding them costs nothing, and a separate migration would have been pointless.
  Phase 6's display auto-scaling is still separate, client-side work.
- **Phase 4's exception removal landed with phase 1**, since it rewrote the same lines of
  `RecipeController`. Beyond the plan: `createRecipe` now validates ingredient rows *before* inserting
  the recipe, so a rejected row no longer leaves an orphaned recipe behind.
- **The capability rule lives in `shared/utils/UnitCapabilities.kt`**, which both the `Ingredient`
  entity and `IngredientDTO` delegate to, so the entity and the validation path cannot drift.
- **`allowedTypes` replaced the three booleans on the wire** (`RecipeIngredientInfo`,
  `IngredientInfo`) rather than exposing the raw conversion fields to the recipe editor.
- Supporting changes: a `:backend:generateDbMigration` Gradle task (the generator was IDE-only
  before), `scripts/verify-migrations.sh`, and a testcontainers bump 1.20.1 → 1.21.4 because 1.20.1
  cannot talk to Docker Engine 29.

## Goals

Goals, in order:
1. Merge the parallel custom-ingredient system into `RecipeIngredient` and persist ingredient ordering (fixes the reorder-not-saved bug).
2. Make unit metadata (measurement family, conversion factor) a single source of truth served to both clients.
3. Replace the `allowAmount/allowWeight/allowVolume` booleans + always-set conversion floats with nullable conversion fields.
4. Validate units and amounts server-side; stop swallowing exceptions in `RecipeController`.
5. Pre-fill ingredient capabilities from `IngredientType`, add a per-ingredient `defaultUnit`.
6. Add missing units (kg, L, cL) and auto-scale display formatting.
7. Admin funnel: see most-used custom ingredient names, convert them into real ingredients.

Each phase is independently shippable, in this order. Phases 1–3 each carry a DB migration.

## Migration workflow (applies to phases 1, 2, 3, 5, 6)

- Edit the Ebean entities, then run `backend/src/test/kotlin/GenerateMigration.kt` to generate the next `backend/src/main/resources/dbmigration/1.NN.sql`.
- Append hand-written data-migration SQL to the generated file (precedent: `1.29__fuzzy_search_recipes.sql`, `1.11.sql`).
- Column/table drops go in a later deferred migration using the `pendingDropsFor` mechanism (precedent: `1.10__dropsFor_1.6.sql`; the property is commented in `GenerateMigration.kt:10`). Never drop in the same release that stops writing the old columns.
- Migrations run automatically at boot (`DatabaseManager.kt:70-74`).

---

## Phase 1 — Unified ingredient list + persisted ordering (size: L)

### Data model

`backend/.../models/jointables/RecipeIngredient.kt`:
- add `var customName: String? = null` (`@Column(length = 50)`, matching the frontend `max50` rule)
- add `var sortOrder: Int = 0` (`@DbDefault("0")`)
- `ingredient` stays `Ingredient?` — the DB column `ingredient_id` is already nullable (see `1.0__initial.sql`), so no nullability migration is needed.
- `toInfo(locale)` handles both shapes: `name = customName ?: translations lookup`, `id = ingredient?.id`.

`backend/.../models/Recipe.kt`:
- delete the `customIngredients` relation and its `toInfo` mapping
- in `toInfo`, emit `ingredients.sortedBy { it.sortOrder }`

Delete `backend/.../models/jointables/CustomIngredient.kt` (entity kept one release for the drop migration is NOT needed — Ebean drops via dropsFor from the model diff; delete now, table drop lands in the deferred migration).

### Migration SQL (hand-written part, appended to generated 1.NN.sql)

```sql
-- backfill order for existing rows (insertion order ≈ id order)
update recipe_ingredients ri
set sort_order = sub.rn
from (
  select id, row_number() over (partition by recipe_id order by id) - 1 as rn
  from recipe_ingredients
) sub
where ri.id = sub.id;

-- fold custom ingredients in, after the regular ones
insert into recipe_ingredients (recipe_id, custom_name, amount, unit, sort_order)
select ci.recipe_id, ci.name, ci.amount, ci.unit,
       coalesce((select max(ri.sort_order) + 1 from recipe_ingredients ri where ri.recipe_id = ci.recipe_id), 0)
         + row_number() over (partition by ci.recipe_id order by ci.id) - 1
from custom_ingredients ci;

-- exactly one of reference / free text
alter table recipe_ingredients
  add constraint ck_recipe_ingredients_ref_xor_custom
  check ((ingredient_id is null) <> (custom_name is null));
```

`custom_ingredients` table drop: next release, via dropsFor migration.

### DTO / wire format (shared module)

`shared/dto/RecipeDTO.kt`:
```kotlin
data class RecipeIngredientDTO(
    val id: Long? = null,          // reference to ingredients table
    val customName: String? = null, // free-text fallback; exactly one of id/customName is set
    val unit: AmountUnit = AmountUnit.NONE,
    val amount: Float? = null,
    val complement: String? = null,
)
```
Remove `customIngredients` + `CustomIngredientDTO`. **Compat decision: clean break** — backend, web, and app ship together (solo project, coordinated deploy). If old Android builds must keep working, keep `customIngredients: List<CustomIngredientDTO> = mutableListOf()` for one release and append its rows to the unified list server-side.

`shared/infodto/RecipeIngredientInfo.kt`: `id: Long?` (null ⇒ custom), keep `name` (resolved translation or customName). Delete `CustomIngredientInfo` and `RecipeInfo.customIngredients`.

### Backend services

Rewrite `RecipeIngredientService.updateRecipeIngredients` as **replace-all** (delete rows for the recipe, re-insert in DTO order with `sortOrder = index`, inside one transaction). This:
- removes the partition/`compareTo` diff logic and the by-ingredient-id keying (which breaks when the same ingredient appears twice)
- makes ordering persistence free
- is safe because `recipe_ingredients` rows are referenced by nothing else (id churn harmless)

Delete `CustomIngredientService.kt` (its name-keyed diffing — `CustomIngredientService.kt:31` — is the delete-and-recreate-on-rename bug). Remove both `customIngredientService` calls from `RecipeController` create/update. Remove `RecipeIngredient.compareTo` and `mergeDTO` if replace-all makes them dead.

### Web frontend

`pages/recipe/edit.vue`:
- one draggable list; drop the entire custom section (lines ~183–281), the add-custom button, and the `custom_ingredients_warning` tooltip
- free-text fallback in the existing `v-autocomplete`: when the search query matches nothing, append a synthetic item `{ id: null, name: query, custom: true }` rendered as "Use “query” as custom ingredient" (via the item slot); selecting it sets `customName`
- submit maps rows in list order: `{ id: item.ingredient?.id ?? null, customName: item.customName ?? null, unit, amount, complement }`
- custom rows show the full unit list (no ingredient capabilities to filter by)

`pages/recipe/view.vue`: single list (delete the second `v-list` at ~166); custom rows (`id == null`) get the generic image, no click-through link.

Locales `en.ts`/`fr.ts`: drop `custom_ingredients_warning`, `custom_ingredients`; keep `custom_ingredient` for the autocomplete fallback row label.

### Android app

`network/dto/NetworkDtos.kt`: `RecipeIngredientInfo.id: Long?` + drop `CustomIngredientInfo`/`CustomIngredientSaveDto`/`customIngredients` fields; `RecipeIngredientSaveDto` gains `customName: String?`, `id: Long?`.
`RecipeEditViewModel.kt`: merge `EditCustomIngredient` into the main edit row type (nullable `ingredientId`); delete the `addCustomIngredient`/`updateCustomIngredient*`/`removeCustomIngredient` family.
`RecipeEditScreen.kt`: single section; autocomplete sheet gets the same "use as custom" fallback row.

### Tests (backend/src/test/kotlin)

Update `RecipeControllerTest`, `ApplicationTest`, `RecipeHelpers` for the new DTO shape. Add:
- create/update with mixed referenced + custom rows round-trips names, units, order
- **reorder persists**: PUT with reversed ingredient list → GET returns reversed order (this is the bug fix)
- renaming a custom ingredient updates in place; two custom rows with the same name both survive
- same referenced ingredient twice in one recipe survives update

---

## Phase 2 — Unit metadata as single source of truth (size: M)

### Shared enum

New `shared/enums/MeasurementType.kt`: `NONE, AMOUNT, WEIGHT, VOLUME`.

`shared/enums/AmountUnit.kt`:
```kotlin
enum class AmountUnit(val type: MeasurementType, val factorToBase: Float) {
    NONE(MeasurementType.NONE, 0f),
    UNIT(MeasurementType.AMOUNT, 1f),
    GRAM(MeasurementType.WEIGHT, 1f),          // base: grams
    POUND(MeasurementType.WEIGHT, 453.6f),
    MILLILITERS(MeasurementType.VOLUME, 1f),   // base: mL
    TEASPOON(MeasurementType.VOLUME, 5f),
    TABLESPOON(MeasurementType.VOLUME, 15f),
    CUP(MeasurementType.VOLUME, 240f),
}
```

**DB storage**: `unit` is currently an ordinal integer with a check constraint (`1.0__initial.sql`, widened in `1.11.sql`). Recommended: switch to string storage now, so enum order never matters again — add `io.ebean:ebean-annotation` (annotations only) to `shared` and put `@DbEnumValue` on a property returning `name`; migration:
```sql
alter table recipe_ingredients drop constraint if exists ck_recipe_ingredients_unit;
alter table recipe_ingredients alter column unit type varchar(31)
  using (case unit when 0 then 'NONE' when 1 then 'UNIT' when 2 then 'GRAM' when 3 then 'POUND'
              when 4 then 'MILLILITERS' when 5 then 'TEASPOON' when 6 then 'TABLESPOON' when 7 then 'CUP' end);
```
Low-effort alternative if the ebean dep in `shared` is unwanted: keep ordinal storage with a strict **append-only** rule (comment on the enum) and a check-constraint bump per addition, as `1.11.sql` already did.

### Endpoint

New `UnitController` (`GET /units`, public, registered in `config/appModules.kt` routing next to the others) returning `AmountUnit.entries.filter { it != NONE }.map { UnitInfo(name, type, factorToBase) }` — include NONE too, clients want it in the picker. `UnitInfo` in `shared/infodto`. Response is static → set Cache-Control.

### Web frontend

- new `scripts/units.ts`: fetch `/units` once (module-level promise cache), expose `unitOptions` built from the response; icons stay client-side keyed by `MeasurementType` (`NONE→ICON_NONE, AMOUNT→ICON_AMOUNT, WEIGHT→ICON_WEIGHT, VOLUME→ICON_VOLUME`), i18n labels keyed by unit name lowercased (existing keys already match: `gram`, `pound`, …)
- delete the hardcoded `unitOptions` in `scripts/values.ts:67`
- `getUnitOptions(ingredient)` in `recipe/edit.vue` filters by `type` from server data (unchanged logic, no duplicated family mapping)

### Android app

- `RecipeApi`: fetch + cache `/units` (repository-level, refresh per app start, hardcoded fallback for offline)
- delete `allUnits` and the family knowledge in `unitsForIngredient` (`RecipeEditScreen.kt:83-99`); keep only display labels

### Tests

- enum invariants: every unit except NONE has `factorToBase > 0`; every unit has an i18n label in both locales (frontend can't test Kotlin — cover label completeness with a small vitest against a checked-in copy of the endpoint response, or skip)
- controller test for `/units`

---

## Phase 3 — Nullable conversion fields replace allow* booleans (size: M)

### Data model

`Ingredient.kt`: remove `allowAmount`, `allowWeight`, `allowVolume`, `volumicMass`, `weightPerUnit`. Add:
```kotlin
var gramsPerUnit: Float? = null        // non-null ⇒ countable ⇒ UNIT allowed
var gramsPerMilliliter: Float? = null  // non-null ⇒ volume units allowed
@DbDefault("true")
var measurableByWeight: Boolean = true // false for "to taste" style ingredients
```
Helper: `fun allowedTypes(): Set<MeasurementType>` = `{NONE}` ∪ `{AMOUNT if gramsPerUnit != null}` ∪ `{WEIGHT if measurableByWeight}` ∪ `{VOLUME if gramsPerMilliliter != null}`.

Migration backfill (before deferred drop of old columns):
```sql
update ingredients set
  grams_per_unit       = case when allow_amount then weight_per_unit end,
  grams_per_milliliter = case when allow_volume then volumic_mass end,
  measurable_by_weight = allow_weight;
```
Note: rows that had `allowVolume=true` with the untouched default `volumicMass=1.0` were already showing fabricated conversions; the migration preserves them as `1.0`. Optional data-quality pass afterwards: `select` ingredients where `grams_per_milliliter = 1.0` or `grams_per_unit = 1.0` and review in the admin panel.

### Wire format

- `IngredientDTO` / `IngredientInfo`: swap the five fields for the three new ones.
- `RecipeIngredientInfo`: replace the three `allow*` booleans with `allowedTypes: Set<MeasurementType>` computed server-side — clients stop re-deriving capability logic.
- `RecipeIngredient.toInfo` uses `ingredient.allowedTypes()`; custom rows get all types.

### Web frontend

- `pages/ingredient/list.vue` admin panel: replace three checkboxes + two always-on number inputs with: a weight switch, a clearable "grams per unit" input (empty = not countable), a clearable "density (g/mL)" input (empty = no volume). Nullability = capability, so invalid states are unrepresentable.
- `components/IngredientNutritionalData.vue`: per-unit / per-volume columns render only when the corresponding conversion is non-null (fixes fabricated values from the 1.0 defaults); computations use `gramsPerUnit` / `gramsPerMilliliter`.
- `recipe/edit.vue` `getUnitOptions`: filter `unitOptions` by `ingredient.allowedTypes`.

### Android app

`NetworkDtos.kt` `IngredientSummary`/`RecipeIngredientInfo`: `allowedTypes: List<String>`; `unitsForIngredient` becomes a pure filter of the fetched unit list.

### Tests

- migration correctness is covered implicitly by existing fixtures failing loudly; add controller tests for create/update ingredient with the new fields and for `allowedTypes` in recipe responses.

---

## Phase 4 — Server-side validation + honest error responses (size: S)

1. **Stop swallowing exceptions**: remove the `try/catch(e) { logger.error }` wrappers in `RecipeController.createRecipe` (:82) and `updateRecipe` (:98) — today a failed save logs and never responds. `Application.kt:67-80` StatusPages already maps typed exceptions to statuses.
2. New `BadRequestCause` entries in `exceptions/Exceptions.kt`: `INVALID_INGREDIENT_ROW` (both/neither of id & customName), `UNIT_NOT_ALLOWED_FOR_INGREDIENT`, `INVALID_AMOUNT`, `CUSTOM_NAME_TOO_LONG`.
3. In the rewritten `RecipeIngredientService.updateRecipeIngredients`, per row:
   - exactly one of `id` / `customName` set; `customName` non-blank, ≤ 50 chars
   - referenced rows: `dto.unit.type in ingredient.allowedTypes()` else 400
   - `unit == NONE ⇒ amount == null`; `unit != NONE ⇒ amount != null && amount > 0` (reject, don't coerce — the web client already conforms via `recipe/edit.vue:494`)
4. `IngredientService.createIngredient/updateIngredient`: reject non-null `gramsPerUnit`/`gramsPerMilliliter` ≤ 0.
5. Tests: one 400 test per rule; regression test that a validation failure on update leaves the stored recipe unchanged (transaction rollback).

---

## Phase 5 — Defaults from type + per-ingredient default unit (size: S)

1. **Admin pre-fill** (client-side only): in `ingredient/list.vue`, watching the type select pre-fills capabilities for new ingredients — e.g. liquids/beverages/oil → density 1.0 + weight; fruit/vegetable → gramsPerUnit placeholder + weight; condiment → density + weight. Table lives in `scripts/values.ts` next to `ingredientTypes`. Backend stays dumb.
2. **`defaultUnit: AmountUnit?`** column on `Ingredient` (+ DTO/Info + admin select filtered to allowed units + migration; backfill null). Validate server-side: `defaultUnit.type in allowedTypes()`.
3. Recipe editors preselect it: web `recipe/edit.vue` on autocomplete selection; app `RecipeEditViewModel` on ingredient pick. Fallback when null: WEIGHT→GRAM if allowed, else first allowed unit, else NONE.

---

## Phase 6 — More units + display auto-scaling (size: S)

1. Add `KILOGRAM(WEIGHT, 1000f)`, `LITER(VOLUME, 1000f)`, `CENTILITER(VOLUME, 10f)`. With string storage (phase 2) this is enum + locales only; with ordinal storage, append at the end + check-constraint migration (the `1.11.sql` dance).
2. Locales: `kilogram`/`liter`/`centiliter` labels in `en.ts`/`fr.ts`; `unitToReadable` in `scripts/common.ts:290` gains `kg`, `L`, `cL`; same for the app's label map.
3. Display auto-scaling helper `formatAmount(amount, unit)` (web + app): after the yield coefficient is applied in `recipe/view.vue:136`, scale to the readable sibling unit (≥1000 g → kg, ≥1000 mL → L, ≥10 mL → cL only for round values). Pure display; stored values untouched.

---

## Phase 7 — Custom → real ingredient funnel (size: M)

1. **Usage endpoint** (admin-gated in `IngredientController`): `GET /ingredient/custom-usage?page=&size=` →
   ```sql
   select lower(unaccent(custom_name)) as key, min(custom_name) as display, count(*) as uses
   from recipe_ingredients where custom_name is not null
   group by 1 order by uses desc
   ```
   (unaccent already installed — `1.30__unaccent_import.sql`).
2. **Convert endpoint**: `POST /ingredient/{id}/absorb-custom` body `{ "name": "..." }`, admin-gated, single transaction:
   ```sql
   update recipe_ingredients set ingredient_id = :id, custom_name = null
   where lower(unaccent(custom_name)) = lower(unaccent(:name));
   ```
   Recipes that used the custom name gain nutrition data retroactively. Guard: chosen unit types that the new ingredient doesn't allow — keep the rows as-is (historical data), the phase-4 validation only applies to new writes.
3. **Admin UI** in `pages/ingredient/list.vue`: a "Custom ingredients in use" table (name, count, convert button). Convert opens the existing create panel pre-filled with the name in both locales; on save, call absorb with the created id. If a matching real ingredient already exists, allow picking it instead of creating.
4. Tests: usage aggregation (case/accents collapse), absorb re-points rows and clears `custom_name`, absorb is admin-only.

---

## Rollout & sequencing

- One PR per phase; phases 1–3 each bump the Ebean migration version, deferred drops ride the following phase's release.
- Backend + web deploy together (clean-break DTO changes in phases 1 and 3). Android app releases alongside; if store-review lag matters, activate the `customIngredients` compat shim noted in phase 1 for one release.
- Run each migration against a copy of the production DB first (compose stack) and diff row counts: `custom_ingredients` count must equal rows inserted into `recipe_ingredients` with `custom_name is not null`.
- Out of scope, unlocked by this work: recipe-level nutrition aggregation, metric/imperial user preference, shopping-list aggregation, recipe scaling beyond the existing yield coefficient.
