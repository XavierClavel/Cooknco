package com.xavierclavel.cooknco

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge for a tapped notification, on the same design as [DeepLinks].
 *
 * The platform entry point pushes the tapped notification's `link` in; [App] collects it and
 * navigates. Replays the last one so a tap that cold-starts the app is not lost before the
 * UI has composed — which is the common case, since a notification is usually tapped on a
 * device where the app is not running.
 */
object PushNotifications {

    private val _taps = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    /** App routes to navigate to, already translated from the backend's paths. */
    val taps: SharedFlow<String> = _taps.asSharedFlow()

    /**
     * Records a tapped notification.
     *
     * @param link the `link` the push carried — an app-relative path the backend built, such
     *   as `/recipe/view?id=12`. Anything this cannot place is dropped rather than guessed
     *   at: opening the app on the wrong screen is worse than opening it on its own.
     */
    fun onNotificationTapped(link: String?) {
        routeFor(link)?.let { _taps.tryEmit(it) }
    }

    /**
     * Translates one of the backend's paths into this app's own route.
     *
     * The backend stores an app-relative *web* path, because one value has to serve both
     * clients: the web app routes to it as it stands, and this maps it. Keeping the mapping
     * here rather than in the notification payload means a new screen is a change in the app
     * rather than a migration of every notification already sent.
     */
    fun routeFor(link: String?): String? {
        if (link.isNullOrBlank()) return null
        // Resolved against a base because the stored value is a path, which Url alone rejects
        val parsed = runCatching { Url("https://cooknco.eu${if (link.startsWith("/")) link else "/$link"}") }
            .getOrNull() ?: return null

        return when (parsed.encodedPath.trimEnd('/')) {
            "/recipe/view" -> parsed.parameters["id"]?.let { "recipe/$it" }
            "/user/view" -> parsed.parameters["user"]?.let { "user/$it" }
            "/cookbook/view" -> parsed.parameters["id"]?.let { "cookbook/$it" }
            else -> null
        }
    }
}
