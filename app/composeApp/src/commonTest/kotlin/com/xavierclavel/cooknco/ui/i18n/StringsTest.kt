package com.xavierclavel.cooknco.ui.i18n

import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.network.ReportReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * That the catalogue actually names the things screens ask it to name.
 *
 * Every word the app says lives in [Strings], once per language. What this guards is the
 * gap that keeps reopening: a screen prettifying a server constant itself instead of asking
 * — which is how "2 tablespoons sugar" got into a French recipe, and later how a French
 * recipe came to be headed "MAIN DISH". A constant that reaches a screen unnamed looks like
 * a label rather than like a bug, so nothing else catches it.
 */
class StringsTest {

    /** What the editor offers, and therefore everything a recipe can come back carrying. */
    private val dishClasses =
        listOf("ENTREE", "MAIN_DISH", "DESERT", "SALTY_SNACK", "SUGARY_SNACK", "DRINK", "OTHER")

    private val catalogues = mapOf("EN" to EnStrings, "FR" to FrStrings)

    @Test
    fun `every dish class is named in every language, and never as its constant`() {
        catalogues.forEach { (language, s) ->
            dishClasses.forEach { value ->
                val name = s.dishClassName(value)
                assertFalse(name.contains('_'), "$language leaves $value as its constant: $name")
                // Case-sensitive on purpose: "Drink" is a perfectly good name for DRINK,
                // and only the untouched constant is a bug
                assertNotEquals(value, name, "$language does not name $value, it echoes it")
                assertTrue(name.isNotBlank(), "$language names $value as nothing")
            }
        }
    }

    @Test
    fun `the two languages disagree about a dish class, which is the point of having two`() {
        assertEquals("Main dish", EnStrings.dishClassName("MAIN_DISH"))
        assertEquals("Plat principal", FrStrings.dishClassName("MAIN_DISH"))
    }

    @Test
    fun `a dish class the app has never heard of still reads as a word`() {
        catalogues.forEach { (language, s) ->
            val name = s.dishClassName("SOMETHING_NEW")
            assertFalse(name.contains('_'), "$language passes an unknown class through: $name")
            assertTrue(name.isNotBlank(), "$language names an unknown class as nothing")
        }
    }

    @Test
    fun `every reason to report something is named in every language, and never as its constant`() {
        // The moderation queue groups on these, so the set is the backend's and closed. A
        // reason nobody can read is one nobody picks, which quietly skews what moderators see.
        catalogues.forEach { (language, s) ->
            ReportReason.entries.forEach { reason ->
                val name = s.reportReasonName(reason)
                assertFalse(name.contains('_'), "$language leaves $reason as its constant: $name")
                assertNotEquals(reason.value, name, "$language does not name $reason, it echoes it")
                assertTrue(name.isNotBlank(), "$language names $reason as nothing")
            }
        }
    }

    @Test
    fun `the two languages disagree about a reason, which is the point of having two`() {
        assertNotEquals(
            EnStrings.reportReasonName(ReportReason.HARASSMENT),
            FrStrings.reportReasonName(ReportReason.HARASSMENT),
        )
    }

    @Test
    fun `every unit is named in every language, and never as its constant`() {
        // The same rule for the other table a screen used to keep its own copy of
        catalogues.forEach { (language, s) ->
            UnitRepository.DEFAULT_UNITS.map { it.name }.forEach { value ->
                val name = s.unitName(value)
                assertFalse(name.contains('_'), "$language leaves $value as its constant: $name")
                assertTrue(name.isNotBlank(), "$language names $value as nothing")
            }
        }
    }
}
