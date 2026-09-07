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
- cooknco-mail-service: microservice used for sending mails
  - cooknco-mail-database: its database
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
