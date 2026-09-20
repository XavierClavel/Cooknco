package com.xavierclavel.cooknco.platform

import com.xavierclavel.cooknco.data.CookSessionState

/**
 * Mirrors the recipe being cooked onto whatever the platform shows outside the app.
 *
 * One call, unlike the timer's two: a session has no moment worth interrupting anybody for.
 * It is furniture — the step the cook is on, silently redrawn each time it changes, and
 * taken down on `null`, which means nothing is being cooked any more.
 *
 * What the platform is asked for is also the way *back*: the notification is not a readout
 * of the session but the thing driving it, so whatever this posts has to be able to send
 * "next", "previous" and "done" to [com.xavierclavel.cooknco.data.CookSession].
 */
expect fun onCookSessionChanged(state: CookSessionState?)
