package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.RecipeFilter
import shared.dto.OfflineSyncDTO
import shared.enums.Locale
import shared.enums.OfflinePinSet
import shared.enums.Sort
import shared.infodto.OfflineNotes
import shared.infodto.OfflinePin
import shared.infodto.OfflineSyncInfo
import shared.overviewdto.RecipeOverview

/**
 * Brings a phone's offline copy of an account's recipes up to date in one request.
 *
 * It replaces the app reading its three collections page by page and then each recipe, its
 * notes and its like one request at a time — dozens of requests on an ordinary launch and well
 * over a thousand on a first one, which ran into the API's per-client rate limit and had the
 * cook's own taps refused while the sync used up the budget.
 *
 * Three things about how it is arranged are worth keeping:
 *
 * - **It compares state, it does not replay a log.** The phone says what it holds and at which
 *   revision; the server works out what it should hold, from the very listings the profile and
 *   the cookbooks tab read, and answers with the difference. A recipe leaves a phone for reasons
 *   that happen to other people's rows — its author deletes it, a moderator hides it, the author
 *   goes private — and none of those places knows a phone is holding it. A cursor over a change
 *   log would need each of them to write a tombstone for every account holding the recipe; a
 *   comparison needs nothing written anywhere (pull, not push).
 * - **What changed is told by [com.xavierclavel.models.Recipe.revision]**, which every write to
 *   a recipe's content moves, pictures included — not by its modification date, which only the
 *   editor's save moved.
 * - **The recipes come a page at a time, the rest every time.** A first sync can owe a phone six
 *   hundred recipes, and one answer carrying them all is megabytes a dropped connection throws
 *   away. The lists, the notes and the cookbooks are small and are what the phone's index is
 *   written from, so every answer carries them whole.
 */
class OfflineSyncService : KoinComponent {
    private val recipeService: RecipeService by inject()
    private val cookbookService: CookbookService by inject()
    private val likeService: LikeService by inject()
    private val recipeNotesService: RecipeNotesService by inject()

    fun sync(userId: Long, request: OfflineSyncDTO, locale: Locale): OfflineSyncInfo {
        // Three lists' worth, with room to spare: a phone holding more than this is not one the
        // server ever filled, and the comparison below is linear in what it is sent.
        if (request.held.size > MAX_HELD) throw BadRequestException(BadRequestCause.INVALID_REQUEST)

        val own = listed(userId, RecipeFilter(user = userId))
        val liked = listed(userId, RecipeFilter(likedBy = userId))
        val inCookbooks = listed(userId, RecipeFilter(cookbookUser = userId))

        // Own first, then the cookbooks, then the likes: the order a sync cut short leaves a
        // phone with the recipes most likely to be cooked from.
        val pins = linkedMapOf<Long, Pin>()
        fun pin(list: List<RecipeOverview>, set: OfflinePinSet) = list.forEach { overview ->
            pins.getOrPut(overview.id) { Pin(overview.revision) }.sets += set
        }
        pin(own, OfflinePinSet.OWN)
        pin(inCookbooks, OfflinePinSet.COOKBOOK)
        pin(liked, OfflinePinSet.LIKED)

        val held = request.held.associate { it.id to it.revision }
        val stale = pins.filter { (id, pin) -> held[id] != pin.revision }.keys.toList()
        val page = stale.take(RECIPES_PER_RESPONSE)
        val likedIds = likeService.likedAmong(userId, pins.keys)
        val whole = recipeService.findWhole(userId, page, locale).associateBy { it.id }

        return OfflineSyncInfo(
            pinned = pins.map { (id, pin) ->
                OfflinePin(id = id, revision = pin.revision, sets = pin.sets, isLiked = id in likedIds)
            },
            // In the pinned order, which is the one worth keeping if the phone stops reading.
            recipes = page.mapNotNull { whole[it] },
            hasMore = stale.size > page.size,
            notes = recipeNotesService.notesOf(userId, pins.keys)
                .map { (recipeId, notes) -> OfflineNotes(recipeId, notes) },
            own = own,
            liked = liked,
            cookbooks = cookbookService.findWithRecipes(userId),
        )
    }

    /** One of the three collections, as `GET /recipe` serves it to the app's profile. */
    private fun listed(userId: Long, filter: RecipeFilter): List<RecipeOverview> =
        recipeService.findList(userId, Paging.of(0, LIST_LIMIT), Sort.DATE_DESCENDING, filter)

    private class Pin(val revision: Long, val sets: MutableSet<OfflinePinSet> = mutableSetOf())

    companion object {
        /**
         * The most recent recipes each collection keeps on a phone. Per collection rather than
         * overall, so a cook with six hundred likes still keeps every recipe they wrote — likes
         * are the one set with no natural ceiling.
         */
        const val LIST_LIMIT = 200

        /** Recipes per answer. A first sync of a full phone is six of them. */
        const val RECIPES_PER_RESPONSE = 100

        const val MAX_HELD = 3 * LIST_LIMIT * 2
    }
}
