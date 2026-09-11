# Cooknco
Cooknco is an web application that allows you to write cooking recipes and share them with friends.

## Features
- Write recipes and add an image to them
- Like recipes from your friends
- Follow your friends to see their recipes appear on your feed
- Create cookbooks to group recipes, and add friends to your cookbooks to allow them to add their own recipes

## Infrastructure
Cooknco runs on a Kubernetes cluster. Here are the pods used:
- cooknco-backend: most of the application as a monolith for now
  - cooknco-database: the main database
  - cooknco-redis: used for sessions
- cooknco-mail-service: microservice used for sending mails. Who a mail goes to is decided
  by the backend, which owns the follow graph, and travels on the event — this service
  keeps no copy of the users
  - cooknco-mail-database: its database, holding only the operator-edited mail wordings
- cooknco-frontend: the frontend, powered by nginx

Kafka carries the events between the backend and the mail service; in the cluster it is a
separate Strimzi deployment, and `k8s/kafka-topics` declares the topics.

## Running locally
`compose.yaml` brings the same set of services up on one machine. The app images copy an
artefact the Gradle build already produced, exactly as CI builds them, so build first:

```
./gradlew build
docker compose up --build
```

Then open <http://localhost> — not `127.0.0.1`, or the `SameSite=Lax` session cookie is
never sent back and every call 401s. The default admin is `admin@mail.com` / `Passw0rd`.
The backend is also published directly on `:8081` for poking at the API.

On an arm64 machine every `/image/` request answers 500: the `libwebp-imageio` native
library packaged in the dependency jar is x86_64 only. Set `BACKEND_PLATFORM=linux/amd64`
to run the backend emulated, which serves images at the cost of a slower JVM.

The stack needs no `.env`: every variable has a local default. Set `REDIS_PASSWORD`,
`COOKNCO_SMTP_ADDRESS` / `COOKNCO_SMTP_PASSWORD` (the mail service sends through Gmail and
logs a failure without them), or `BACKEND_PORT` / `FRONTEND_PORT` to override. The backend
configuration compose mounts is `config/application.yaml` — placeholders, whereas
production reads the real one from the `cooknco-config` Secret.

## MCP server
The backend answers Model Context Protocol at `POST /mcp`, so an MCP client — Claude Code,
Claude Desktop — can read and write Cook&co as one account. It exposes twelve tools:
searching recipes, reading one in full, the followed-users feed, cookbooks, ingredients and
profiles; and, as writes, creating, editing and deleting a recipe, liking one, and saving one
into a cookbook.

Adding it takes the URL and nothing else:

```
claude mcp add --transport http cooknco https://cooknco.eu/mcp
```

Then `/mcp` inside the client opens a browser: sign in as you would to the app — password or
Google — and approve the client on the consent screen. Cook&co is its own OAuth 2.1
authorization server, so the client registers itself, gets a token bound to this endpoint, and
refreshes it on its own; nothing is pasted into a config file, and revoking a client is a
matter of the tokens expiring or Redis being cleared. Point it at `http://localhost/mcp` to
drive the local stack instead.

A session token still works in an `Authorization: Bearer` header, which is the easy path for a
script or a test:

```
TOKEN=$(curl -su mail@example.com:password -X POST https://cooknco.eu/api/v1/auth/login | jq -r .token)
claude mcp add --transport http cooknco https://cooknco.eu/mcp --header "Authorization: Bearer $TOKEN"
```

Either way the session *cookie* is refused, unlike on the rest of the API. A session token
lasts 30 days and slides forward whenever it is used, and signing out of the web app does not
affect it — only logging out with that token does.

The tools are defined in `backend/.../mcp/CookncoMcpServer.kt` and call the same services the
REST controllers do, which is what keeps a tool inside the visibility and ownership rules the
app itself obeys.

## Mobile app
The native app in `app/` is a Kotlin Multiplatform project sharing one Compose UI
across Android and iOS:
- `app/composeApp` — shared module. `commonMain` holds the whole app (UI, view models,
  networking); `androidMain` and `iosMain` hold only the platform pieces (photo picker,
  in-app browser, preferences path, HTTP engine).
- `app/androidApp` — Android application shell: manifest, resources, `MainActivity`.
- `app/iosApp` — Xcode project; builds the `ComposeApp` framework via
  `:composeApp:embedAndSignAppleFrameworkForXcode`.

```
cd app
./gradlew :androidApp:assembleDebug   # Android APK
open iosApp/iosApp.xcodeproj          # iOS (requires Xcode)
```

## Stack
- Kotlin
- Kotlin Multiplatform + Compose Multiplatform (mobile app)
- Ktor
- Ebean
- Vue.js
- Apache Kafka
