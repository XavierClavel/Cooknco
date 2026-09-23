package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.RecipeOverview

/**
 * What a sync has to do, worked out from two lists and nothing else.
 *
 * Pure on purpose: this is the part with all the decisions in it, and it is the part a test
 * can reach without a network, a filesystem or a clock. [OfflineSync] is then only the
 * plumbing that carries it out.
 */
data class SyncPlan(
    /** Recipes to fetch whole: new to this phone, or edited since it last looked. */
    val fetch: List<Long> = emptyList(),
    /**
     * Recipes whose *picture* moved while their text did not.
     *
     * Separate from [fetch] because the two move independently and the cheap case is much the
     * commoner one: re-downloading a photograph costs a request, refetching the recipe to
     * discover the method is unchanged costs another for nothing.
     */
    val refetchImages: List<Long> = emptyList(),
    /** Recipes in none of the three lists any more: deleted, unliked, or a cookbook left. */
    val drop: List<Long> = emptyList(),
    /** What the index becomes once the plan has been carried out. */
    val entries: List<OfflineEntry> = emptyList(),
)

/**
 * The most recent recipes each list may keep on the phone.
 *
 * Own recipes and cookbook recipes rarely come near it — they are bounded by what somebody
 * wrote and what they joined. Likes are the set with no natural ceiling: liking is one tap,
 * and a few hundred is an ordinary number for somebody who has used the app for a year.
 * Applied per list rather than overall so a cook with six hundred likes still keeps every
 * recipe they wrote.
 */
const val OFFLINE_LIST_LIMIT = 200

/**
 * Decides what to fetch, what to re-photograph and what to forget.
 *
 * Each list arrives in the order the server sent it — most recent first — and is cut to
 * [OFFLINE_LIST_LIMIT] here rather than by the caller, so the bound is one rule in one place
 * and the caller can page until it runs out without knowing about it.
 *
 * A recipe in several lists is fetched once and dropped only when it has left all of them,
 * which is what [OfflineEntry.sets] is for.
 */
fun planSync(
    own: List<RecipeOverview>,
    liked: List<RecipeOverview>,
    cookbook: List<RecipeOverview>,
    local: OfflineIndex?,
): SyncPlan {
    val sets = mutableMapOf<Long, MutableSet<PinSet>>()
    val remote = mutableMapOf<Long, RecipeOverview>()

    fun take(list: List<RecipeOverview>, set: PinSet) {
        list.take(OFFLINE_LIST_LIMIT).forEach { overview ->
            sets.getOrPut(overview.id) { mutableSetOf() } += set
            // First writer wins: the same recipe in two lists is the same recipe, and the
            // overviews only differ in fields the index does not read.
            remote.getOrPut(overview.id) { overview }
        }
    }

    take(own, PinSet.OWN)
    take(liked, PinSet.LIKED)
    take(cookbook, PinSet.COOKBOOK)

    val held = local?.byId.orEmpty()
    val fetch = mutableListOf<Long>()
    val refetchImages = mutableListOf<Long>()
    val entries = mutableListOf<OfflineEntry>()

    remote.values.forEach { overview ->
        val entry = OfflineEntry(
            id = overview.id,
            editionDate = overview.editionDate,
            imageVersion = overview.version,
            sets = sets[overview.id].orEmpty(),
        )
        entries += entry
        val previous = held[overview.id]
        when {
            // Never held, or held from before the recipe was last edited.
            previous == null || previous.editionDate != overview.editionDate -> fetch += overview.id
            previous.imageVersion != overview.version -> refetchImages += overview.id
        }
    }

    return SyncPlan(
        fetch = fetch,
        refetchImages = refetchImages,
        drop = held.keys.filterNot { it in remote },
        entries = entries,
    )
}
