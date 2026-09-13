package com.xavierclavel.cooknco.data

/**
 * Reads a step's own wording for how long it takes — "laisser reposer 30 mn", "bake for
 * 1h30", "simmer 20 minutes".
 *
 * Best effort, deliberately. A step with no stated duration gets no timer, which is the
 * right answer for nearly every step; a step whose wording this cannot parse gets no timer
 * either, and the cook sets one by hand in the editor. It is never *wrong*, only sometimes
 * absent — which is why nothing treats a null as a failure.
 *
 * Both units are added when both appear, so "1h30" is ninety minutes rather than one hour.
 *
 * This used to live in `CookModeViewModel`, parsing the step at the moment a cook wanted a
 * timer. It moved here when a step gained a duration of its own: the parse now happens once,
 * in the editor, where the author can see the answer and correct it — and cook mode reads
 * what was saved rather than guessing again at the stove. It still runs at cook time for
 * recipes written before the field existed.
 *
 * **The backend has the same rule** (`shared.utils.StepDurations`), for recipes written
 * through `/mcp`. The two are meant to agree; a change to the wording this understands
 * belongs in both. The web editor has no copy: it neither shows nor sets a step's timer, it
 * only carries one that is already there.
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
