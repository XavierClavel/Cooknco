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

## The account's language is the backend's, and a client only ever reports

`users.locale` is what the product writes *to* a user in — the mails `mail-service` sends and
the notifications they are pushed. It is nullable, and null means "nothing has ever told us".
That distinction is the whole feature: the column shipped non-null with a default of FR and
no writer at all, so every account read as French and every mail this product sent went out
in French whoever received it. Migration `1.44` drops the default and nulls the rows, because
none of those values was ever a choice.

The rule underneath everything is that **a report never corrects a value already there**:

- **Clients report, they do not decide.** Signing up, signing in and registering a device all
  carry `?locale=`, and an account with none adopts it (`UserService.adoptLocale`). An
  account that has one keeps it — a borrowed laptop, a second-hand phone or a
  holiday-language handset is not a decision the user made.
- **The user decides, in the settings.** `UserSettingsDTO.locale` is nullable *on the way in*
  too, and null there means "not saying": a client that predates the field saves the other
  settings without wiping a language chosen from one that does know about it.
- **Unknown falls back, it does not guess.** Notifications fall back to the most recent
  device's language and then to `Locale.FR`; `mail-service` falls back to `Locale.FR`
  directly. FR because that is what the column held for everybody, so nothing changes
  language until something actually reports one. A locale that will not parse is treated as
  absent rather than refused — a signup is worth more than a preference
  (`AuthController.reportedLocale`).

Two consequences worth keeping:

- **Resolution happens here, not in `mail-service`.** That service reads the column and has
  no devices or requests to infer anything from, which is why the backend fills the column in
  eagerly on adoption rather than resolving lazily at read time.
- **The announcement audience filter resolves the language the same way the wording does**
  (`NotificationService.recipientsOf`). They have to agree, or an operator selects an audience
  by one rule and has it written by another — an account reading EN whose last handset
  reported FR would land in the French send and receive an English notification.

`devices.locale` still describes the handset, and is what the backoffice shows and what an
audience falls back to. The Google sign-in is the one flow where the language cannot be read
off the request that needs it: Google builds the callback itself, so `?locale=` is captured
when the flow leaves (`onStateCreated`) and consumed when the state comes back.

The app reports `deviceLocale` — the real platform language — rather than the constant it
used to send. `ApiClient.LOCALE` still exists and is still `EN`, because what the app asks
for *content* in is a different question from what the backend writes to its user in: the
app's own copy is English-only.

## The MCP endpoint holds the SDK back on purpose

`POST /mcp` serves Model Context Protocol from the backend (`McpController`, tools in
`mcp/CookncoMcpServer.kt`). Three things about it are load-bearing:

- **`io.modelcontextprotocol:kotlin-sdk-server` stays at 0.10.0**, the last release built against
  this build's Ktor 3.2.3 and Kotlin 2.2.21. Later releases compile here and then fail at runtime
  reaching for Ktor internals that moved — 0.15.0 throws `tried to access private field
  io.ktor.http.HttpMethod.Post` on the first request. Upgrading the SDK means upgrading Ktor
  across the build in the same change, and `McpControllerTest` is what catches it either way:
  it drives a real handshake and real tool calls over HTTP rather than asserting on types.
- **`ContentNegotiation` lives on the routing root**, not on the application (`configureSerialization`).
  MCP needs its own JSON settings (`explicitNulls = false`, `encodeDefaults = true`, no class
  discriminator) or JSON-RPC replies come out with an explicit `"error": null`, and Ktor refuses a
  route-scoped install whose key is already installed application-wide. Moving it back to the
  application silently breaks `/mcp`.
- **A tool goes through the services, and repeats the checks the controller makes.** `RecipeController`
  and friends enforce some rights themselves (`checkRecipeEditionRights`, cookbook membership)
  rather than in the service, so a tool that skips them is a hole. Where a tool deliberately
  differs from its REST counterpart, the reason is a comment at the call site — `like_recipe` only
  purges a recipe that is actually tagged for deletion, because `RecipeService.tryDelete` does not
  check the tag itself.

Tools are advertised with `ToolAnnotations`: reads carry `readOnlyHint`, `delete_recipe` carries
`destructiveHint`. Clients decide what to confirm from those, so a new tool needs them set.

## The backend is an OAuth 2.1 server, for `/mcp` only

`OAuthService` and `OAuthController` exist so that adding the MCP endpoint to a client is a URL
and a browser login rather than a token pasted into a config file. What a client does — read
`/.well-known/oauth-protected-resource/mcp`, follow it to `/.well-known/oauth-authorization-server`,
register itself, send the user to `/oauth/authorize`, exchange the code at `/oauth/token` — is
[the MCP authorization spec](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization),
and the parts of it that are MUSTs are the parts not to relax:

- **The 401 from `/mcp` carries `WWW-Authenticate: Bearer resource_metadata="…"`.** That header
  is the entire discovery chain; without it a client cannot find the login and the endpoint is
  back to needing a hand-pasted token. `McpController` resolves its own bearer token for this
  reason — Ktor's `bearer` provider sends a challenge of its own making.
- **Tokens are audience-bound.** Every token records the resource it was issued for and
  `/mcp` accepts only its own (`OAuthService.tokenFor`), which is what stops a token minted for
  another service being replayed here.
- **PKCE S256, and redirect URIs matched exactly.** A public client has no secret, so the
  verifier is all that ties a code to the process that asked for it. An unregistered redirect
  URI is refused *on the page*, never redirected to — redirecting to it is the attack.
- **Refresh tokens rotate.** OAuth 2.1 requires it of public clients: each refresh destroys the
  token presented, so a stolen one is worth one use.
- **Registration ignores the metadata it does not know.** RFC 7591 asks for that, and every
  real client sends some (`logo_uri`, `software_id`, `application_type`…). `/oauth/register`
  therefore reads the body as text and parses it with its own lenient `Json`, not through
  `call.receive`: the strict application-wide converter threw on the first unknown key, and
  every client got `400 invalid_client_metadata` before a field had been looked at.

Codes and tokens live in Redis as opaque strings, next to the sessions. That is deliberate: the
store enforces expiry, revocation is a delete, and — unlike a JWT — no signing key has to be
added to `cooknco-config`. Registered clients live in Postgres instead, because a client keeps
its `client_id` on disk indefinitely and `invalid_client` is not an error it can recover from.

The consent and error pages are Mustache templates in `backend/src/main/resources/oauth/`,
rendered by `OAuthPages`. Unlike the mail wordings and document layouts they are *not*
operator-editable: the sentence naming the client and where its code will be sent is a security
control, not copy. The client name on that page is attacker-controlled, which is why it is
escaped and printed next to the redirect URI it registered.

Signing in is delegated to the app's login page (`?redirect=` in `frontend/src/scripts/common.ts`,
same-origin paths only), so the flow keeps Google sign-in and there is still one page in the
product that asks for a password.

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
