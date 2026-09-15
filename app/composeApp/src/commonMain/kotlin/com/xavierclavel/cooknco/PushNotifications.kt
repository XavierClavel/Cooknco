package com.xavierclavel.cooknco

import com.xavierclavel.cooknco.navigation.WebRoutes
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
     *   as `/recipe/view?id=12`. Translated by [WebRoutes], which a deep link goes through
     *   too; anything it cannot place is dropped rather than guessed at, because opening the
     *   app on the wrong screen is worse than opening it on its own.
     */
    fun onNotificationTapped(link: String?) {
        WebRoutes.routeForPath(link)?.let { _taps.tryEmit(it) }
    }
}
