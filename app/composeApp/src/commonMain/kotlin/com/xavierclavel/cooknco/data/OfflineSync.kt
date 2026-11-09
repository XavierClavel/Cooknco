package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.HeldRecipe
import com.xavierclavel.cooknco.network.dto.OfflineSyncResponse
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Brings this phone's copy of the cook's three collections up to date.
 *
 * Four things about how it is arranged are worth keeping:
 *
 * - **One request, not one per recipe.** The phone says what it holds and at which revision
 *   (`POST /offline/sync`), and the server answers with what it should hold and the recipes
 *   it is missing or has out of date. This used to read the three collections page by page
 *   and then every new or edited recipe, its notes and its like one request at a time — dozens
 *   of requests at every launch and well over a thousand at a first one, which ran into the
 *   API's per-client rate limit and had the cook's own taps refused while it ran. Which
 *   recipes to hold is now the server's to say, from the same listings and visibility rules
 *   the screens read, so nothing here decides it.
 * - **It never reports an error.** A sync is best effort by construction: whatever it could
 *   not fetch is simply not pinned, and the index says what it managed. There is no screen
 *   this could fail on that would not be lying — a cook did not ask for this to happen and
 *   cannot do anything about it having not.
 * - **The index is written last.** Everything else can be interrupted — the process dies, the
 *   network goes halfway through a first sync's pages — and the worst that leaves is an index
 *   that under-claims, costing a refetch next time. Writing it first would leave one naming
 *   recipes whose files were never written, which is a screen opening on nothing.
 * - **One at a time, and the second is dropped rather than queued.** Launch, sign-in and a
 *   pull-to-refresh can all ask within a second of each other, and a sync starting a second
 *   after one that is still running has nothing new to find. Queueing it would only mean
 *   doing every request twice, a moment later.
 */
class OfflineSync(
    private val recipeApi: RecipeApi,
    private val tokenDataStore: TokenDataStore,
    private val store: OfflineStore,
    private val images: OfflineImages,
    private val preferences: DevicePreferences,
) {

    private val mutex = Mutex()

    /**
     * Syncs, unless one is already running or the cook has turned the whole thing off.
     *
     * Returns quietly either way. Callers are launch, sign-in and the refresh gestures, none
     * of which has anything to say about the outcome.
     */
    suspend fun sync(userId: Long) {
        if (!preferences.offlineRecipes.first()) return
        if (mutex.isLocked) return
        mutex.withLock { runCatching { run(userId) } }
    }

    private suspend fun run(userId: Long) {
        val token = tokenDataStore.tokenFlow.first() ?: return
        // A mismatch means the copies are somebody else's, which counts as holding nothing.
        val previous = store.readIndex()?.takeIf { it.userId == userId }

        // What is on this phone, by the revision each copy was read at — which is all the
        // server needs to work out what to send. It grows as the pages arrive, so each page
        // asks only for what is still missing.
        val held = previous?.recipes.orEmpty().associateTo(mutableMapOf()) { it.id to it.revision }

        var answer: OfflineSyncResponse? = null
        for (page in 0 until MAX_PAGES) {
            val next = runCatching {
                recipeApi.syncOffline(token, held.map { (id, revision) -> HeldRecipe(id, revision) })
            }.getOrNull() ?: break
            answer = next
            val pins = next.pinned.associateBy { it.id }
            val notes = next.notes.associate { it.recipeId to it.notes }
            next.recipes.forEach { recipe ->
                store.writeRecipe(
                    OfflineRecipe(recipe = recipe, notes = notes[recipe.id], isLiked = pins[recipe.id]?.isLiked ?: false)
                )
                // The recipe's own revision, not the pin's: it was read with the content, and
                // the pin's can be a moment older.
                held[recipe.id] = recipe.revision
            }
            // An answer that promises more and brings nothing would loop for ever.
            if (!next.hasMore || next.recipes.isEmpty()) break
        }
        // An unreachable server is not an account with nothing in it: a sync that got no
        // answer at all leaves a perfectly good copy exactly as it was.
        val last = answer ?: return

        val pinnedIds = last.pinned.map { it.id }.toSet()
        // Unliked, taken out of a cookbook, deleted, hidden, out of reach: the server does not
        // say which, only that the account no longer keeps it.
        held.keys.filterNot { it in pinnedIds }.forEach { store.deleteRecipe(it) }

        val notes = last.notes.associate { it.recipeId to it.notes }
        val entries = mutableListOf<OfflineEntry>()
        val wanted = mutableSetOf<String>()
        last.pinned.forEach { pin ->
            // Only what is actually on disk goes into the index, which is why each copy is read
            // back rather than the answer trusted. A recipe that never arrived — a page that
            // failed — has no file, and an entry claiming it would leave the screen opening it
            // with nothing. An index that under-claims costs one refetch; this is the only
            // shape that heals itself.
            val stored = store.readRecipe(pin.id) ?: return@forEach
            val pinNotes = notes[pin.id]
            // The notes and the like are the cook's own and change without the recipe doing
            // so, which no revision follows — so a copy that is otherwise current is corrected
            // where it stands.
            if (stored.notes != pinNotes || stored.isLiked != pin.isLiked) {
                store.writeRecipe(stored.copy(notes = pinNotes, isLiked = pin.isLiked))
            }
            val entry = OfflineEntry(
                id = pin.id,
                revision = held[pin.id],
                sets = pin.sets.mapNotNull { name -> PinSet.entries.firstOrNull { it.name == name } }.toSet(),
            )
            entries += entry
            wanted += pictureUrlsOf(stored.recipe, entry)
        }

        val cookbooks = last.cookbooks.map { OfflineCookbook(cookbook = it.cookbook, recipes = it.recipes) }
        cookbooks.forEach { store.writeCookbook(it) }
        val cookbookIds = cookbooks.map { it.cookbook.id }
        (previous?.cookbookIds.orEmpty() - cookbookIds.toSet()).forEach { store.deleteCookbook(it) }
        cookbooks.forEach { if (it.cookbook.version > 0) wanted += ImageUrls.cookbook(it.cookbook.id, it.cookbook.version) }

        wanted.forEach { images.download(it) }
        images.retainOnly(wanted)

        store.writeLists(
            OfflineLists(
                own = last.own,
                liked = last.liked,
                cookbooks = cookbooks.map { it.cookbook },
            )
        )

        store.writeIndex(
            OfflineIndex(
                userId = userId,
                syncedAt = Clock.System.now().epochSeconds,
                recipes = entries,
                cookbookIds = cookbookIds,
            )
        )
        // Only now: everything above may have written or deleted a file, and [resolve] reads
        // this set from memory on every frame.
        images.refresh()
    }

    /**
     * Which of a recipe's pictures are worth the bytes.
     *
     * Thumbnail and hero for anything pinned — those are what the grids and the recipe screen
     * draw. **Step pictures only for the cook's own recipes and their cookbooks'**: a step
     * picture is a cooking instruction, and those are the recipes somebody actually stands
     * over a pan with, while a few hundred liked recipes' worth of them is most of the storage
     * this feature would ever take.
     *
     * A version of zero means the entity has no picture of its own. The server answers those
     * with the bucket's default image, so downloading them would be the same handful of bytes
     * saved a few hundred times under a few hundred names. They are skipped, and the
     * placeholder every one of these composables already draws behind the picture shows
     * through instead.
     */
    private fun pictureUrlsOf(recipe: RecipeInfo, entry: OfflineEntry): List<String> = buildList {
        if (recipe.version > 0) {
            add(ImageUrls.recipeThumbnail(recipe.id, recipe.version))
            add(ImageUrls.recipe(recipe.id, recipe.version))
        }
        if (PinSet.OWN in entry.sets || PinSet.COOKBOOK in entry.sets) {
            recipe.steps.forEach { step ->
                val id = step.id
                if (id != null && step.imageVersion > 0) add(ImageUrls.step(id, step.imageVersion))
            }
        }
    }

    private companion object {
        /**
         * Pages a sync reads at most. The server bounds a full phone at six (three collections
         * of two hundred, a hundred recipes a page), so this only stops a server that keeps
         * saying there is more.
         */
        const val MAX_PAGES = 12
    }
}
