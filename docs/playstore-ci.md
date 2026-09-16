# Publishing the Android app from CI

Two cycles, one build. The whole design exists because **Google Play refuses a `versionCode`
it has already seen, for ever** — even one belonging to a release since deleted — and because
a Play edit covers the whole app rather than a single track.

```
push to develop  →  android-internal-develop.yml  →  builds, uploads to `internal`,
                                                     tags android-build-<code>
push to master   →  android-release-master.yml    →  promotes internal → alpha (closed testing)
                                                     tags android-v<version>, opens a release
```

`develop` is the only branch that compiles. `master` promotes the bundle `develop` already
published, so what reaches closed testing is bit-for-bit what was tested internally, and the
release cycle takes a minute instead of ten.

The shared body of both is `.github/workflows/android-deploy.yml`, a reusable workflow whose
header explains every mechanism in detail. This page is the operator's side: what has to exist
outside the repository for any of it to run.

---

## 1. Branch model

| Branch | Backend | Android |
| --- | --- | --- |
| `develop` | built, pushed to Docker Hub, applied to the cluster (`build.yml`) | built, uploaded to `internal` |
| `master` | nothing | promoted `internal` → `alpha`, tagged, GitHub release |

`develop` is the default branch and the base of every PR. **`master` must only ever receive
merges from `develop`**: an app commit pushed directly there is promoted *without its own
changes* — it is `develop`'s last build that goes out, the promotion succeeds, and nothing
reports it.

`build.yml` ignores `app/**`, so an app-only merge to `develop` does not roll the backend. The
reverse also holds: `android-internal-develop.yml` only fires on `app/**` and on the two
Android workflow files.

---

## 2. Versioning

`versionCode` is the **commit count** of the whole monorepo (`git rev-list --count HEAD`),
derived by Gradle in `app/androidApp/build.gradle.kts` and never committed. `versionName` is
`"<versionMajor>.<versionCode>"`, with `versionMajor` the only value still written by hand.

Consequences worth knowing:

- **`versionName` changes on every commit.** It carries no semantic meaning beyond the major.
  That is the price of zero bot commits and of a number no two branches can disagree on.
- **A backend-only commit moves it too.** The number only has to be monotonic; one shared with
  the repository is one nothing about the app can reset.
- **Every checkout in the Android workflows is `fetch-depth: 0`.** A shallow clone would count
  a handful of commits and produce a bundle numbered far below the last published one. The
  `check` job compares the number against the `android-build-*` tags before anything compiles,
  so this fails in ten seconds rather than ten minutes.
- **CI never greps the version.** It asks Gradle (`./gradlew -q :androidApp:printVersion`, run
  from `app/`), and the `upload` job refuses to compile if Gradle and its own derivation
  disagree.
- **If history is ever rewritten** and the count goes backwards, the only lever is a constant
  offset added to `gitCommitCount`. Play will not take a number twice.

The backend's `version` in the root `build.gradle.kts` is unrelated and still bumped by hand
per `CLAUDE.md`.

### The tags

| Tag | Pushed by | What it is |
| --- | --- | --- |
| `android-build-<code>` | every successful `internal` upload | the registry of published builds — **the only thing that tells `master` what to promote** |
| `android-v<major>.<code>` | `master`, after a successful promotion | the release milestone, with a GitHub release beside it |
| `v<x.y.z>` | `build.yml` | the backend's Docker image version, unrelated |

`android-build-*` is pushed *after* the upload, never before: a tag for a release that did not
go out would have `master` promote a `versionCode` the source track does not carry.

---

## 3. Track names

The API identifiers never followed the console's renamings:

| API | Console |
| --- | --- |
| `internal` | Internal testing — no Play review, up to 100 testers, live in minutes |
| `alpha` | **Closed testing** |
| `beta` | Open testing |
| `production` | Production |

`master` promotes to `alpha`. That choice is not cosmetic: only a **closed** test feeds the
"12 testers for 14 consecutive days" counter a personal developer account has to satisfy
before Google will open production.

A promotion **moves** the release — Play deactivates it on the source track, so `internal` is
left with no active release until the next push to `develop`. That is how a track funnel works,
not a fault of the workflow.

---

## 4. The five repository secrets

Settings → Secrets and variables → Actions. The `upload` job checks all five before spending
ten minutes on a build, and names the missing one.

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the upload keystore, base64 of the `.jks` file |
| `KEYSTORE_PASSWORD` | its store password |
| `KEY_ALIAS` | the key alias inside it |
| `KEY_PASSWORD` | that key's password |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole Play service-account key file, verbatim |

### The keystore

The app already on the store was signed with a key generated by Android Studio's *Generate
Signed Bundle* flow. **That same key has to go into CI** — Play matches the upload key on every
upload, and a different one is rejected outright. If it has been lost, the Play Console's
*App signing → Request upload key reset* is the only way back.

Encoding it, from wherever the `.jks` lives:

```bash
# macOS / Linux
base64 -i upload-keystore.jks | tr -d '\n' | pbcopy
```

```powershell
# Windows
[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload-keystore.jks")) | Set-Clipboard
```

Paste that single line as `ANDROID_KEYSTORE_BASE64`. A value truncated on copy decodes without
complaint and then fails signing ten minutes later, which is why the workflow runs
`keytool -list` on the decoded file and says so instead.

The alias and passwords are the ones typed into Android Studio when the key was created:

```bash
keytool -list -v -keystore upload-keystore.jks    # prints the alias
```

### The Play service account

**The JSON key on its own grants nothing.** It authenticates a principal that, until the two
steps below are done, is allowed to do precisely nothing with this app. Both are easy to skip,
and skipping either produces a 403 rather than anything that names the cause.

1. **Link a Cloud project.** Play Console → **Setup → API access**. Link an existing Google
   Cloud project or let the console create one.
2. **Enable the API on that project.** Google Cloud console → **APIs & Services → Library →
   Google Play Android Developer API → Enable**. Missing, every call fails with
   *"Google Play Android Developer API has not been used in project N before or it is
   disabled"* — which at least names itself, and the error carries the enable link.
3. **Create the service account.** Google Cloud console → **IAM & Admin → Service Accounts →
   Create**, then **Keys → Add key → JSON**. Download it. No Cloud IAM role is needed: the
   authorisation that matters is granted in the Play Console, not here.
4. **Invite it as a Play Console user.** Play Console → **Users and permissions → Invite new
   users**, pasting the service account's own address — the
   `something@project-id.iam.gserviceaccount.com` from the Service Accounts page, not your own.
   This is the step people miss: the account exists, the key works, and it can see no apps.
5. **Grant it, under App permissions scoped to Cooknco** (not Account permissions):
   - **View app information (read-only)**
   - **Release apps to testing tracks**

   That second one covers both halves of this pipeline — `develop`'s upload to `internal` and
   `master`'s promotion of it to closed testing. **Release to production** is deliberately not
   granted: nothing here writes to production, and the `production` option on
   `android-release-master.yml`'s manual dispatch will fail until it is. Grant it the day you
   actually want that, so an accidental dispatch cannot ship to everyone before then.
6. Paste the entire JSON file as `PLAY_SERVICE_ACCOUNT_JSON`. The workflow parses it and
   refuses a truncated paste rather than failing mid-upload.

Google's documentation says access is available as soon as the invitation is accepted, and it
usually is. So treat `The caller does not have permission` as a real misconfiguration rather
than something to wait out: check the API is enabled (step 2), that the invited address is the
service account's and not yours (step 4), and that the grant is on this app (step 5). Waiting a
day on the assumption it will settle is how an afternoon gets lost to a disabled API.

---

## 5. The manual gate on `master`

`android-release-master.yml` runs its publication in the `play-store` GitHub environment.
GitHub creates it on the first run, and it is **inert until someone attaches a required
reviewer**: Settings → Environments → `play-store` → Required reviewers. Do that and every
promotion to closed testing waits for an approval.

`develop`'s internal builds pass `environment: ''` — no guard. An internal test has no business
waking a reviewer.

---

## 6. Running it by hand

Both workflows carry `workflow_dispatch`. `android-release-master.yml` also takes a `track`
input (`alpha` / `beta` / `production`), which is how a release finally reaches production once
Google opens it.

Locally, from `app/`:

```bash
# What CI is about to publish
./gradlew -q :androidApp:printVersion            # "1.679 679"

# A signed bundle, with the key referenced from local.properties
#   KEYSTORE_FILE=../path/to/upload-keystore.jks
#   KEYSTORE_PASSWORD=…
#   KEY_ALIAS=…
#   KEY_PASSWORD=…
./gradlew :androidApp:bundleRelease

# Upload it yourself, with your own Play key
PLAY_JSON_KEY_PATH=~/keys/cooknco-play.json bundle exec fastlane deploy track:internal
```

`local.properties` is gitignored, and `configValue` reads it before the environment so a
variable left exported in a shell cannot silently override the file.

There is no committed `Gemfile.lock`: `bundler-cache: true` resolves and caches on the runner.
Committing one (`cd app && bundle install`) would pin fastlane and make the two cycles
reproducible — worth doing the next time anyone has Ruby to hand.

---

## 7. When it goes wrong

| Message | Cause |
| --- | --- |
| `versionCode N <= M, already published` | the clone went shallow, or history was rewritten. Check `fetch-depth: 0` first. |
| `versionCode N is not on the "internal" track` | that version was never built. `master` compiles nothing — the change has to go through `develop`. |
| `No android-build-* tag among the ancestors` | this commit of `master` contains no published build. Push to `develop` first. |
| `Cannot query the develop cycle's runs` | `actions: read` missing from the caller, or `android-internal-develop.yml` renamed. |
| `The decoded keystore is unreadable` | `ANDROID_KEYSTORE_BASE64` truncated, or `KEYSTORE_PASSWORD` wrong. |
| `is not signed — the release signingConfig did not apply` | `KEYSTORE_FILE` did not reach Gradle, so `signingConfigs.findByName("release")` returned null. |
| `N rounds of waiting and the develop cycle is still publishing` | merges are landing faster than the promotion can catch up. Re-run once `develop` settles. |
| `The caller does not have permission` | § 4 is incomplete — API not enabled, the wrong address invited, or the grant not on this app. Not a propagation delay. |
| `Google Play Android Developer API has not been used in project N…` | § 4 step 2. The error carries the link that enables it. |

A release that sits in "In review" on a closed track is normal — closed testing goes through
Play review, unlike internal testing. Nothing in this pipeline can shorten that.
