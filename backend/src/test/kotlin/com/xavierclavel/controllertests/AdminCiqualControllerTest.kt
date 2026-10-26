package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.searchCiqual
import main.com.xavierclavel.utils.searchCiqualRaw
import org.junit.jupiter.api.Test
import shared.enums.Locale
import kotlin.test.assertEquals

/** The backoffice's search of the bundled Ciqual table; the ranking itself is `CiqualServiceTest`'s. */
class AdminCiqualControllerTest : ApplicationTest() {

    @Test
    fun `the backoffice finds a food with the figures linking would copy`() = runTestAsAdmin {
        val results = client.searchCiqual("tomate", limit = 3)
        assertEquals(3, results.size)
        val average = results.first()
        assertEquals(20385, average.code)
        assertEquals("Tomate sans précision, crue (aliment moyen)", average.name[Locale.FR])
        assertEquals("légumes", average.group[Locale.FR])
        assertEquals(19, average.calories)
        assertEquals(0f, average.saturatedFat)
    }

    @Test
    fun `twenty results unless asked for more`() = runTestAsAdmin {
        assertEquals(20, client.searchCiqual("a").size)
        assertEquals(50, client.searchCiqual("a", limit = 500).size)
    }

    @Test
    fun `the table is closed to regular users`() = runTestAsUser {
        client.searchCiqualRaw("tomate").apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }
}
