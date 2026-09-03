package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.TimeGranularity

/**
 * One time bucket of platform activity.
 *
 * [bucket] is the ISO date the bucket starts on. Empty buckets are present with
 * zero counts, so a chart never has to guess at a gap.
 */
@Serializable
data class AdminTrendPoint(
    val bucket: String,
    val newUsers: Int,
    val newRecipes: Int,
    val newReports: Int,
    /** Running total of accounts at the end of the bucket. */
    val totalUsers: Int,
    /** Running total of recipes at the end of the bucket. */
    val totalRecipes: Int,
)

/**
 * Activity over time for the backoffice dashboard.
 *
 * [previous] holds the same metrics for the window immediately before [points],
 * so each chart can show a period-over-period delta without a second request.
 */
@Serializable
data class AdminTrends(
    val granularity: TimeGranularity,
    val points: List<AdminTrendPoint>,
    val previous: AdminTrendTotals,
) {
    @Serializable
    data class AdminTrendTotals(
        val newUsers: Int,
        val newRecipes: Int,
        val newReports: Int,
    )
}
