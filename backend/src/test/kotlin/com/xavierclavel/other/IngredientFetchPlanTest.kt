package main.com.xavierclavel.other

import com.xavierclavel.services.AdminService
import com.xavierclavel.services.IngredientService
import io.ebean.Paging
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.ingredientDTO
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.enums.Locale

/**
 * Guards the ingredient catalogue against N+1.
 *
 * An ingredient's name is not on its row — it is one translation row per language — so every entry
 * the search returns reads a collection to be named at all. This one is typed against: the editor
 * queries it on every keystroke.
 */
class IngredientFetchPlanTest : FetchPlanTest() {
    private val ingredientService: IngredientService by inject()
    private val adminService: AdminService by inject()

    private val paging = Paging.of(0, 20)

    @Test
    fun `searching the catalogue costs the same whether it holds one entry or several`() = runTest {
        runAsAdmin {
            client.createIngredient(ingredientDTO.copy(name = mapOf(Locale.EN to "tomato")))
            assertQueryCountDoesNotGrow(
                what = "IngredientService.search",
                read = { ingredientService.search("", paging, Locale.EN).second },
                grow = {
                    listOf("potato", "carrot").forEach {
                        client.createIngredient(ingredientDTO.copy(name = mapOf(Locale.EN to it)))
                    }
                },
            )
        }
    }

    @Test
    fun `the admin catalogue table costs the same whether it holds one entry or several`() = runTest {
        runAsAdmin {
            client.createIngredient(ingredientDTO.copy(name = mapOf(Locale.EN to "tomato")))
            assertQueryCountDoesNotGrow(
                what = "AdminService.searchIngredients",
                read = { adminService.searchIngredients(null, null, paging).second },
                grow = {
                    listOf("potato", "carrot").forEach {
                        client.createIngredient(ingredientDTO.copy(name = mapOf(Locale.EN to it)))
                    }
                },
            )
        }
    }
}
