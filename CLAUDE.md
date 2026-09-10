# Cooknco

Kotlin monolith (`backend`) + Vue web app (`frontend`) + `mail-service`, sharing `shared`.
A Kotlin Multiplatform mobile app lives in `app/` with its own Gradle build. One third-party
service rides along: `cooknco-gotenberg`, headless Chromium, which prints the PDF exports.
Deployed to Kubernetes from `k8s/` by `.github/workflows/build.yml` on every push to
`master`.

## Always bump the version when a feature is done

Every finished feature or fix bumps the release version **in the same branch as the change**,
before the PR is merged. Two files carry it and they must stay in sync:

- `build.gradle.kts` — `version = "x.y.z"` in the `allprojects` block (line 2). This is the
  source of truth: CI reads it via `./gradlew -q printVersion` and tags the three Docker
  images with it.
- `frontend/package.json` — `"version"`.

Default level: patch for fixes, minor for features. Never reuse a version that has already
reached `master` — CI pushes an immutable image tag per version.

The mobile app's `versionCode` / `versionName` in `app/androidApp/build.gradle.kts` are a
separate, store-facing pair. Leave them alone unless the task is a store build.

## Git and PRs

- No AI attribution anywhere in git history. Do not add a `Co-Authored-By: Claude ...`
  trailer, and do not put "Generated with Claude Code", "written by Claude", or any similar
  footer or note in commit messages or PR bodies. Commits and PRs are authored by
  Xavier alone.
- PR bodies describe the change and why, nothing else.

## All queries go through query beans

**Every database query MUST be written with the generated query beans (`Q<Entity>`)** from
`com.xavierclavel.models.query` — produced by `io.ebean:querybean-generator` (kapt, wired in the root
`build.gradle.kts`). They give compile-checked property paths, so a renamed field breaks the build
instead of failing at runtime.

No untyped `DB.find(X::class.java)`, no raw SQL where a query bean can express the same thing, and no
magic strings for property paths. Typed paths traverse associations, so filtering on a related entity
needs no join string:

```kotlin
QRecipe().owner.id.`in`(ownerIds).findList()
```

`DB` stays the entry point for saves, deletes and transactions — it is only *querying* that must go
through query beans. A bulk update is reached from the query bean too, so its predicates stay typed:
`QX().where().<typed predicates>.asUpdate().set(QX.Alias.field, value).update()` (set the FK id, not
the entity: `set(QRecipeIngredient.Alias.ingredient.id, ingredient.id)` — Ebean has no ScalarType for
an entity and fails at bind time otherwise).

### Use the typed overloads, not the string ones

`select`, `fetch` and `orderBy` all have typed forms. Both the path and the column list of the string
versions rot silently on a rename:

```kotlin
// Don't                                    // Do
.select("id").fetch("owner", "id")          .select(QRecipe.Alias.id).owner.fetch(QUser.Alias.id)
.fetch("users", FetchConfig.ofLazy())       .users.fetchLazy()
.orderBy("title desc")                      .orderBy().title.desc()
```

Subqueries take a nested query bean — `.id.isIn(QOther()....query())`, `.exists(query)` — never the
`inSubQuery(sql)` / `eqSubQuery(sql)` family, which parses its argument as raw SQL.

Where no typed overload exists (an aggregate column expression such as
`fetch(path, "count(*)", ofLazy())`, an aggregate in `having()`, or a SQL function in `orderBy()`),
keep the string API but derive every path from `Alias`:
`fetch(QRecipe.Alias.likes.toString(), "count(*)", FetchConfig.ofLazy())`,
`orderBy("count(${QRecipe.Alias.likes.id}) desc")`.

`orderBy` is the one place a *value* cannot be bound — Ebean copies the string into the SQL verbatim,
with no placeholders. A search term ordered on must therefore go through `sqlStringLiteral()`, which
emits it as a hex-decoded literal that cannot carry SQL (`RecipeService.sort`, `IngredientService.search`).

### `raw()` and raw SQL — last resort, never with hardcoded columns

`raw()` inside a query bean is allowed only for what Ebean cannot express: PostgreSQL functions and
operators (`word_similarity`, `unaccent`, `random()`), aggregate predicates in `having()`, and
**correlated** `EXISTS` subqueries — a typed `-to-many` predicate joins the collection, which
double-counts against the `count(*)` like-aggregate in `RecipeService.findList`, so the filters there
are `EXISTS` by necessity (see the commented-out typed lines next to them).

When you write one, **reference every column through `Q<Entity>.Alias.<path>` — never a hardcoded
column name or a `t0.` alias.** Interpolating an `Alias` path yields the logical property path, which
Ebean resolves to the right column and table alias, so a rename becomes a compile error instead of a
runtime SQL failure. Bind *values* with `?` placeholders; only identifiers may be interpolated, and
only from `Alias`.

```kotlin
// Don't — hardcoded Ebean table alias
.raw("... AND t0.owner_id = f.user_id", followerId)
// Do — alias-derived paths, values bound (RecipeService.filterByFollowed, filterBySearch)
.raw("... AND ${QRecipe.Alias.owner.id} = f.user_id", followerId)
.raw("word_similarity(unaccent(?), unaccent(${QRecipe.Alias.title})) > 0.3", search)
```

`DB.sqlQuery` / `DB.sqlUpdate` / `DB.findDto` is reserved for queries that return no entities at all —
dashboard aggregates, `/admin` reporting counts, storage reconciliation. A new query that returns
entities does not qualify. Values must still be bound (`:name` / `?`), but note that `Alias` paths do
**not** belong there: Ebean only translates property paths inside a query bean, so plain SQL names the
real columns (`custom_name`, not `customName`).

### Fetch joins and N+1

Fetch the associations the mapping code reads, up front, instead of letting them lazy-load per row.
A `findList()` honours only a **single `-to-many` fetch path**: every other collection is silently
dropped from the plan and lazy-loads one query per row (`findOne` is unaffected). When a list DTO
needs several collections, query each from the child side and group it back by parent id.

Nothing enforces this automatically here — unlike `backend-zourite-api`, which fails the build on it
via custom detekt rules. `backend/src/main` and `mail-service/src/main` currently hold no violations,
so any string property path or `DB.sqlQuery` you find in a diff is new and should be sent back. Test
sources are out of scope: fixtures may set up rows with raw SQL.

Reference: [`Pictarine/backend-zourite-api` — `docs/product-v2/ebean-patterns.md`](https://github.com/Pictarine/backend-zourite-api/blob/develop/docs/product-v2/ebean-patterns.md)
(that project's Kotlin query beans call the alias `_alias`; ours are Java beans and use `Q<Entity>.Alias`).

## PDF exports are HTML, printed by Chromium

The recipe sheet is **not** laid out in Kotlin. `ExportService` fills a Mustache layout in
and posts the resulting HTML to Gotenberg, which prints it (`GotenbergPdfRenderer`). iText
is a test-only dependency now — it reads exports back so tests can assert on them — and
nothing should go back to building a PDF box by box.

The layout itself is operator-owned, on the same design as the mail wordings: a saved row in
`pdf_templates` overrides the copy packaged in `backend/src/main/resources/pdf/`, restoring
one is a delete, and the backoffice edits them in the documents tab. What a layout may name
is `PdfDocumentKind.RECIPE.variables` and nothing else — adding a value to a sheet means
adding it there and in `ExportService.modelOf`, not just writing it into the HTML.

Two constraints the layouts have to respect, both enforced by the deployment rather than by
review:

- **No network.** Gotenberg runs with `--chromium-allow-list=^file:///tmp/.*` (plus the two
  `deny-*-ips` flags), so a URL in a layout fetches nothing — remote, a neighbouring service,
  or a file off the renderer's disk. Fonts and pictures are posted alongside the document as
  named files (`assets` in `PdfRenderer.render`) and referenced by that name.
  Do **not** fall back to Gotenberg's stock deny-list: it is `^file:(?!//\/tmp/).*`, and Go's
  RE2 cannot compile a negative lookahead, so it is matched by a backtracking engine under a
  timeout that fires under concurrent prints and fails the conversion with a 500.
- **Values are escaped.** Mustache's `{{name}}` escapes HTML, which is what keeps a recipe
  title from being markup. Do not reach for the raw `{{{name}}}` form on anything a user typed.

`GotenbergPdfRenderer` bounds how many prints are in flight (`Configuration.Pdf.maxConcurrentRenders`,
matching the renderer's own `--chromium-max-concurrency`) and refuses with a 503 rather than
queueing for ever. Keep that bound: the backoffice paces its live preview, but nothing else
calling the endpoint does.

`:backend:test` starts a real Gotenberg container (`GotenbergTestContainer`) rather than
stubbing the renderer, so the export tests assert on PDFs Chromium actually produced. The
bound itself is tested against a mock engine instead (`GotenbergPdfRendererTest`), because a
real renderer answers too fast to hold two prints open at once.

## The mobile version gate fails open, on purpose

`app_versions` holds at most one row per `AppPlatform`, and **a platform with no row is not
gated at all**. That is the floor the whole feature is built on: a fresh install, a restored
backup or a wiped table lets every build run rather than locking every build out, because a
blocked app has no other screen to reach and nothing on the phone can undo it. Every other
uncertainty resolves the same way — a version string we cannot parse, a failed request, a
verdict a build predates, a platform the backend does not know — all answer `OK`
(`AppVersionService.check`, `AppVersionRepository.refresh`).

Two consequences worth keeping:

- **The backend never enforces the block.** It answers `GET /api/v1/app-version` and the
  client stops itself. Do not add a 426 on the other endpoints: it would break the sign-in
  and the update prompt along with everything else, and still not stop a client that
  ignored it.
- **A minimum above the latest version is refused, not warned about** — it blocks everyone
  including the users who did update, and the only way back is the backoffice screen that
  just accepted it. `1.10` vs `1.9` is one typo away at all times, which is also why
  versions are compared component-wise by `shared.utils.AppVersions` and never as strings.

The store link is part of the gate rather than optional: a build that stops with nowhere to
go is one nobody can fix. The block screen's copy lives in the app, not in the database —
unlike the mail and PDF wordings — because the app has no locale to render server copy in
(`ApiClient.LOCALE` is a constant) and a wrong-language block screen is worse than none.

### What a floor costs is measured, not guessed

`devices.app_version` is what each client reported at its last launch, and
`AppVersionService.reach` counts it against a *candidate* floor so the backoffice can price
a save before making it. Three things about those figures:

- **They are scoped to a 90-day window.** Nothing prunes `devices` on a timer — only a token
  FCM rejects is removed — so without one, handsets replaced years ago would make every
  floor look more expensive than it is.
- **`blockedUsers` is "people with at least one stale device"**, not "people locked out": a
  stale phone and a current tablet is one of each.
- **A device reporting no version is counted apart and blocked by nothing**, matching the
  gate. Empty is what every build that predates the column sends, which is why
  `DeviceRegistrationDTO.appVersion` is defaulted on the server side and *not* defaulted in
  the app's copy — kotlinx omits defaults when encoding, so a default there would be a field
  the backend never sees (the same trap `devicePlatform` documents).

Only devices registered for push are visible, so reach is a floor on the real number.

## Build and test

Use `sh ./gradlew` (the wrapper lacks the exec bit in worktrees) with JDK 23 — Gradle 8.10.2
fails on JDK 25:

```bash
export JAVA_HOME=/Users/xavierclavel/Library/Java/JavaVirtualMachines/temurin-23.0.2/Contents/Home
sh ./gradlew :backend:test        # needs Docker: ebean-test starts a Postgres container
sh ./gradlew build
```

Tests build the schema from the entity model (`ddlMode: dropCreate`), so they never execute
`backend/src/main/resources/dbmigration`. After changing entities or writing a migration, run
`scripts/verify-migrations.sh` to exercise the SQL and backfills against a throwaway Postgres.

## Frontend

```bash
cd frontend && npm run build      # use this to verify changes
npx eslint <files>                # lint your own files only
```

**Never run `npm run lint`** — it is `eslint . --fix` over a codebase the config was never
enforced on, and rewrites ~75 unrelated files. If the formatting is ever to be applied, that
is its own commit.
