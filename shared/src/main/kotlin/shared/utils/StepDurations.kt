package shared.utils

/**
 * Reads a step's own wording for how long it takes — "laisser reposer 30 mn", "bake for
 * 1h30", "simmer 20 minutes".
 *
 * Best effort, deliberately. A step with no stated duration gets no timer, which is the
 * right answer for nearly every step; a step whose wording this cannot parse gets no timer
 * either, and the cook sets one by hand. It is never *wrong*, only sometimes absent — which
 * is why nothing downstream treats a null as a failure.
 *
 * Both units are added when both appear, so "1h30" is ninety minutes rather than one hour.
 * Seconds out, because seconds are what a countdown counts.
 *
 * **There are three copies of this rule**, and they are meant to agree: here, for the
 * backend and anything writing recipes through `/mcp`; `StepDurations` in the mobile app,
 * which has its own DTOs and does not depend on this module; and `stepDurationSeconds` in
 * the web app. A change to the wording it understands belongs in all three.
 */
object StepDurations {

    private val hours = Regex("""(\d+)\s*(?:h|hours?|heures?)\b""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(\d+)\s*(?:mn|min(?:ute)?s?)\b""", RegexOption.IGNORE_CASE)

    fun parseSeconds(step: String?): Int? {
        if (step.isNullOrBlank()) return null
        var total = 0
        var found = false
        hours.find(step)?.let { total += it.groupValues[1].toInt() * 3600; found = true }
        minutes.find(step)?.let { total += it.groupValues[1].toInt() * 60; found = true }
        return total.takeIf { found && total > 0 }
    }
}
