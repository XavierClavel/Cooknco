package main.com.xavierclavel.utils

import com.xavierclavel.ApplicationTest
import io.ebean.test.LoggedSql
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Base class for fetch-plan tests: the ones that assert *how many* queries a read path costs, so an
 * N+1 fails the build instead of reaching production.
 *
 * Nothing else here enforces it. The repository's rule — fetch the associations the mapping code
 * reads, up front — is a convention, unlike `backend-zourite-api`, which fails the build on it with
 * custom detekt rules. These tests are what holds it, one read path at a time.
 *
 * Measuring works because Ebean logs every statement to `io.ebean.SQL` and `ebean-test` swaps that
 * logger for one that can be captured ([LoggedSql]). The capture is a plain static, not a
 * thread-local, so it sees the SQL of a request served on a Ktor worker as readily as a service call
 * made from the test thread — which is also why a test class doing this must not run in parallel
 * with another.
 *
 * The assertion is that a read path costs the same at few rows and at many — never an exact number.
 * An N+1 is precisely a count that grows with the rows, so the comparison says what is meant and
 * survives an unrelated change elsewhere in the fetch plan.
 *
 * ```kotlin
 * assertQueryCountDoesNotGrow(
 *     what = "RecipeService.findList",
 *     read = { recipeService.findList(me, paging, Sort.NONE, RecipeFilter()) },
 *     grow = { repeat(2) { client.createRecipe(recipeDTO) } },
 * )
 * ```
 *
 * The stronger form used elsewhere — load the rows, then require the *mapping* to cost nothing — has
 * no place here: the services return DTOs, so there is no loaded-but-unmapped state to measure
 * between. Which is also why the guards sit on service calls rather than on HTTP requests.
 *
 * What this shape does **not** guard is anything Ebean batch-loads, because the cost is then the
 * same at one row and at three. That is every lazy `-to-one`, batched by `lazyLoadBatchSize` (100 by
 * default): `Recipe.owner` costs one query whether the list holds one recipe or twenty, so dropping
 * `.owner.fetch()` would not show up here at all. Collections are the opposite — every one measured
 * in this codebase lazy-loads one un-batched query per parent, and a paged query fetches none of
 * them however the fetch plan asks. So measure the relation rather than reasoning from `@OneToMany`
 * vs `@ManyToOne`, and read `utils/GroupedCounts.kt` before trying to fix one with a fetch path.
 *
 * The read also has to be checked to have produced rows at all, or the comparison passes vacuously
 * on two empty results. The assertions do that themselves, from what the measured block returns.
 */
abstract class FetchPlanTest : ApplicationTest() {

    /**
     * Runs [block] and returns its result alongside the SQL Ebean issued while it ran.
     *
     * Notification fan-out is drained first. A dispatch deliberately outlives the request that
     * caused it (`NotificationService.awaitDispatches`), so one still running from the fixture setup
     * would have its inserts and its audience lookups counted against the read being measured — and
     * land in the count unpredictably, which is worse than landing in it always.
     */
    protected suspend fun <T> recordSql(block: suspend () -> T): SqlRecording<T> {
        notificationService.awaitDispatches()
        LoggedSql.start()
        val result = runCatching { block() }
        val statements = LoggedSql.stop()
        return SqlRecording(result.getOrThrow(), statements)
    }

    /**
     * Requires a read path to cost the same after [grow] has added rows to what it returns.
     *
     * @param what names the read path, for the failure message
     * @param read the read path being measured, returning what it produced
     * @param grow adds rows to it — enough that one query per row is unmistakable, and measured
     *   outside both windows so that setting the fixtures up is never counted as part of the read
     */
    protected suspend fun assertQueryCountDoesNotGrow(
        what: String,
        read: suspend () -> Collection<*>,
        grow: suspend () -> Unit,
    ) {
        val forFew = recordSql(read)
        grow()
        val forMany = recordSql(read)
        assertSameQueryCount(what, forFew, forMany)
    }

    /**
     * Requires two reads of the same path, over a small subject and a larger one, to cost the same.
     *
     * For what does not grow by rows being added to the database — a recipe's own ingredient list,
     * say, which is fixed when the recipe is saved — so the two subjects are set up in advance and
     * read once each.
     */
    protected suspend fun assertSameQueryCount(
        what: String,
        forFew: suspend () -> Collection<*>,
        forMany: suspend () -> Collection<*>,
    ) = assertSameQueryCount(what, recordSql(forFew), recordSql(forMany))

    private fun assertSameQueryCount(
        what: String,
        forFew: SqlRecording<out Collection<*>>,
        forMany: SqlRecording<out Collection<*>>,
    ) {
        // Without this the comparison passes on two empty results, which is the one way a fetch-plan
        // test can be green while measuring nothing at all
        assertTrue(
            forMany.result.size > forFew.result.size,
            "$what returned ${forFew.result.size} rows and then ${forMany.result.size}: " +
                "there is no growth here to measure the cost of",
        )
        assertEquals(
            forFew.statements.size,
            forMany.statements.size,
            "$what costs ${forFew.statements.size} queries for ${forFew.result.size} rows and " +
                "${forMany.statements.size} for ${forMany.result.size} — it scales with the rows.\n" +
                "At ${forMany.result.size} rows:\n${describe(forMany.statements)}",
        )
    }

    /** The result of [recordSql], holding what the block returned and the SQL it cost. */
    protected data class SqlRecording<T>(
        val result: T,
        val statements: List<String>,
    )

    /**
     * The captured SQL, one statement per line and short enough to read in a build log. The bind
     * values are kept: on an N+1 they are the same statement repeated with a different id, and
     * seeing the ids is what identifies which relation is loading per row.
     */
    private fun describe(statements: List<String>): String =
        statements.joinToString("\n") { "  - " + it.replace(Regex("\\s+"), " ").take(200) }
}
