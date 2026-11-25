package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.services.OpenPricesService
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.searchOpenPrices
import main.com.xavierclavel.utils.searchOpenPricesRaw
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The backoffice's search of the bundled Open Prices table; the ranking itself is `OpenPricesServiceTest`'s. */
class AdminOpenPricesControllerTest : ApplicationTest() {

    @Test
    fun `the backoffice finds a category with the price linking would give`() = runTestAsAdmin {
        val results = client.searchOpenPrices("carottes", limit = 3)
        assertEquals(OpenPricesService().search("carottes", 3), results)
        assertEquals(3, results.size)
    }

    @Test
    fun `twenty results unless asked for more`() = runTestAsAdmin {
        assertEquals(20, client.searchOpenPrices("a").size)
        assertEquals(OpenPricesService.MAX_RESULTS, client.searchOpenPrices("a", limit = 500).size)
    }

    @Test
    fun `the table is closed to regular users`() = runTestAsUser {
        client.searchOpenPricesRaw("carottes").apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }
}
