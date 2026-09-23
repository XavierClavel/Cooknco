# Offline recipe access — implementation plan

Branch: `offline-recipe-access`

## Implementation status

**Phases 0–6 are implemented** — the whole of offline *reading* — at version `1.35.0`.
`:composeApp:testAndroidHostTest` is green (166 tests, 36 of them new),
`:composeApp:compileKotlinIosSimulatorArm64` compiles, and `:backend:test` covers the one
change outside `app/`.

**Nothing offline is writable yet.** Notes, new recipes and edits are all still
server-only — so the notes work that [Offline editing](#offline-editing) folds into Phase 4 is
*not* done either, only the reading of notes already synced. That section stands as written,
and each of its three parts is its own release.

### Deviations from the plan as written below

- **The cookbook set is one query, not one per book.** `GET /recipe?cookbookUser={me}`
  (`RecipeFilter.cookbookUser`) already returns every recipe in every cookbook the account
  belongs to. `GET /cookbook/{id}/recipes` is still called per cookbook, but only for the rows
  the cookbook *screen* lists — it answers `CookbookRecipeInfo`, which carries no
  `editionDate` and so cannot be diffed on.
- **`RecipeInfo.toOverview()` in `shared` had to carry `editionDate` too**, and its field is
  nullable there while the overview's is not — so it maps through `?: 0`. Seven backend tests
  caught this by comparing a mapped overview against the API's; without it, every client
  reading a list built that way would have seen `editionDate = 0` and diffed on nothing.
- **The banner sits above the bottom bar**, not under the status bar. Each tab applies its own
  top inset (they render standalone through other routes), so a banner up there is drawn behind
  one of them.
- **`OfflineLists` was added** and is not in the plan below: the profile's two grids and the
  cookbooks tab need overviews in the server's order, and rebuilding those from the pinned
  recipe files would put them back in whatever order the directory happened to hold.

## Goal

A cook standing in a kitchen with no signal opens the app and can **read and cook** the
recipes that are theirs: the ones they wrote, the ones they liked, and the ones in the
cookbooks they belong to. Nothing else about being offline is in scope.

"Read and cook" means, exactly: the app opens signed in; the profile's two grids and the
cookbooks tab list what they listed last time; opening a recipe shows its picture,
ingredients, amounts, steps and notes; cook mode walks it with its timers.

### Non-goals

- **Editing a recipe that already exists, offline.** Reading is this plan; writing is
  [its own section](#offline-editing) below, which concludes that notes and new recipes are
  worth doing and that editing an existing recipe is not — at least not as a replayed queue.
- **The feed and the search.** Both are *discovery* — they are about recipes the cook does
  not have. A feed served from a week-old cache is lying about what is new.
- **Background sync.** Syncing happens while the app is open. WorkManager and
  BGTaskScheduler are two platform implementations of a thing that is only worth having once
  the foreground one is proven.
- **The PDF and Cooklang exports.** Both are rendered on the server.

## What already works offline, and is not touched

Worth knowing, because three of the harder-looking pieces are already done:

- **Amounts convert.** `AppUnits.catalog` is seeded from `UnitRepository.DEFAULT_UNITS`,
  a complete packaged copy, and the account's ladder is cached in `DevicePreferences`.
- **The app's own words.** `ui/i18n/Strings.kt` is compiled in, and the resolved language is
  cached in `DevicePreferences` too.
- **The cook timer and the cook session.** `CookTimer`/`CookSession` already persist the
  whole recipe being cooked to disk and are driven from a broadcast receiver with no network
  — the design note on `CookSessionState` is the doctrine this plan extends.
- **The version gate.** `AppVersionRepository` answers `OK` to everything it cannot reach.

## The five things in the way

### 1. An offline launch signs the user out

`AuthRepository.getCurrentUser` (`data/AuthRepository.kt:50`) calls `whoami` and clears the
token on **any** failure. A dropped connection is a failure. So a launch with no network
deletes the session and lands on a login screen that, offline, can never be passed — and the
user is then signed out for good until they find signal, having also lost `AccountSettings`.

Everything else in this plan is moot until this is fixed, and fixing it is worth shipping on
its own.

### 2. There is no local store

`RecipeRepository` and `CookbookRepository` are thin wrappers over Ktor. Nothing is written
down but the token, the device preferences, the cook timer and the cook session — all four in
one DataStore Preferences file.

### 3. The pictures live in a directory the OS may empty

No `ImageLoader` is configured, so Coil uses its singleton default, whose disk cache is
`FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "coil3_disk_cache"` — `cacheDir` on Android,
`NSTemporaryDirectory()` on iOS. Both are purgeable by the system, which is the one property
a picture we promised to have offline must not have. Nothing prefetches, either: an image is
only ever on disk because a screen already drew it.

Two things are in our favour: image URLs are fully derivable from `(id, version)` and carry
the version in the filename, and nginx serves `/image/` as `immutable, max-age=6 months`
(`frontend/nginx.conf:86`). Coil's `CacheStrategy.DEFAULT` "always returns the disk cache
response", so a file that is there is served without asking anybody.

### 4. `version` is not a change token

`RecipeOverview.version` and `RecipeInfo.version` are `Recipe.imageVersion` — they move when
the *picture* changes and not when the recipe does (`models/Recipe.kt:135`). `RecipeInfo`
carries `editionDate`, but the app's copy of the DTO does not declare it and `RecipeOverview`
does not have it at all. So from a list of recipes there is today no way to tell which of them
changed, and a sync would have to refetch every recipe in full, every time.

### 5. Every view model treats a failed request as an error to print

Which is right today and wrong the moment there is something to fall back to.

## The rules this is built on

Four, and they are what the phases below are arranging:

1. **The network is the truth when it answers; the store is the answer when it does not.**
   Never the other way round, and never both — a screen showing a merge of the two is a screen
   nobody can reason about.
2. **A store fallback is only ever silent for content we pinned.** Falling back for the feed
   or a search would present a week-old list as today's. Falling back for a recipe the cook
   pinned is the whole feature.
3. **Anything that will not parse reads as absent**, the `CookTimerStore` rule: a cook whose
   offline copy is silently missing goes and looks at their phone's signal; a cook whose app
   crashes on launch has nothing to look at.
4. **Offline is inferred from transport failures, not asked of the platform.** One helper
   distinguishes "the server said no" from "there was no server", which is the same
   distinction rule 1 above needs and the same one the sign-out bug turns on. A
   `NWPathMonitor`/`ConnectivityManager` pair is two platform shims for an answer we already
   have, and it is the *request* failing that matters, not what the radio claims.

---

## Phase 0 — an offline launch stays signed in (app, size: S)

Shippable alone, and the highest value per line in the plan.

**`network/Offline.kt`** (commonMain):

```kotlin
/**
 * Whether this failure means "there was no server" rather than "the server said no".
 *
 * An [ApiException] is the server answering, whatever it answered. Anything else got out of
 * Ktor without a status — a DNS failure, a refused socket, a timeout — and is the only kind
 * of failure a cached answer may stand in for.
 */
val Throwable.isOffline: Boolean get() = this !is ApiException
```

**`data/SessionStore.kt`**: the last `UserInfo` this device saw, as JSON in the offline root
(Phase 2) — id, username, role and `isPremium`, which is all `MainScreen` and `AppNavigation`
read off it.

**`AuthRepository.getCurrentUser`** becomes:

- token present, `whoami` succeeds → store the `UserInfo`, return it.
- `whoami` fails with an `ApiException` → the session really is dead: clear the token, clear
  the store, return null. This is the case the current code is written for.
- `whoami` fails otherwise → return the stored `UserInfo` if there is one, and do **not**
  touch the token. No stored user (a first launch that never reached the server) → null, as
  today.

`AppNavigation` needs no change: it reads `AuthState.Authenticated`.

One thing to be explicit about: a cached `isPremium` decides what is *offered*, and the
`403` from `UserService.checkPremiumAccess` still decides what is allowed — which is the
existing rule that the gate reads the row and never the session. A grant that lapsed while the
phone was offline shows the export in the sheet and fails at the route. Both premium features
need the server anyway, so the window is one refused tap wide.

**Also in this phase:** `AuthRepository.logout` wipes the offline root. It is another
account's recipes on a handset somebody else may pick up — the reason `AccountSettings.forget`
exists, applied to a much bigger pile of data.

---

## Phase 1 — a change token on the overview (backend + shared + app, size: S)

The one change outside `app/`. `Recipe.toOverview` already has the value:

```kotlin
// shared/src/main/kotlin/shared/overviewdto/RecipeOverview.kt
val editionDate: Long = 0,   // additive, defaulted: no client breaks
```

```kotlin
// backend .../models/Recipe.kt, in toOverview()
editionDate = this.modificationDate.toEpochSecond(ZoneOffset.UTC),
```

And on the app's side of the wire, `editionDate` on both `RecipeOverview` and `RecipeInfo` in
`network/dto/NetworkDtos.kt`, defaulted to `0`.

**Why it is worth touching the backend for.** Without it a sync has exactly two shapes: refetch
every pinned recipe in full on every sync (a cook with 200 liked recipes pays 200 requests to
discover that none changed), or never refresh what it holds. With it, a sync lists the three
sets — paged, 20 overviews a request, which the app already does — diffs against the index,
and fetches only what is new or moved. For most syncs that is zero requests beyond the lists.

`modificationDate` is set by `Recipe.mergeDTO` on every update. It does **not** move when only
a picture changes — that is what `imageVersion` is for, and the index carries both.

Per `CLAUDE.md` this change runs `build.yml`, so it carries the version bump for the whole
feature. Frontend TypeScript reads the same DTO and ignores a field it does not use.

---

## Phase 2 — the local store (app, size: M)

**Not DataStore.** `AppGraph.preferences` is one Preferences file that every read of the token
deserialises whole, and every write rewrites whole. Recipes do not belong in it.

**Not SQLDelight or Room.** A few hundred recipes that are always read whole, by id, do not
need a query engine — and the cost is a KMP database dependency, a schema, a migration story
and iOS build wiring, for a lookup a filename already does.

**Okio, which is already a dependency**, one file per recipe:

```
<offlineRoot>/
  index.json               OfflineIndex — what is pinned and what state we hold it in
  session.json             the last UserInfo (Phase 0)
  recipes/<id>.json        OfflineRecipe: RecipeInfo + notes + isLiked + cookbook ids
  cookbooks/<id>.json      CookbookInfo + the ids of its recipes, in order
  images/<filename>.webp   exactly the last path segment of the image URL
```

`platform/OfflineRoot.kt` — `expect fun offlineRoot(): Path`:

- **Android**: `filesDir/offline`, captured at startup by `AppGraph.initFor`, the way
  `captureAppVersion(context)` already is.
- **iOS**: `NSApplicationSupportDirectory`, with `NSURLIsExcludedFromBackupKey` set — this is
  re-downloadable data and has no business in anyone's iCloud backup.

Neither is a cache directory, which is the point of Phase 0's finding 3.

**`data/OfflineStore.kt`** wraps it: `readRecipe(id)`, `writeRecipe`, `deleteRecipe`, the same
for cookbooks, and `readIndex`/`writeIndex`. Every write goes to `<name>.tmp` and then
`atomicMove` — a half-written recipe is otherwise a recipe that will not parse *sometimes*.
Every read is `runCatching { json.decodeFromString(...) }.getOrNull()`: rule 3.

**`OfflineIndex`** — one entry per pinned recipe: `id`, `editionDate`, `imageVersion`, and
which of the three sets put it there (a recipe can be in all three; it leaves the store when
it is in none). Plus the cookbook ids and `syncedAt`, which is what the banner in Phase 6
prints.

---

## Phase 3 — the sync (app, size: L)

**`data/OfflineSync.kt`**, with the diff as a pure function so it can be tested without a
network or a filesystem:

```kotlin
/** What a sync has to do, worked out from two lists and nothing else. */
data class SyncPlan(
    val fetch: List<Long>,      // new, or editionDate moved
    val refetchImages: List<Long>,  // imageVersion moved, recipe unchanged
    val drop: List<Long>,       // no longer in any of the three sets
)

fun planSync(remote: List<RecipeOverview>, local: OfflineIndex): SyncPlan
```

**What it walks**, in this order, so the cheapest and most important finishes first:

1. `GET /recipe?user={me}` — paged to the end. These are the cook's own.
2. `GET /cookbook?user={me}`, then `GET /cookbook/{id}/recipes` for each.
3. `GET /recipe?likedBy={me}` — paged, up to the bound below.

Then `planSync`, then `GET /recipe/{id}` for each id in `fetch`, then the pictures (Phase 5),
then the index is written — **last**, so a sync interrupted halfway leaves an index that
under-claims rather than one that names files that are not there.

`getNotes(id)` and `isLiked(id)` ride along with each fetched recipe: the recipe screen draws
all three and a screen that has the recipe but not the notes is a screen with an empty
text box where the cook's own writing was.

**A recipe that 404s or 403s is dropped**, not kept — it was deleted, hidden by a moderator,
or the cookbook was left. The same rule the export follows: what the caller may read is what
they get.

**When it runs:** after a successful `whoami` at launch; after sign-in; on the profile tab's
pull-to-refresh; and after this cook creates, updates or deletes a recipe (a write-through, so
the phone does not have to be told about a change it made itself). One at a time — a `Mutex`
in `OfflineSync` — and never on the main dispatcher.

**A sync never surfaces an error.** It is best-effort by construction: what it could not fetch
is simply not pinned, and the index says so.

### The bound, and the decision behind it

Own recipes and cookbook recipes are bounded by what the cook made and joined. **Liked
recipes are not** — liking is one tap, and a few hundred is an ordinary number.

Recommendation: pin the **200 most recent** of each set, with a settings switch
("Keep my recipes on this phone", default on) that turns the whole thing off and empties the
store. 200 recipes is roughly 2–4 MB of JSON and, at thumbnail-only, 10–20 MB of pictures.
See [Decisions](#decisions-needed) — this is Xavier's call, and it is the one number in this
plan that a user will notice.

---

## Phase 4 — reading through the store (app, size: M)

The repositories gain the fallback; **no view model changes shape.**

```kotlin
// RecipeRepository
suspend fun getRecipe(id: Long): Result<RecipeInfo> = runCatching {
    val token = tokenDataStore.tokenFlow.first()
    recipeApi.getRecipe(id, token).also { store.writeRecipe(id, it) }
}.recoverCatching { error ->
    // Rule 2: only for something we pinned, and only when there was no server.
    if (error.isOffline) store.readRecipe(id)?.recipe ?: throw error else throw error
}
```

The same for `getNotes`, `isLiked`, `getCookbook`, `getCookbookRecipes`, `listCookbooks`, and
`UserRepository.getUserRecipes` (both tabs — the grids are the index's own lists, in its
order). `HomeViewModel` and `RecipesViewModel` are deliberately left alone: the feed and the
search stay as they are, and show their error.

Writes — `addLike`, `saveNotes`, `createRecipe`, `updateRecipe`, `deleteRecipe`,
`uploadRecipeImage` — get no fallback at all. They fail, and Phase 6 makes the failure read as
"no connection" rather than as a mystery.

---

## Phase 5 — the pictures (app, size: M)

**`data/OfflineImages.kt`**, and one rule: a pinned picture is a file we own, not a cache
entry we hope survives.

- The sync downloads each pinned recipe's thumbnail and hero image, and its steps' pictures
  where `imageVersion > 0`, straight through `ApiClient.httpClient` into
  `<offlineRoot>/images/<last-path-segment>`. The filename carries the version, so a changed
  picture is a different file and the old one is deleted with the index entry that named it.
- `OfflineImages.resolve(url: String): Any` returns the local `okio.Path` when the file is
  there and the `url` otherwise. Coil registers `PathMapper` in its common components, so an
  `okio.Path` is a valid model on both platforms with no new dependency and no second image
  pipeline.
- `RecipeImage`, `StepImage`, `CookbookImage` and `UserAvatar` each change one line:
  `model = OfflineImages.resolve(url)`.

That is four lines and a helper, and in exchange "offline" stops depending on whether the OS
felt like emptying a temp directory.

**Also set the singleton `ImageLoader` explicitly** (`setSingletonImageLoaderFactory` in
`App.kt`) with a disk cache under the offline root and a stated size, so everything *not*
pinned — avatars, the feed's thumbnails — is at least cached somewhere we chose. Leaving it
implicit means the cache's location and size are Coil's defaults changing under us on upgrade.

---

## Phase 6 — saying so on screen (app, size: S)

**`data/OfflineState.kt`**: one `StateFlow<Boolean>`, set by the repositories — false on any
successful request, true on any `isOffline` failure. Rule 4: the request failing is the
signal, and there is no platform shim.

- **A bar under the top bar** when it is true: "No connection — showing what is saved on this
  phone", with the index's `syncedAt` ("saved 2 hours ago"). It must name the date: content of
  unstated age is worse than an error, because nothing tells the cook their recipe is the
  version before the fix they made last night.
- **Write actions are shown, and say why they are unavailable** — the rule
  `premiumSheetAction` and `PremiumLockDialog` already establish: hiding the edit button
  leaves the cook wondering where it went. The like button is the exception worth watching: it
  is optimistic and reverts on failure, which offline reads as the tap having done nothing.
  It should refuse up front, like the rest.
- **A recipe that was not pinned**, opened offline (from a deep link, a notification, or the
  feed still on screen) shows "This recipe is not saved on this phone" and not a network
  error. That is a different fact and the cook can act on it.
- **Settings**: the switch from Phase 3, the pinned count, what it takes on disk, and
  "Sync now".

---

## Tests

All in `app/composeApp/src/commonTest` — there is no device on this machine, so everything
worth testing is arranged to be reachable from the JVM.

| Test | What it holds |
|---|---|
| `OfflineSignInTest` | `getCurrentUser` **keeps** the token on a transport failure and returns the cached user; **clears** it on a 401. The regression that matters most. |
| `SyncPlanTest` | `planSync` over fixtures: new, edited (`editionDate` moved), repictured (`imageVersion` moved), unchanged, unliked, deleted. Pure, no I/O. |
| `OfflineStoreTest` | Round trip; a truncated file reads as absent; a write that fails leaves the previous version intact. |
| `OfflineFallbackTest` | `MockEngine` throwing `IOException` → the store's recipe; answering `404` → the failure, and the recipe dropped. |
| `OfflineImagesTest` | `resolve` returns the `Path` when present and the URL when not. |

On the backend side, Phase 1 needs one assertion in the existing recipe-list test that
`editionDate` is on the overview and moves with an update.

The two platform `offlineRoot()` actuals are compile-checked only — the same position
`RecipeScanner`'s shims are in.

---

## Decisions taken

1. **Automatic**, not opt-in: the three sets are kept offline without the cook asking, because
   a feature you have to know about before you need it is one nobody has turned on when the
   signal drops. A settings switch turns the whole thing off and empties the store.
2. **200 most recent per set.** Own and cookbook recipes rarely reach it; liked recipes are the
   only set with no natural ceiling, and one tap is all it takes to add to it.
3. **Thumbnails and hero for every pinned recipe; step pictures only for the cook's own
   recipes and their cookbooks'.** A step picture is a cooking instruction, and those are the
   recipes they will actually stand over a pan with.

## Sequencing

Phase 0 ships first and alone — it is a bug fix with a test, and it is worth having whatever
happens to the rest. Phase 1 next, since it is the only thing outside `app/` and it carries the
version bump. Phases 2–5 are one piece of work; Phase 6 can trail it by a release, though an
app that silently shows saved content with nothing on screen to say so is worse than one that
does not, so it should not trail by much.

---

# Offline editing

Three things get called "offline editing", and they differ by one question: **who else could
have changed this while the phone was away?** That question, and not the amount of code,
is what decides whether each is worth doing.

| | Who else can touch it | Conflict | Verdict |
|---|---|---|---|
| Personal notes | Nobody — one row per (user, recipe) | None possible | **Do it**, with Phase 4 |
| A new recipe | Nothing exists yet | None possible | **Do it**, as Phase 7 |
| An existing recipe | The web, another handset, a cookbook co-owner | Silent data loss | **Not as a queue** — Phase 8 keeps the typing instead |

## Notes, offline (fold into Phase 4, size: S)

Notes are the strongest case in the whole feature and the cheapest. They are what a cook
writes *while cooking* — "halve the sugar", "35 min not 25 in our oven" — which is precisely
when they are standing in a kitchen, in a cellar, or in a holiday house with no bars. And
`recipe_notes` is one row per (user, recipe): nobody else can write to it, so last-write-wins
is the correct answer rather than a compromise.

The store already holds the notes (the sync fetches them with each recipe). Editing offline
adds: a `dirty` flag on the stored notes, a write-through on save, and a replay on the next
sync. One wrinkle, and it is the whole implementation risk: `RecipeApi.saveNotes` picks POST or
PUT from an `isCreate` flag the *client* decides, and offline the client cannot know whether
the row exists. So a replay tries the one it believes in and falls back on the other when the
server disagrees (`409`/`404`) — or, better, `PUT /recipe-notes/{id}` upserts and the flag goes
away. The second is one backend line and removes a class of bug rather than handling it.

## A new recipe, offline (Phase 7, size: M)

There is a hole here today that is worth naming on its own: **the scanner works with no
network and the save does not.** `RecipeScanner` reads a printed recipe entirely on the
device — no API key, no upload, explicitly "it works with no network" — fills the editor in,
and then Save fails. The one screen in the app built to work offline cannot keep its result.

Nothing can conflict with a recipe that does not exist yet, so this is a queue of exactly one
verb. A recipe created offline gets a local id, lives in `<offlineRoot>/drafts/`, and is
`POST`ed on the next sync, after which the local id is swapped for the server's everywhere it
was referenced.

Three things it inherits rather than invents:

- **Free-text ingredients.** The catalogue cannot be searched offline, so an offline row stays
  `customName` — which is exactly what the scan and the Cooklang import already do when the
  catalogue does not obviously hold a row ("a working ingredient rather than a wrong one").
  The rule exists; this is a third caller of it.
- **The picture waits for the id.** `RecipeEditViewModel.save` already uploads the image
  *after* the create, because that is when the id exists. Offline, the bytes go to
  `<offlineRoot>/drafts/` and the same two-phase order runs at sync.
- **Followers are notified on sync, not on writing.** Correct, and the opposite of why an
  import does not save: there, the owner has not read the recipe yet; here, they wrote it.

## An existing recipe, offline (Phase 8 — and not as a queue)

This is the one to push back on, and the reason is in the backend rather than in the app.

**`PUT /recipe/{id}` is last-write-wins over the whole recipe, with no version check.**
`updateRecipe` merges the DTO and calls `saveSteps`, which **deletes every step the client
stopped mentioning**. So an edit queued offline on Monday and replayed on Thursday does not
produce a conflict the cook gets to resolve — it silently destroys whatever was done from the
web in between, rows and all. Two more sharp edges under it:

- **Ingredient rows are positional.** `RecipeStepIngredientInfo.index` points into
  `RecipeInfo.ingredients` by position, and `replaceRecipeIngredients` replaces the list
  wholesale. A stale copy's positions mean something different against the current recipe.
- **A step deleted elsewhere comes back.** `saveSteps` handles an *unknown* id by inserting
  (deliberately — the comment says so), which is right for a stale client and is exactly how a
  step somebody deleted on the web returns from the dead.

The same hazard is not limited to recipes. A **queued unlike** is replayed against the recipe
as it is *now*, and `LikeController.deleteLike` purges a recipe whose owner has tagged it for
deletion — so an unlike queued before that tag existed deletes the recipe on sync. Nothing is
wrong with that check; it is what a replay does to a rule written for a live request.

Making a queue safe means conflict detection: send the `editionDate` the edit was based on,
have the server answer `412` when it has moved, and build the cook a merge screen. That is a
feature in its own right — a screen, a set of rules, its own tests — and it is a lot of
machinery for a case that is rare in a recipe app, where a cook editing their own recipe from
two places at once is not the common story.

### What to do instead: keep the typing, save it when there is signal

The thing that actually hurts is losing twenty minutes of typing, not the write failing. So:

- An edit made offline is kept as a **draft on the device**, against the recipe id, and is
  **not queued**. The editor reopens on it.
- When there is signal, the cook is told they have unsaved changes to that recipe, and taps
  Save. It is an ordinary online save, made by somebody who is present and looking at it.
- Before that save goes out, compare the `editionDate` the draft was based on against the
  recipe's now — **which Phase 1 already puts on the overview**, so the check is free. If it
  moved, say so ("This recipe changed since you edited it offline") and let the cook look
  before overwriting.

That is a fraction of the machinery, it never overwrites anybody silently, and the one thing a
queue would have bought — not having to tap Save — is not worth what it costs here.

## Sequencing, revised

Notes ride with Phase 4. Phase 7 (new recipes) is worth doing for the scanner alone and can
follow a release later. Phase 8 is the draft-and-warn shape above; if a queue is ever wanted
instead, it starts with the `412` on `PUT /recipe/{id}`, not with the app.
