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
- cooknco-frontend: the frontend, a Nuxt server-rendering the shareable pages
  and proxying /api and /image to the backend

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
- Nuxt (Vue 3, SSR)
- Apache Kafka
