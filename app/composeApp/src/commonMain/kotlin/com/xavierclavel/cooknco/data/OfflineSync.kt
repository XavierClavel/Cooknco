package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.RecipeScope
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Brings this phone's copy of the cook's three collections up to date.
 *
 * Three things about how it is arranged are worth keeping:
 *
 * - **It never reports an error.** A sync is best effort by construction: whatever it could
 *   not fetch is simply not pinned, and the index says what it managed. There is no screen
 *   this could fail on that would not be lying — a cook did not ask for this to happen and
 *   cannot do anything about it having not.
 * - **The index is written last.** Everything else can be interrupted — the process dies, the
 *   network goes halfway through — and the worst that leaves is an index that under-claims,
 *   costing a refetch next time. Writing it first would leave one naming recipes whose files
 *   were never written, which is a screen opening on nothing.
 * - **One at a time, and the second is dropped rather than queued.** Launch, sign-in and a
 *   pull-to-refresh can all ask within a second of each other, and a sync starting a second
 *   after one that is still running has nothing new to find. Queueing it would only mean
 *   doing every request twice, a moment later.
 */
class OfflineSync(
    private val recipeApi: RecipeApi,
    private val cookbookApi: CookbookApi,
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

        val own = pagesOf(RecipeScope.OWN, userId, token)
        val liked = pagesOf(RecipeScope.LIKED, userId, token)
        val inCookbooks = pagesOf(RecipeScope.COOKBOOKS, userId, token)

        // A list that came back empty because the request failed is indistinguishable here
        // from one that is genuinely empty, and acting on the second would throw away a
        // perfectly good offline copy. So a sync that reached nothing at all does nothing.
        if (own.isEmpty() && liked.isEmpty() && inCookbooks.isEmpty() &&
            runCatching { recipeApi.listRecipesIn(RecipeScope.OWN, userId, token, 0, 1) }.isFailure
        ) return

        val held = store.readIndex()?.takeIf { it.userId == userId }
        val plan = planSync(own, liked, inCookbooks, held)

        plan.fetch.forEach { id ->
            val recipe = runCatching { recipeApi.getRecipe(id, token) }.getOrNull() ?: return@forEach
            // Fetched with the recipe, not on opening it: a copy with the method but without
            // the cook's own notes is a screen with an empty box where their writing was.
            val notes = runCatching { recipeApi.getNotes(id, token) }.getOrNull()
            val isLiked = runCatching { recipeApi.isLiked(id, token) }.getOrDefault(false)
            store.writeRecipe(OfflineRecipe(recipe = recipe, notes = notes, isLiked = isLiked))
        }

        // The recipe did not change, only its picture did — so the stored copy needs its
        // version moved on and nothing else. That is the request this case exists to save.
        plan.refetchImages.forEach { id ->
            val entry = plan.entries.first { it.id == id }
            val stored = store.readRecipe(id) ?: return@forEach
            store.writeRecipe(stored.copy(recipe = stored.recipe.copy(version = entry.imageVersion)))
        }

        plan.drop.forEach { store.deleteRecipe(it) }

        val cookbooks = syncCookbooks(userId, token)

        // Only what is actually on disk goes into the index, which is why this reads each
        // recipe back rather than trusting the plan. A fetch that failed leaves no file, and an
        // entry claiming it would match its own edition date on the next sync — so the recipe
        // would never be fetched again, and the screen that opened it would find nothing. An
        // index that under-claims costs one refetch; this is the only shape that self-heals.
        val wanted = mutableSetOf<String>()
        val pinned = mutableListOf<OfflineEntry>()
        plan.entries.forEach { entry ->
            val recipe = store.readRecipe(entry.id)?.recipe ?: return@forEach
            pinned += entry
            wanted += pictureUrlsOf(recipe, entry)
        }
        cookbooks.forEach { if (it.version > 0) wanted += ImageUrls.cookbook(it.id, it.version) }
        wanted.forEach { images.download(it) }
        images.retainOnly(wanted)

        store.writeLists(
            OfflineLists(
                own = own.take(OFFLINE_LIST_LIMIT),
                liked = liked.take(OFFLINE_LIST_LIMIT),
                cookbooks = cookbooks,
            )
        )

        store.writeIndex(
            OfflineIndex(
                userId = userId,
                syncedAt = Clock.System.now().epochSeconds,
                recipes = pinned,
                cookbookIds = cookbooks.map { it.id },
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

    /**
     * The cookbooks themselves, and the rows their screens list.
     *
     * One request per cookbook, unlike the recipes: `GET /recipe?cookbookUser=` gets every
     * recipe in every book in one paged query, but a cookbook screen also prints who added
     * each recipe and when, which only `GET /cookbook/{id}/recipes` knows. Cookbooks are
     * counted in tens, so N+1 here costs what the recipes' N+1 would not.
     */
    private suspend fun syncCookbooks(userId: Long, token: String): List<CookbookInfo> {
        val cookbooks = runCatching { cookbookApi.listCookbooks(token, userId) }.getOrNull()
            ?: return store.readLists()?.cookbooks.orEmpty()
        val previous = store.readIndex()?.cookbookIds.orEmpty()
        cookbooks.forEach { cookbook ->
            val recipes = runCatching { cookbookApi.getCookbookRecipes(cookbook.id, token) }
                .getOrNull() ?: return@forEach
            store.writeCookbook(OfflineCookbook(cookbook = cookbook, recipes = recipes))
        }
        (previous - cookbooks.map { it.id }.toSet()).forEach { store.deleteCookbook(it) }
        return cookbooks
    }

    /**
     * Pages one collection until the server runs out or the bound is reached.
     *
     * Deduplicated by id on the way, for the reason the feed and the profile grids both do it:
     * offset paging over a list that is changing repeats rows, and a repeated row here would
     * be a recipe counted twice against the bound.
     */
    private suspend fun pagesOf(scope: RecipeScope, userId: Long, token: String): List<RecipeOverview> {
        val collected = mutableListOf<RecipeOverview>()
        val seen = mutableSetOf<Long>()
        var page = 0
        while (collected.size < OFFLINE_LIST_LIMIT) {
            val items = runCatching { recipeApi.listRecipesIn(scope, userId, token, page, PAGE_SIZE) }
                .getOrNull() ?: break
            items.forEach { if (seen.add(it.id)) collected += it }
            if (items.size < PAGE_SIZE) break
            page++
        }
        return collected.take(OFFLINE_LIST_LIMIT)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
