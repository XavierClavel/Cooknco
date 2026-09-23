package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The decisions a sync makes, taken away from the network and the filesystem.
 *
 * Everything in [planSync] is a judgement about what changed; everything in [OfflineSync] is
 * plumbing that carries it out. This is the half worth testing, and it is testable at all
 * because the two are kept apart.
 */
class SyncPlanTest {

    private fun overview(id: Long, edited: Long = 100, image: Long = 1) = RecipeOverview(
        id = id,
        version = image,
        title = "Recipe $id",
        owner = RecipeOwner(id = 1, version = 0, username = "xavier"),
        likesCount = 0,
        creationDate = 0,
        editionDate = edited,
    )

    private fun index(vararg entries: OfflineEntry) =
        OfflineIndex(userId = 1, syncedAt = 1, recipes = entries.toList())

    @Test
    fun a_recipe_this_phone_has_never_seen_is_fetched() {
        val plan = planSync(own = listOf(overview(1)), liked = emptyList(), cookbook = emptyList(), local = null)

        assertEquals(listOf(1L), plan.fetch)
        assertTrue(plan.drop.isEmpty())
    }

    @Test
    fun a_recipe_that_has_not_changed_is_left_alone() {
        val local = index(OfflineEntry(id = 1, editionDate = 100, imageVersion = 1, sets = setOf(PinSet.OWN)))

        val plan = planSync(listOf(overview(1)), emptyList(), emptyList(), local)

        assertTrue(plan.fetch.isEmpty(), "nothing moved, so nothing is worth a request")
        assertTrue(plan.refetchImages.isEmpty())
    }

    @Test
    fun an_edited_recipe_is_fetched_whole() {
        val local = index(OfflineEntry(id = 1, editionDate = 100, imageVersion = 1, sets = setOf(PinSet.OWN)))

        val plan = planSync(listOf(overview(1, edited = 200)), emptyList(), emptyList(), local)

        assertEquals(listOf(1L), plan.fetch)
    }

    /**
     * The case the whole `editionDate` change exists for: a new photograph is not an edit, and
     * refetching the recipe to discover its method is unchanged is a request for nothing.
     */
    @Test
    fun a_new_picture_alone_does_not_refetch_the_recipe() {
        val local = index(OfflineEntry(id = 1, editionDate = 100, imageVersion = 1, sets = setOf(PinSet.OWN)))

        val plan = planSync(listOf(overview(1, image = 2)), emptyList(), emptyList(), local)

        assertTrue(plan.fetch.isEmpty(), "the recipe did not change")
        assertEquals(listOf(1L), plan.refetchImages)
    }

    @Test
    fun a_recipe_that_has_left_every_list_is_dropped() {
        val local = index(OfflineEntry(id = 1, editionDate = 100, sets = setOf(PinSet.LIKED)))

        val plan = planSync(emptyList(), emptyList(), emptyList(), local)

        assertEquals(listOf(1L), plan.drop)
    }

    /**
     * A recipe is commonly in more than one list, and unliking one of your own must not throw
     * away the copy your cookbook still wants. The sets are what remembers that.
     */
    @Test
    fun a_recipe_in_two_lists_is_fetched_once_and_kept_by_either() {
        val plan = planSync(
            own = listOf(overview(1)),
            liked = listOf(overview(1)),
            cookbook = emptyList(),
            local = null,
        )

        assertEquals(listOf(1L), plan.fetch, "one recipe, one request")
        assertEquals(setOf(PinSet.OWN, PinSet.LIKED), plan.entries.single().sets)

        val afterUnliking = planSync(listOf(overview(1)), emptyList(), emptyList(), index(plan.entries.single()))
        assertTrue(afterUnliking.drop.isEmpty(), "still their own recipe")
        assertEquals(setOf(PinSet.OWN), afterUnliking.entries.single().sets)
    }

    /** Likes are the one list with no natural ceiling, so the bound is applied per list. */
    @Test
    fun each_list_is_cut_to_the_bound_on_its_own() {
        val liked = (1L..(OFFLINE_LIST_LIMIT + 50L)).map { overview(it) }
        val own = (10_000L..10_010L).map { overview(it) }

        val plan = planSync(own, liked, emptyList(), null)

        assertEquals(OFFLINE_LIST_LIMIT + own.size, plan.entries.size)
        assertTrue(own.all { recipe -> plan.entries.any { it.id == recipe.id } },
            "a cook with six hundred likes still keeps every recipe they wrote")
    }

    /**
     * A recipe the plan asked for but the sync could not fetch must not end up in the index.
     *
     * It is the plan that says what *should* be held; only the files say what *is*. An entry
     * claiming a recipe with no file behind it matches its own edition date on the next sync,
     * so the recipe is never fetched again and the screen that opens it finds nothing. This is
     * the shape [OfflineSync] relies on: the index is built from what was read back, so a
     * failed fetch costs one refetch and repairs itself.
     */
    @Test
    fun the_plan_says_what_should_be_held_and_the_index_says_what_is() {
        val plan = planSync(listOf(overview(1), overview(2)), emptyList(), emptyList(), null)
        assertEquals(listOf(1L, 2L), plan.fetch)

        // Recipe 2's fetch failed, so only 1 was written and only 1 may be claimed.
        val written = plan.entries.filter { it.id == 1L }
        val next = planSync(listOf(overview(1), overview(2)), emptyList(), emptyList(), index(*written.toTypedArray()))

        assertEquals(listOf(2L), next.fetch, "the one that failed is asked for again")
        assertTrue(next.drop.isEmpty())
    }

    /**
     * A backend that predates `editionDate` sends zero, which reads as "never edited". Every
     * recipe is then fetched once and never refetched — which is the safe way round: a copy
     * that is never refreshed beats one that is thrown away and refetched on every sync.
     */
    @Test
    fun a_backend_without_edition_dates_settles_rather_than_churning() {
        val first = planSync(listOf(overview(1, edited = 0)), emptyList(), emptyList(), null)
        assertEquals(listOf(1L), first.fetch)

        val second = planSync(listOf(overview(1, edited = 0)), emptyList(), emptyList(), index(*first.entries.toTypedArray()))
        assertTrue(second.fetch.isEmpty())
    }
}
