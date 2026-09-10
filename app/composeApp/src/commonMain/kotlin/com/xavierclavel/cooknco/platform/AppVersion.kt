package com.xavierclavel.cooknco.platform

/**
 * How this build names itself, as the platform records it.
 *
 * Android's `versionName` and iOS's `CFBundleShortVersionString` — the strings the stores
 * show, not the internal codes, because those are per-platform integers that could not be
 * compared against one number an operator types once.
 *
 * The empty string is a legitimate answer, on a build whose metadata could not be read. It
 * is what the backend treats as "cannot tell", and it answers OK — see `AppVersionService`.
 * That is deliberate: a build that has lost track of its own version must not be blocked
 * over it, since nothing on the phone could then unblock it.
 */
expect val appVersion: String
