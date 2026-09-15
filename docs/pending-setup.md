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

1. There is no release signing key yet: `app/androidApp/build.gradle.kts` declares no
   `signingConfig`, so a release build is unsigned today. Creating one, and enrolling in
   Play App Signing on the first upload, comes first.
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
bump the version, merge to `master`, and CI deploys it.

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
