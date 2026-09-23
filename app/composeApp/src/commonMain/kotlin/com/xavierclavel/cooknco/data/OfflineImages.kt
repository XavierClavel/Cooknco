package com.xavierclavel.cooknco.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xavierclavel.cooknco.network.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess

/**
 * The pictures of pinned recipes, as files this app owns.
 *
 * Coil has a disk cache and it is not enough. Its singleton lives in
 * `FileSystem.SYSTEM_TEMPORARY_DIRECTORY` — `cacheDir` on Android, `NSTemporaryDirectory()`
 * on iOS — where the system is entitled to delete it whenever it wants the space, and it is
 * an LRU besides, so a browse through the feed can evict the recipe somebody pinned for a
 * weekend away. Neither is a fault in Coil: that is what a cache is, and what an offline copy
 * is not.
 *
 * So a pinned picture is downloaded once by the sync and kept here, and [resolve] hands Coil
 * a local path when there is one. Coil registers a mapper for `okio.Path` in its common
 * components, so this needs no fetcher, no second image pipeline and no new dependency — the
 * call sites change from passing a URL to passing whatever this returns.
 *
 * **Names are the URL's path with its slashes flattened**, not its last segment: a recipe's
 * full-size picture and its thumbnail are `recipes/12-v3.webp` and
 * `recipes-thumbnails/12-v3.webp`, which share a last segment and are different pictures. The
 * version is already in the filename — nginx serves these `immutable` for six months on the
 * strength of it — so a changed picture is a new name and can never be confused with the old.
 */
class OfflineImages(
    private val store: OfflineStore,
    private val client: HttpClient,
) {

    /**
     * What to hand Coil for this URL: the local file if it is here, the URL if it is not.
     *
     * Returns `Any` because those are two unrelated types to Coil and it accepts both. A
     * caller never needs to know which it got — that is the point.
     */
    fun resolve(url: String): Any {
        val name = nameFor(url) ?: return url
        val path = store.imagePath(name)
        return if (cached.contains(name)) path else url
    }

    /**
     * The names on disk, read once per sync and consulted by [resolve] on every frame.
     *
     * [resolve] is called from composition, which cannot suspend and must not touch a
     * filesystem — so the answer has to already be in memory. It is refreshed whenever the
     * sync writes or deletes anything, which is the only thing that changes it.
     *
     * **Compose snapshot state, not a plain `var`.** The first frame of an offline launch is
     * drawn before the effect that reads this directory has run, so every picture resolves to
     * a URL that will not load; a plain field would leave them there, because nothing would
     * tell composition the answer had changed. Read inside composition, snapshot state
     * subscribes the caller, and the pictures appear the moment the set is known.
     */
    private var cached: Set<String> by mutableStateOf(emptySet())

    suspend fun refresh() {
        cached = store.imageNames()
    }

    /** Downloads [url] unless it is already here. Silent on failure: a missing picture is a
     * picture that draws its placeholder, which every one of these call sites already has. */
    suspend fun download(url: String) {
        val name = nameFor(url) ?: return
        if (store.hasImage(name)) return
        runCatching {
            val response = client.get(url)
            if (response.status.isSuccess()) store.writeImage(name, response.bodyAsBytes())
        }
    }

    /** Deletes every picture no pinned recipe names any more. Called at the end of a sync. */
    suspend fun retainOnly(urls: Set<String>) {
        val keep = urls.mapNotNull { nameFor(it) }.toSet()
        store.imageNames().forEach { name ->
            if (name !in keep) store.deleteImage(name)
        }
        refresh()
    }

    private fun nameFor(url: String): String? {
        if (!url.startsWith(PREFIX)) return null
        return url.removePrefix(PREFIX).trim('/').replace('/', '_').ifEmpty { null }
    }

    private companion object {
        val PREFIX = ApiClient.IMAGE_URL
    }
}

/** Where a recipe's own pictures live, as [OfflineImages.resolve] and the sync both name them. */
object ImageUrls {
    fun recipeThumbnail(recipeId: Long, version: Long) =
        "${ApiClient.IMAGE_URL}/recipes-thumbnails/$recipeId-v$version.webp"

    fun recipe(recipeId: Long, version: Long) =
        "${ApiClient.IMAGE_URL}/recipes/$recipeId-v$version.webp"

    fun step(stepId: Long, version: Long) =
        "${ApiClient.IMAGE_URL}/recipe-steps/$stepId-v$version.webp"

    fun cookbook(cookbookId: Long, version: Long) =
        "${ApiClient.IMAGE_URL}/cookbooks/$cookbookId-v$version.webp"

    fun user(userId: Long, version: Long) =
        "${ApiClient.IMAGE_URL}/users/$userId-v$version.webp"
}
