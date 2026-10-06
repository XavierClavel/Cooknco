package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.PhotoImportOutcome

/**
 * What the backoffice photo-import tab edits: the allowance, the budget, and the prices that
 * turn tokens into spend. Money travels as a plain number in [currency]; it is stored exactly
 * and shown rounded, and nothing a browser computes from it decides anything.
 */
@Serializable
data class PhotoImportSettingsDTO(
    val dailyLimitPerUser: Int,
    /** Null for no ceiling. */
    val monthlyBudget: Double? = null,
    val inputPricePerMillion: Double = 0.0,
    val outputPricePerMillion: Double = 0.0,
    val currency: String = "EUR",
)

/** Everything the photo-import tab shows, in one read. Dates are epoch seconds, UTC. */
@Serializable
data class AdminPhotoImportOverview(
    val provider: Provider,
    val settings: PhotoImportSettingsDTO,
    /** False while the defaults are in force, which the tab says, as the releases tab does. */
    val settingsSaved: Boolean,
    val settingsUpdatedAt: Long? = null,
    val month: Usage,
    val today: Usage,
    /** One entry per UTC day, oldest first, the last being today. Days with nothing included. */
    val days: List<Day>,
    /** This month's heaviest accounts, by spend and then by imports. */
    val topUsers: List<UserUsage>,
    val recent: List<Entry>,
) {
    /** What the backend reads with. Never the key: an admin session is not a reason to see it. */
    @Serializable
    data class Provider(
        val configured: Boolean,
        val baseUrl: String,
        val model: String,
    )

    @Serializable
    data class Usage(
        /** Where the period starts. */
        val since: Long,
        val imports: Int,
        val inputTokens: Long,
        val outputTokens: Long,
        val cost: Double,
    )

    @Serializable
    data class Day(
        /** `YYYY-MM-DD`. */
        val date: String,
        val imports: Int,
        val inputTokens: Long,
        val outputTokens: Long,
        val cost: Double,
    )

    @Serializable
    data class UserUsage(
        /** Null for an account that has since been deleted: its spend stays, its name does not. */
        val userId: Long? = null,
        val username: String? = null,
        val imports: Int,
        val importsToday: Int,
        val inputTokens: Long,
        val outputTokens: Long,
        val cost: Double,
    )

    @Serializable
    data class Entry(
        val id: Long,
        val createdAt: Long,
        val userId: Long? = null,
        val username: String? = null,
        val pages: Int,
        val model: String,
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val cost: Double,
        val outcome: PhotoImportOutcome,
    )
}
