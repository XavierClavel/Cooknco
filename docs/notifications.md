# Push notifications

Cook&co pushes through Firebase Cloud Messaging. The backend sends, the Android app
receives, and the web app shows the same notifications as an in-app list.

## What a notification is

A notification is a **row in `notifications`**, addressed to one user. The push is only the
buzz that announces it.

That order matters and is the reason the two halves exist:

- A row is written for **every** recipient, whether or not a push could be delivered. A user
  with no device, a dead token, or notifications switched off at the OS still finds
  everything addressed to them when they next open the app or the website.
- The wording is stored **already rendered**, not as a kind plus values. Re-rendering later —
  against a recipe since retitled, or a user since renamed — would make the in-app list
  disagree with the notification still sitting on the recipient's phone.

`NotificationService` is the only thing that writes them, and it always does so before it
pushes.

## Clearing

A notification is the recipient's to be rid of. `DELETE /api/v1/notification/{id}` clears one
and `DELETE /api/v1/notification` clears the lot, both scoped to the caller — one that is not
theirs answers 404 rather than 403, because an answer that told the two apart would let a
caller learn which ids exist.

**It is a delete, not a flag.** The row *is* the notification, so a cleared one left in the
table would put a filter on every read from then on to keep the list and the table agreeing,
and `unreadCount` — a count over unread rows — would be the first thing to get it wrong.
Deleting keeps that count honest with nothing said to it.

Three things clearing deliberately does not do:

- **It does not recall the push.** The buzz has already happened, and the notification may
  still be sitting in the handset's own tray. A client that then taps it marks a row that is
  gone, which is the 404 both clients already tolerate on `markRead`.
- **It does not touch follow requests.** They are the other half of the bell and a queue the
  user has to answer rather than news, so clearing the notification that announced a request
  leaves the request itself waiting. A "clear all" that declined everybody would be the same
  gesture with a very different meaning.
- **It is not per client.** The list is one list — the same rows the app reads — so clearing
  on the website clears it everywhere. That follows from a notification being a row rather
  than a copy per device, and it is worth keeping: the alternative is a cleared-state per
  client, a second thing to keep in sync for nobody's benefit.

Clearing all clears everything addressed to the user, not the page they were shown. The bell
asks for `DEFAULT_PAGE_SIZE` at a time, so a clear bounded by what came back would empty a
list that fills itself again on the next poll, with no way for the user to tell how many
presses it takes.

## The kinds

`shared.enums.NotificationKind` is the list, and `shared.utils.NotificationWordings` holds
the wording each one goes out with, per locale. A kind's wording and the placeholders it may
fill in are declared together, so a kind whose text names `{{username}}` is one whose emitter
is known to have a username to give it.

| Kind | Emitted when | Emitted from |
|---|---|---|
| `new_recipe` | someone you follow publishes a recipe | `RecipeController.createRecipe` |
| `new_follower` | someone follows you, on an account that accepts follows outright | `FollowService.createFollow` |
| `follow_request` | someone asks to follow you, on an account that holds requests | `FollowService.createFollow` |
| `follow_accepted` | a request you sent was accepted | `FollowService.acceptFollowRequest` |
| `announcement` | an operator sends one from the backoffice | `AdminNotificationController` |

`announcement` is the odd one out on purpose: an operator types its title and body at send
time, so it has no packaged wording and nothing to translate. It is not a template — unlike a
mail, an announcement is a one-off, and a template for it would be a template with one use.

### Adding a kind

1. Add the entry to `NotificationKind`, with the placeholders its wording may use.
2. Add its wording to `NotificationWordings.packaged`, for every `Locale` — the `when` is
   exhaustive, so a missing one is a compile error rather than a blank notification.
3. Emit it: a method on `NotificationService` alongside `onRecipeCreated` and friends, called
   from wherever the thing actually happens.

Nothing else needs touching. The list endpoints, the admin tab and both clients are
kind-agnostic.

## Fan-out, and why it is in the background

`NotificationService.dispatch` runs on the service's own coroutine scope, not the caller's.
Publishing a recipe must not wait on a push to every follower, and must not fail because one
of them could not be reached — the recipe is already committed and answered by then.

Only ids cross into the coroutine. The entities the caller holds belong to its transaction,
which is closed by the time the fan-out runs, so the recipients are re-read on the other side.

An announcement is the exception, and only half of one: the rows are written before the
request returns, so the count the backoffice reports is real, and only the pushes are left in
the background.

## FCM, concretely

`FcmPushSender` calls the HTTP v1 API, one request per device, a bounded number at a time
(`Configuration.Push.maxConcurrentSends`). That is not a shortcut around a batch endpoint:
FCM's `/batch` was retired in 2024 and the official SDKs now do exactly this behind a method
that still looks like a batch.

`FcmAccessTokens` mints the access token — a JWT signed with the service account's private
half, traded at Google's token endpoint, cached until shortly before it expires. Hand-rolled
rather than pulling in `google-auth-library`, which would bring the Google HTTP client and
Guava along for one signature and one form POST.

Two details worth knowing before changing the payload:

- **Every message carries both a `notification` and a `data` block.** The `notification` half
  is what lets Android display it while the app is backgrounded or stopped — the case that
  matters most, and the one a data-only message does not cover. The `data` half carries the
  kind, the notification's id and the path to open. They never draw twice: with a
  `notification` block present, Android hands the message to the app *instead of* displaying
  it whenever the app is in the foreground, and displays it itself otherwise.
- **`android.notification.channel_id` must name a channel the app has created.** From Android
  8 on, a message naming a channel that does not exist is dropped without a trace. The one
  channel is `cooknco_default`, and it is spelled in three places that have to agree:
  `FcmPushSender.DEFAULT_CHANNEL_ID`, `CookncoMessagingService.CHANNEL_ID`, and the
  `default_notification_channel_id` meta-data in `AndroidManifest.xml`.

### Dead tokens

Only `UNREGISTERED`, `INVALID_ARGUMENT` and `SENDER_ID_MISMATCH` mean the device is gone, and
only those prune the `devices` row. A 401, a 429, a timeout or a 503 is this backend's problem
or Google's, and deleting on one of those would quietly unsubscribe live users during an
outage.

## Devices

A device is a token, not a handset: the clients have no stable id to offer and do not need
one, since FCM already guarantees a token identifies exactly one install.

`devices.token` is unique **across the table**, not per user. Registering a token that already
exists re-points it at the caller, which is the case that matters — a resold or shared handset
would otherwise keep receiving the previous owner's notifications.

Rows are created by `POST /api/v1/notification/devices?locale=EN` and removed either by the
client (`POST .../devices/unregister`, on sign-out) or by the pruning above. Nothing expires
them on a timer: a token stays valid across app restarts and reboots, so an idle install is
not a stale one.

The locale is stored **on the device**, not read off the account, because it is the one in
force where the notification will actually be read — a user with the app in English and the
website in French gets each in its own language.

## Consent

There is no per-user notification preference. The off switch is the platform's own: Android
13+ asks for `POST_NOTIFICATIONS` (once, after sign-in — see `EnsureNotificationPermission`),
and signing out unregisters the device. Nothing is pushed to a user who has no device row.

A preference — per kind, or one switch — would be a reasonable next step, and
`shared.events.NotificationsToggledEvent` already exists unused for it.

## The clients

### Android app (`app/`)

| Piece | Does |
|---|---|
| `currentPushToken()` (`androidMain`) | asks Firebase for this install's token |
| `CookncoMessagingService` | receives a push in the foreground and draws it; picks up token rotations |
| `MainActivity.handleNotificationIntent` | picks up a tap on a notification the *system* drew |
| `PushNotifications` | replays the tap to `AppNavigation`, translating the backend's path to an app route |
| `PushRepository` | registers on launch and on rotation, unregisters on sign-out |

The app lists notifications and marks them read; it does not clear them yet. Nothing in the
endpoints is web-specific, so adding it there is a screen rather than a backend change.

iOS is a stub: `currentPushToken()` returns null there, and `pushSupported` is false. Reaching
a working one needs an APNs key, the Firebase iOS SDK in a real Xcode project, and a
`UNUserNotificationCenter` delegate — none of which exists yet.

### Web app (`frontend/`)

No web push. The bell in `App.vue` polls `GET /api/v1/notification` (already on a five-minute
`pollingStore`) and shows the notification list next to the follow-request queue. Tapping one
marks it read and navigates to its `link`.

Every row also carries a clear button, and the foot of the menu offers "mark all as read" and
"clear all" side by side — the two things a user can mean by being done with a notification.
Both clearing actions take the row out of the list on the spot rather than waiting for the
next poll, but only once the backend has agreed: a clear that silently failed would otherwise
reappear five minutes later. The per-row button stops the click from reaching the row, so
clearing one neither opens it nor closes the menu on the way.

### Links

A notification's `link` is an app-relative **web** path — `/recipe/view?id=12` — because one
value has to serve both clients. The web app routes to it as it stands; the app maps it in
`PushNotifications.routeFor`. Keeping the mapping in the app means a new screen is a change in
the app rather than a migration of every notification already sent.

## Setting it up

### 1. Firebase project

The project is `cooknco-9282f`. Both halves below are already in place; this is what to
redo if the project is ever recreated or the key rotated. In the
[Firebase console](https://console.firebase.google.com/):

- **The Android app** is registered under package name `com.xavierclavel.cooknco`. Its
  `google-services.json` is committed at `app/androidApp/google-services.json`, and the app
  build fails with a clear message from the Google Services plugin if it goes missing.
- Under **Project settings → Service accounts**, generate a private key. That JSON is the
  backend's half, and it is the one real secret here — see below.

Both halves must name the **same** Firebase project, or every push is refused.

### Why only one of the two is a secret

`google-services.json` is tracked, which surprises people. The Google Services plugin turns
it into ordinary string resources — `google_api_key`, `google_app_id`, `gcm_defaultSenderId`
— and they end up in `resources.arsc` of every build, so every install of the app already
carries the lot. Hiding it from the repository would protect nothing and would only stop a
clean clone from building. Firebase says as much itself: a Firebase API key identifies the
project, and what guards the project is Security Rules and App Check, not the key's secrecy.
Worth restricting the Android key to the package name and signing certificate in the Google
Cloud console all the same, since it is public either way.

The **service account private key** is the opposite. It authorises sending, so it can put a
notification on every device the app is installed on. It is never committed, never in an
image, and lives only in the `cooknco-config` Secret.

### 2. Backend

The service account key is a genuine secret: it can send a notification to every install of
the app. It is never in the image.

Production reads it from the `cooknco-config` Secret, which is already mounted at
`/app/config` — so it goes in as a second key alongside `application.yaml`.

> **Add the key; do not rewrite the Secret.** The production `application.yaml` exists only
> in the cluster (k8s/README.md) — there is no copy in this repository. Anything of the shape
> `kubectl create secret … --dry-run=client -o yaml | kubectl apply -f -` restates the whole
> Secret from local files, so it overwrites `application.yaml` with whatever is on your disk.
> The repo's `config/application.yaml` is the **local dev** placeholder, and applying that
> would point production at `localhost`, blank the SMTP credentials, and replace
> `encryption.key` with the dev key — after which no existing user's mail address decrypts.
> Patch one key at a time instead.

```sh
# 1. See what is actually in there. Never assume it is only application.yaml.
kubectl -n cooknco get secret cooknco-config -o go-template='{{range $k,$_ := .data}}{{$k}}{{"\n"}}{{end}}'

# 2. Add the service account key. A merge patch on `data` touches no other key.
kubectl -n cooknco patch secret cooknco-config --type merge \
  -p "{\"data\":{\"fcm-service-account.json\":\"$(base64 < ./fcm-service-account.json | tr -d '\n')\"}}"
```

Then turn it on — by editing the **live** `application.yaml`, not a local one:

```sh
# 3. Pull the one that is in service, and append the push block to it.
kubectl -n cooknco get secret cooknco-config \
  -o go-template='{{index .data "application.yaml" | base64decode}}' > application.live.yaml

cat >> application.live.yaml <<'YAML'
push:
  enabled: true
  # Defaults, listed for the record. projectId blank takes it from the key file,
  # which is the normal case — the two disagreeing is a misconfiguration.
  credentialsPath: /app/config/fcm-service-account.json
  projectId: ""
  maxConcurrentSends: 8
YAML

# 4. Put it back, again one key at a time.
kubectl -n cooknco patch secret cooknco-config --type merge \
  -p "{\"data\":{\"application.yaml\":\"$(base64 < application.live.yaml | tr -d '\n')\"}}"

# 5. Restart: loadConfig() reads the file once, at startup.
kubectl -n cooknco rollout restart deployment/cooknco-backend
```

The log line on the way up says which sender the container got, which is the quickest
confirmation that step 4 landed.

(`base64 -d` is `base64 -D` on macOS, and `base64 -w0` is a GNU-only shorthand for the
`| tr -d '\n'` above.)

Locally, drop the key at `config/fcm-service-account.json` and uncomment the `push:` block in
`config/application.yaml`.

**With no credentials, nothing breaks.** `push.enabled` defaults to false, the container gets
`NoopPushSender`, and the log says so once. A key that cannot be read is also not fatal — a
bad key must not take the backend down — so it degrades the same way. Notifications are still
stored and still listed; only the buzz is missing.

### 3. Checking it works

The backoffice notifications tab sends to your own devices only:

1. Sign in to the app on a handset, and grant notifications.
2. Backoffice → Notifications → type a title and message → **Send to me**.

It answers with how many of your devices were pushed to, rather than "queued", because a test
whose answer was "queued" would not have tested the thing anyone runs it to test.

## Tests

`:backend:test` swaps in `FakePushSender`, which records the messages rather than counting
them: what the tests assert is the payload — the wording, the kind, and the notification id
the clients mark read by. `FakePushSender.awaitKind` waits on a fan-out, since the request
that triggers one returns before the push is made.

`FcmPushSenderTest` covers the transport against a Ktor `MockEngine`, asserting on the
request that goes out: that the body is serialised JSON, that the payload carries the fields
both clients rely on, that the `channel_id` matches what the app registers, and that only
`UNREGISTERED` / `INVALID_ARGUMENT` / `SENDER_ID_MISMATCH` are reported stale while a 503
keeps the token. An earlier version of this file claimed the transport was not worth
covering; it is the one class that builds an HTTP request, and it shipped two bugs on that
excuse — a payload missing a required field, then a body the client could not serialise.

What genuinely cannot be covered is [FcmAccessTokens]: signing a JWT and trading it at
Google's endpoint needs a real key, and a fake one would only assert that the code does what
it does. That is why `FcmCredentials` is an interface — it is the seam between the part worth
testing and the part that is not.
