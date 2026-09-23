package com.xavierclavel.cooknco.network

/**
 * Whether this failure means "there was no server" rather than "the server said no".
 *
 * The distinction is the hinge of everything offline in this app, and it is made once, here.
 * An [ApiException] is the server answering — a 401, a 404, a 500 — whatever it answered, it
 * answered, and a cached copy must not stand in for it. Anything else got out of Ktor with no
 * status at all: a name that would not resolve, a socket that was refused, a request that
 * timed out. That, and only that, is a failure a copy on this phone may answer instead.
 *
 * It decides three separate things, which is why it is worth naming rather than inlining:
 * whether a session survives a failed `whoami`
 * ([com.xavierclavel.cooknco.data.AuthRepository.getCurrentUser]), whether a repository falls
 * back to the store, and whether the app says it is offline
 * ([com.xavierclavel.cooknco.data.OfflineState]).
 */
val Throwable.isOffline: Boolean get() = this !is ApiException
