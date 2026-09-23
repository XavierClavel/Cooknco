package com.xavierclavel.cooknco.platform

import okio.Path

/**
 * Where the recipes kept for offline use live.
 *
 * **Not a cache directory, on either platform, and that is the whole point of it.** Coil's
 * own disk cache sits in `FileSystem.SYSTEM_TEMPORARY_DIRECTORY` — `cacheDir` on Android,
 * `NSTemporaryDirectory()` on iOS — which the system empties whenever it wants the space.
 * That is the correct home for something we merely happened to download, and the wrong one
 * for something the app has told a cook they can rely on in a kitchen with no signal.
 *
 * Android answers `filesDir/offline`, captured at startup because a `Context` is the only
 * thing that knows it; iOS answers Application Support, with the directory excluded from
 * iCloud backup — every byte under here can be downloaded again, and none of it belongs in
 * somebody's backup allowance.
 *
 * The directory is not guaranteed to exist; [com.xavierclavel.cooknco.data.OfflineStore]
 * creates what it writes into.
 */
expect fun offlineRoot(): Path
