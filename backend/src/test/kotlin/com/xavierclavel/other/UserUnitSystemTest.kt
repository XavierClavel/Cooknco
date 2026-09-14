package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.getSettings
import main.com.xavierclavel.utils.updateSettings
import org.junit.jupiter.api.Test
import shared.enums.UnitSystem
import shared.utils.URL.USER_URL
import kotlin.test.assertEquals

/**
 * The units an account reads amounts in.
 *
 * Unlike the language, nothing reports one — no client knows a handset's preferred ladder
 * the way it knows its language — so there is no adoption rule to test here. There is only
 * what the account saved, and metric until it has said. What the two do share is the rule
 * that a save which says nothing about a field leaves it alone, because a build that
 * predates the setting sends a body without it.
 *
 * What this never touches is storage: a recipe keeps the unit its author picked whoever
 * reads it. `UnitConversionTest` is where the conversion itself is checked.
 */
class UserUnitSystemTest : ApplicationTest() {

    private val password = "password"

    @Test
    fun `an account nobody has asked reads in metric`() = runTest {
        val mail = "never-asked@mail.com"
        runAsAdmin { client.createUser(mail) }

        runAs(mail, password) {
            assertEquals(
                UnitSystem.METRIC,
                client.getSettings().unitSystem,
                "metric is what every recipe was written in while there was nothing to choose",
            )
        }
    }

    @Test
    fun `choosing imperial in the settings saves it on the account`() = runTest {
        val mail = "cooks-in-cups@mail.com"
        runAsAdmin { client.createUser(mail) }

        runAs(mail, password) {
            client.updateSettings(client.getSettings().copy(unitSystem = UnitSystem.IMPERIAL))
            assertEquals(UnitSystem.IMPERIAL, client.getSettings().unitSystem)
        }
    }

    /**
     * The reason `UserSettingsDTO.unitSystem` is nullable on the way in: a build that
     * predates the field sends a body without it, and saving a privacy toggle from such a
     * build must not put the account back on metric behind the user's back.
     */
    @Test
    fun `a settings save that says nothing about the units leaves them alone`() = runTest {
        val mail = "old-build@mail.com"
        runAsAdmin { client.createUser(mail) }

        runAs(mail, password) {
            client.updateSettings(client.getSettings().copy(unitSystem = UnitSystem.IMPERIAL))

            // Exactly what a client with no notion of the field puts on the wire - a typed
            // helper cannot express it, since a Kotlin caller always has the property
            client.put("$USER_URL/settings") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody("""{"autoAcceptFollowRequests":true,"isAccountPublic":true}""")
            }.apply { assertEquals(HttpStatusCode.OK, status) }

            assertEquals(UnitSystem.IMPERIAL, client.getSettings().unitSystem)
        }
    }
}
