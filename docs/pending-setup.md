# Setup this repo cannot do for itself

A running list of the steps that have to happen **outside** the codebase — in the Play
Console, in Apple's developer portal, in a dashboard somewhere — before a feature that is
already merged actually works in production.

Everything here is written so that the feature ships first and degrades safely until the
step is done. Nothing on this page is blocking a release; each entry says what is inert
meanwhile.

---

## 1. Android App Links — the release signing fingerprint ✅ filled in

**Done:** `frontend/public/.well-known/assetlinks.json` carries the app signing key's
SHA-256. It takes effect when the frontend image that contains it is deployed, and Android
re-verifies on install and on app update — so a handset that already has the app needs a
reinstall, or the `verify-app-links --re-verify` below.

Until then, and on any handset that has not re-verified, every `https://cooknco.eu/...` link
opens the browser. The website serves the same page, so nothing is broken — the app is simply
not offered.

### Which key this is, because the console offers three

The fingerprint has to be of the certificate the APK is **installed** with. Play App Signing
re-signs every artifact it distributes, so that is Google's key and not the one CI uploads
with. **Test and release → Setup → App signing** lists up to three, and only the first
belongs here:

| Certificate on that page | Signs | In `assetlinks.json`? |
| --- | --- | --- |
| **App signing key certificate** | every install from Play — production, closed **and** internal testing tracks | **Yes.** This is the one. |
| Upload key certificate | only the artifact handed to Play; never reaches a device | No |
| Internal app sharing certificate | builds distributed by Play's internal app *sharing* links | Only if that is used — the internal testing *track* uses the app signing key |

The third is the one that costs an afternoon: it is a genuinely different key, sitting on the
same page, and internal app *sharing* is easy to confuse with the internal testing *track*
this pipeline publishes to. The track gets the app signing key.

A cheap way to confirm a fingerprint is *not* the upload key's, without needing the keystore
password — read the certificate out of a locally built bundle and compare:

```bash
unzip -p app/androidApp/release/androidApp-release.aab 'META-INF/*.RSA' > /tmp/upload.rsa
keytool -printcert -file /tmp/upload.rsa | grep SHA256
```

If that matches what went into `assetlinks.json`, the wrong key was copied.

### Adding the debug key too

The array takes several, which is how a locally installed build can verify alongside the
store one:

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android
```

### Deploying it

The file is baked into the frontend image, so this is an ordinary change: commit, bump the
version, merge to `develop`, and CI deploys it.

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

A first run failing with `The caller does not have permission` is a real misconfiguration, not
something to wait out: the Google Play Android Developer API not enabled on the linked Cloud
project, the wrong address invited to the Play Console (it must be the service account's own
`…iam.gserviceaccount.com`, not yours), or the grant not scoped to this app. The JSON key on
its own authorises nothing — [`playstore-ci.md`](playstore-ci.md) § 4 walks all five steps.

---

## 4. The privacy policy, the deletion URL, and the mailbox behind them

**What is inert until this is done:** nothing in the product. Both documents ship with the
frontend and are live the moment the image is deployed — `https://cooknco.eu/privacy` and
`https://cooknco.eu/account-deletion`, readable signed out, linked from the website's footer,
and the first of the two linked from the app's settings screen. What is missing is the two
console fields that point at them, and the mailbox they publish. **Play will not let the app
out of internal testing without the first**, and a deletion address that bounces is worse
than one that does not exist.

### The mailbox comes first

`contact@cooknco.eu` is written into both documents, in both languages, and into the failure
message the settings screens show. It has to *receive*, and be read: the deletion page
promises an answer within 30 days to somebody who has lost access to their account, and that
promise is the only route left for them.

Any address will do as long as it works — an alias forwarding to the Gmail the service already
sends through is enough. If it is ever a different address, one constant carries it on the
web (`CONTACT_EMAIL` in `frontend/src/locales/legal/shared.ts`) and four strings do not:
`delete_account_failed` in `frontend/src/locales/{en,fr}.ts` and `deleteAccountFailed` in
`app/composeApp/.../ui/i18n/{En,Fr}Strings.kt`.

### Where the two URLs go

Play Console → the app → **Policy → App content**:

| Field | Value |
| --- | --- |
| **Privacy policy** | `https://cooknco.eu/privacy` |
| **Data safety → Data deletion** | *Users can request that their data is deleted*, and the URL `https://cooknco.eu/account-deletion` |

The deletion URL is asked for inside the Data safety questionnaire rather than on a page of
its own, which is why it is easy to fill the form in and never be asked for it: the question
only appears once the form says some data is collected.

### What the Data safety form should say, and why

Every answer below is one the code makes true today. Anything that changes what is collected
changes this table, the two documents, and the form — in that order, and in the same change.

| Question | Answer | Because |
| --- | --- | --- |
| Personal info → Name | Collected, required | the username, which is the account |
| Personal info → Email address | Collected, required | sign-in and the account mails; stored encrypted |
| Photos and videos → Photos | Collected, optional | recipe pictures and the profile picture |
| Device or other IDs | Collected, optional | the FCM registration token, only while push is on |
| App activity, messages, location, financial, health… | Not collected | none of it exists in the schema |
| Is any of it **shared** with third parties? | No | Google carries mail and push as a processor, which Play's definition excludes |
| Is it encrypted in transit? | Yes | the ingress terminates TLS and nothing serves plain HTTP |
| Can users request deletion? | Yes | in the app, on the website, and by mail — `DELETE /api/v1/user` |
| Is any of it collected for advertising or analytics? | No | there is no advertising identifier and no measurement SDK in the build |

The app's manifest declares no `com.google.android.gms.permission.AD_ID`, and the only
Firebase library it pulls in is **Messaging** — no Analytics, no Crashlytics. The ML Kit
document scanner and text recogniser it also carries read the page on the device and send
nothing anywhere. That is what
makes the last row answerable with a flat no, and it is worth re-checking whenever a
dependency is added: a transitive Analytics would make the declaration false without anybody
writing a line of code.

### The listing's closing line is part of this

`app/store/listing-{fr-FR,en-GB}.md` ends on "no advertising, no sponsored content, no
infinite feed". A Data safety declaration that contradicts the store listing is a policy
problem rather than a wording one — the two say the same thing today, and stop saying it in
the same commit or not at all.

### Checking it worked

```bash
# Both pages, signed out, as a reviewer sees them
curl -sS https://cooknco.eu/privacy            | grep -o '<title>.*</title>'
curl -sS https://cooknco.eu/account-deletion   | grep -o '<title>.*</title>'

# The mailbox actually receives
echo "deletion test" | mail -s "Account deletion" contact@cooknco.eu
```

The two `curl`s return the SPA's shell rather than the text — the pages are Vue routes, and
Play's review opens them in a browser, which runs the JavaScript. What the `curl` proves is
that the path serves 200 rather than the SPA's 404 path; the text itself is worth opening in
a private window once, in both languages (the browser's language picks which).
