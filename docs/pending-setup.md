# Setup this repo cannot do for itself

A running list of the steps that have to happen **outside** the codebase — in the Play
Console, in Apple's developer portal, in a dashboard somewhere — before a feature that is
already merged actually works in production.

Everything here is written so that the feature ships first and degrades safely until the
step is done. Nothing on this page is blocking a release; each entry says what is inert
meanwhile.

---

## 1. Android App Links — the release signing fingerprint

**What is inert until this is done:** every `https://cooknco.eu/...` link opens the browser
instead of the app. The website serves the same page, so nothing is broken — the app is
simply never offered.

**Where the placeholder is:** `frontend/public/.well-known/assetlinks.json`

```json
"sha256_cert_fingerprints": ["REPLACE_WITH_THE_RELEASE_SIGNING_CERTIFICATE_SHA256"]
```

### Getting the value

The fingerprint has to be of the certificate the APK is **installed** with, which is not
necessarily the one it was uploaded with — Play App Signing re-signs, and the upload key's
fingerprint would verify nothing for a store install.

1. The app is on the store, so Play App Signing is enrolled and this value now exists.
   `app/androidApp/build.gradle.kts` declares a release `signingConfig` fed by
   `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` (see § 3 and
   [`playstore-ci.md`](playstore-ci.md)) — but the fingerprint wanted below is **not** the
   upload key's: Play re-signs, so a store install carries Google's certificate rather than
   the one CI uploads with.
2. Play Console → the app → **Test and release → Setup → App signing**. Copy the SHA-256
   under **App signing key certificate** — uppercase hex, colon-separated.
3. Paste it into the array. The array takes several, which is how a debug build can be
   verified alongside the store one:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore \
     -alias androiddebugkey -storepass android -keypass android
   ```

### Deploying it

The file is baked into the frontend image, so filling it in is an ordinary change: commit,
bump the version, merge to `develop`, and CI deploys it.

### Checking it worked

```bash
# The domain's side — what Google's verifier reads
curl -sS https://cooknco.eu/.well-known/assetlinks.json

# Google's own parse of it, which catches a malformed statement the curl above will not
curl -sS 'https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://cooknco.eu&relation=delegate_permission/common.handle_all_urls'

# The handset's side. Android verifies on install and on update, so reinstall first.
adb shell pm get-app-links com.xavierclavel.cooknco     # expect: cooknco.eu: verified
adb shell pm verify-app-links --re-verify com.xavierclavel.cooknco
```

A device that says `1024` rather than `verified` has no network answer yet; `1` means the
statement was fetched and rejected — check the fingerprint's case and colons.

---

## 2. iOS Universal Links — the Apple team ID

**What is inert until this is done:** the same thing, on iOS. Links open Safari.

**Where the placeholders are — two files, and they must carry the same team:**

| File | Placeholder |
| --- | --- |
| `frontend/public/.well-known/apple-app-site-association` | `"REPLACE_WITH_TEAM_ID.com.xavierclavel.cooknco"` |
| `app/iosApp/Configuration/Config.xcconfig` | `TEAM_ID=` (empty) |

### Getting the value

1. [developer.apple.com](https://developer.apple.com/account) → **Membership details** →
   **Team ID**, ten characters. This needs a paid Apple Developer Program membership; there
   is no team ID without one.
2. **Certificates, Identifiers & Profiles → Identifiers →** the `com.xavierclavel.cooknco`
   App ID → enable the **Associated Domains** capability, then regenerate the provisioning
   profile. The entitlement in `app/iosApp/iosApp/iosApp.entitlements` is already committed,
   but an entitlement the App ID does not grant fails code signing rather than being ignored.

### Checking it worked

```bash
# The file itself, which must come back as application/json and with no redirect
curl -sSI https://cooknco.eu/.well-known/apple-app-site-association

# Apple's CDN copy — this is what a device actually reads, and it caches
curl -sS https://app-site-association.cdn-apple.com/a/v1/cooknco.eu
```

The CDN can take up to 24 hours to pick up a change. Two things that waste an afternoon if
you do not know them: a device reads the file **on install**, so an app installed before the
file was right needs reinstalling; and typing a URL into Safari's address bar never opens
the app — tap a link from Notes or Messages instead.

For testing before the CDN catches up, `applinks:cooknco.eu?mode=developer` in the
entitlement, with **Settings → Developer → Associated Domains Development** on, makes the
device fetch the file from the domain directly. Do not ship that form.

---

## 3. Play publication from CI — the five secrets, and the branch model

**What is inert until this is done:** nothing regresses, but the app stops being publishable
from CI. `android-internal-develop.yml` fails its first step — deliberately, before spending
ten minutes on a build — naming whichever secret is missing. Publishing stays a manual
*Generate Signed Bundle* and a drag into the Play Console meanwhile.

**Where they go:** Settings → Secrets and variables → Actions.

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the **existing** upload keystore, base64 of the `.jks`, one line |
| `KEYSTORE_PASSWORD` | its store password |
| `KEY_ALIAS` | the key alias inside it |
| `KEY_PASSWORD` | that key's password |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole Play service-account key file, verbatim |

It has to be the **same** keystore the store build was signed with: Play matches the upload key
on every upload and rejects a different one. If it is gone, *App signing → Request upload key
reset* in the console is the only way back.

[`playstore-ci.md`](playstore-ci.md) has the commands for encoding it, the Play service-account
steps, the track-name table, and what each failure message means.

### Two things to set once, outside the secrets

- **`develop` is the default branch**, and the base of every PR. `build.yml` deploys the
  backend from it; `master` is the app's release branch and deploys no backend at all.
  **`master` must only ever receive merges from `develop`** — an app commit pushed straight
  there is promoted *without its own changes*, and the promotion succeeds, so nothing reports
  it.
- **The `play-store` environment guards the promotion to closed testing.** GitHub creates it
  on the first run and it is inert until Settings → Environments → `play-store` → *Required
  reviewers* names somebody. Until then every merge to `master` promotes without asking.

### Checking it worked

```bash
# The first push to develop should build and upload; the tag is the evidence it reached Play
git fetch --tags && git tag --list 'android-build-*'

# What CI thinks it is publishing, from app/
cd app && ./gradlew -q :androidApp:printVersion
```

A first run failing with `The caller does not have permission` is usually Play permissions
still propagating — Google takes up to 24 hours — not a wrong key.
