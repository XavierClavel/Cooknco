package shared.dto

import kotlinx.serialization.Serializable

/**
 * What an operator grants when they make an account premium.
 *
 * The two forms are exclusive on purpose and neither is defaulted into: [forever] false
 * with no [until] is refused rather than read as "premium starting now and ending never",
 * because a form that fails to send its date would otherwise hand out a permanent grant
 * nobody chose. [until] is ignored when [forever] is set, which is the one combination a
 * UI can produce by accident — a date left in a field after the toggle was flipped.
 *
 * @param until epoch seconds, UTC, like every other date this API carries.
 */
@Serializable
data class PremiumGrantDTO(
    val forever: Boolean = false,
    val until: Long? = null,
)
