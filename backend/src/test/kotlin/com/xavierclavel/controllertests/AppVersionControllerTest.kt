package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.models.query.QAppVersion
import com.xavierclavel.models.query.QDevice
import io.ebean.DB
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.appVersion
import main.com.xavierclavel.utils.appVersionReach
import main.com.xavierclavel.utils.appVersionReachRaw
import main.com.xavierclavel.utils.checkAppVersion
import main.com.xavierclavel.utils.checkAppVersionRaw
import main.com.xavierclavel.utils.clearAppVersionRaw
import main.com.xavierclavel.utils.listAppVersions
import main.com.xavierclavel.utils.listAppVersionsJson
import main.com.xavierclavel.utils.listAppVersionsRaw
import main.com.xavierclavel.utils.saveAppVersion
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.registerDevice
import main.com.xavierclavel.utils.registerLegacyDeviceRaw
import main.com.xavierclavel.utils.saveAppVersionRaw
import org.junit.jupiter.api.Test
import shared.enums.DevicePlatform
import shared.enums.AppPlatform
import shared.enums.AppVersionStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gate that decides whether a mobile build is allowed to run.
 *
 * Two properties carry the whole feature, and both are about the direction the thing fails
 * in. An operator has to be able to raise the floor without shipping a build — that is what
 * the tab is for — and *nothing* may lock a user out by accident: no row, an unreadable
 * version, a build newer than the store's, all mean the app runs. The rest is the shape
 * every backoffice tab here has.
 */
class AppVersionControllerTest : ApplicationTest() {

    private val store = "https://play.google.com/store/apps/details?id=com.xavierclavel.cooknco"

    // ----------------------------------------------------------- authorisation

    /** Asked at launch, before there is a session, so this one endpoint is open. */
    @Test
    fun `checking a version needs no session`() = runTest {
        client.checkAppVersionRaw(AppPlatform.ANDROID.name, "1.0.0")
            .apply { assertEquals(HttpStatusCode.OK, status) }
    }

    @Test
    fun `gates are closed to anonymous callers`() = runTest {
        client.listAppVersionsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.saveAppVersionRaw(AppPlatform.ANDROID, "1.0.0", "1.0.0")
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.clearAppVersionRaw(AppPlatform.ANDROID)
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `gates are closed to regular users`() = runTestAsUser {
        client.listAppVersionsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.saveAppVersionRaw(AppPlatform.ANDROID, "1.0.0", "1.0.0")
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ---------------------------------------------------------------- listing

    @Test
    fun `every platform is listed, ungated, until one is saved`() = runTestAsAdmin {
        val gates = client.listAppVersions()

        assertEquals(AppPlatform.entries.size, gates.size)
        gates.forEach {
            assertFalse(it.configured, "${it.platform} is reported as gated when nothing was saved")
            assertEquals("", it.minimumVersion)
            assertNull(it.updatedAt)
        }
    }

    /**
     * The backoffice binds an input to each of these, on an ungated row too, so an absent
     * one would leave the form editing `undefined`.
     *
     * Asserted on the raw JSON: decoding into the DTO fills a missing field back in from
     * its default, so a typed assertion here would pass on a payload the tab cannot use.
     */
    @Test
    fun `an ungated platform still carries every field on the wire`() = runTestAsAdmin {
        val row = client.listAppVersionsJson().first()

        listOf("platform", "configured", "minimumVersion", "latestVersion", "storeUrl").forEach {
            assertTrue(row.containsKey(it), "$it is missing from the payload")
        }
    }

    /** Listing is a read: an install nobody has configured must have no rows at all. */
    @Test
    fun `listing writes no rows`() = runTestAsAdmin {
        client.listAppVersions()
        assertEquals(0, QAppVersion().findCount())
    }

    // ----------------------------------------------------------- the verdict

    @Test
    fun `an ungated platform lets every build run`() = runTest {
        val check = client.checkAppVersion(AppPlatform.ANDROID, "0.0.1")

        assertEquals(AppVersionStatus.OK, check.status)
        assertNull(check.minimumVersion)
        assertNull(check.storeUrl)
    }

    @Test
    fun `a build below the floor is required to update`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.4.0", latest = "1.6.0")

        val check = client.checkAppVersion(AppPlatform.ANDROID, "1.3.9")

        assertEquals(AppVersionStatus.UPDATE_REQUIRED, check.status)
        assertEquals("1.4.0", check.minimumVersion)
        assertEquals("1.6.0", check.latestVersion)
        assertEquals(store, check.storeUrl)
    }

    @Test
    fun `a build between the floor and the store is only nudged`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.4.0", latest = "1.6.0")

        assertEquals(
            AppVersionStatus.UPDATE_AVAILABLE,
            client.checkAppVersion(AppPlatform.ANDROID, "1.5.2").status,
        )
        // The floor itself is allowed to run: it is the oldest *supported* build, not the
        // oldest blocked one
        assertEquals(
            AppVersionStatus.UPDATE_AVAILABLE,
            client.checkAppVersion(AppPlatform.ANDROID, "1.4.0").status,
        )
    }

    @Test
    fun `a build at or past the store is left alone`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.4.0", latest = "1.6.0")

        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "1.6.0").status)
        // A build ahead of the store is a tester's, not a stale one
        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "1.7.0").status)
    }

    /** The bug a string comparison would ship: "1.10.0" sorts before "1.9.0" as text. */
    @Test
    fun `versions are compared as numbers, not as text`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.9.0", latest = "1.10.0")

        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "1.10.0").status)
        assertEquals(
            AppVersionStatus.UPDATE_REQUIRED,
            client.checkAppVersion(AppPlatform.ANDROID, "1.8.9").status,
        )
    }

    /** Trailing components are zeroes, so 1.4 and 1.4.0 are the same build. */
    @Test
    fun `a version with fewer components is padded, not judged short`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.4.0", latest = "1.4.0")

        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "1.4").status)
    }

    /**
     * The property the whole design turns on: anything we cannot read runs.
     *
     * A build that calls itself something we did not anticipate is still a real install on
     * a real phone, and blocking it would be unrecoverable from that phone.
     */
    @Test
    fun `a version that cannot be read is never blocked`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "9.9.9", latest = "9.9.9")

        listOf("", "dev", "nightly-2026-01-04").forEach {
            assertEquals(
                AppVersionStatus.OK,
                client.checkAppVersion(AppPlatform.ANDROID, it).status,
                "\"$it\" was judged old enough to block",
            )
        }
    }

    /** A pre-release compares as its release, which errs towards letting a tester in. */
    @Test
    fun `a pre-release suffix is read as the version it precedes`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.4.0", latest = "1.6.0")

        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "1.6.0-rc2").status)
        assertEquals(
            AppVersionStatus.UPDATE_REQUIRED,
            client.checkAppVersion(AppPlatform.ANDROID, "1.2.0-rc2").status,
        )
    }

    @Test
    fun `a gate applies to its own platform only`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "5.0.0", latest = "5.0.0")

        assertEquals(
            AppVersionStatus.UPDATE_REQUIRED,
            client.checkAppVersion(AppPlatform.ANDROID, "1.0.0").status,
        )
        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.IOS, "1.0.0").status)
    }

    @Test
    fun `a client that does not name its platform is a bad request`() = runTest {
        client.checkAppVersionRaw(null, "1.0.0")
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.checkAppVersionRaw("WEB", "1.0.0")
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun `saving a gate replaces it rather than adding a second`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.4.0", latest = "1.6.0")
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.5.0", latest = "1.7.0")

        assertEquals(1, QAppVersion().findCount())
        val gate = client.appVersion(AppPlatform.ANDROID)
        assertTrue(gate.configured)
        assertEquals("1.5.0", gate.minimumVersion)
        assertEquals("1.7.0", gate.latestVersion)
        assertEquals(
            AppVersionStatus.UPDATE_REQUIRED,
            client.checkAppVersion(AppPlatform.ANDROID, "1.4.0").status,
        )
    }

    /**
     * A floor above the ceiling blocks everybody, the users who did update included, and
     * the only way back is the screen that just refused it. It is one typo away, so it is
     * refused rather than warned about.
     */
    @Test
    fun `a floor above the store version is refused`() = runTestAsAdmin {
        client.saveAppVersionRaw(AppPlatform.ANDROID, minimum = "2.0.0", latest = "1.6.0").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        assertEquals(0, QAppVersion().findCount())
    }

    @Test
    fun `a version that is not a version is refused`() = runTestAsAdmin {
        listOf("", "latest", "v1.4.0", "1.4.0-rc1", "1.4.0.0.1").forEach {
            client.saveAppVersionRaw(AppPlatform.ANDROID, minimum = it, latest = "9.9.9").apply {
                assertEquals(HttpStatusCode.BadRequest, status, "\"$it\" was accepted as a version")
            }
        }
        assertEquals(0, QAppVersion().findCount())
    }

    /** A blocked build with nowhere to go is a dead app, so the link is part of the gate. */
    @Test
    fun `a gate with no store link is refused`() = runTestAsAdmin {
        listOf("", "   ", "play.google.com/store", "javascript:alert(1)").forEach {
            client.saveAppVersionRaw(AppPlatform.ANDROID, "1.4.0", "1.6.0", storeUrl = it).apply {
                assertEquals(HttpStatusCode.BadRequest, status, "\"$it\" was accepted as a store link")
            }
        }
        assertEquals(0, QAppVersion().findCount())
    }

    // ---------------------------------------------------------------- clearing

    @Test
    fun `clearing a gate puts every build back in service`() = runTestAsAdmin {
        client.saveAppVersion(AppPlatform.ANDROID, minimum = "5.0.0", latest = "5.0.0")
        assertEquals(
            AppVersionStatus.UPDATE_REQUIRED,
            client.checkAppVersion(AppPlatform.ANDROID, "1.0.0").status,
        )

        client.clearAppVersionRaw(AppPlatform.ANDROID).apply { assertEquals(HttpStatusCode.OK, status) }

        assertEquals(0, QAppVersion().findCount())
        assertFalse(client.appVersion(AppPlatform.ANDROID).configured)
        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "1.0.0").status)
    }

    @Test
    fun `clearing a gate that does not exist is a 404`() = runTestAsAdmin {
        client.clearAppVersionRaw(AppPlatform.IOS).apply { assertEquals(HttpStatusCode.NotFound, status) }
    }

    // ------------------------------------------------------- what a floor costs

    /**
     * Backdates a device so it falls outside the reach window.
     *
     * Raw SQL because `lastSeenAt` is `@WhenModified`: saving the entity would stamp it
     * with now and undo the very thing being set up. Test fixtures are the one place this
     * project allows that.
     */
    private fun backdate(token: String, days: Long) {
        DB.sqlUpdate("update devices set last_seen_at = last_seen_at - (:days || ' days')::interval where token = :token")
            .setParameter("days", days)
            .setParameter("token", token)
            .execute()
    }

    @Test
    fun `reach is closed to anonymous callers and to regular users`() = runTest {
        client.appVersionReachRaw(AppPlatform.ANDROID)
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        runAsUser1 {
            client.appVersionReachRaw(AppPlatform.ANDROID)
                .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        }
    }

    /**
     * The point of the panel: a floor is costed before it is put in force, in the two
     * units that answer different questions.
     */
    @Test
    fun `reach counts the devices and the users a candidate floor would stop`() = runTest {
        val second = "reach@mail.com"
        runAsAdmin { client.createUser(second) }

        runAsUser1 {
            client.registerDevice("old-phone", appVersion = "1.2.0")
            client.registerDevice("old-tablet", appVersion = "1.3.0")
        }
        runAs(second, "password") { client.registerDevice("new-phone", appVersion = "1.6.0") }

        runAsAdmin {
            val reach = client.appVersionReach(AppPlatform.ANDROID, minimum = "1.4.0")

            assertEquals(3, reach.devices)
            assertEquals(2, reach.users)
            assertEquals(2, reach.blockedDevices)
            // One person, two stale handsets: the figure to hesitate over is people
            assertEquals(1, reach.blockedUsers)
        }
    }

    /** Measured against what is being typed, not against what is saved. */
    @Test
    fun `reach answers for the candidate floor rather than the one in force`() = runTest {
        runAsUser1 { client.registerDevice("phone", appVersion = "1.5.0") }

        runAsAdmin {
            client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.0.0", latest = "1.6.0")

            assertEquals(0, client.appVersionReach(AppPlatform.ANDROID).blockedDevices)
            assertEquals(1, client.appVersionReach(AppPlatform.ANDROID, minimum = "1.6.0").blockedDevices)
        }
    }

    /** With no candidate given, the panel opens on what today's gate is already blocking. */
    @Test
    fun `reach defaults to the floor in force`() = runTest {
        runAsUser1 { client.registerDevice("phone", appVersion = "1.2.0") }

        runAsAdmin {
            client.saveAppVersion(AppPlatform.ANDROID, minimum = "1.5.0", latest = "1.6.0")

            val reach = client.appVersionReach(AppPlatform.ANDROID)
            assertEquals("1.5.0", reach.minimumVersion)
            assertEquals(1, reach.blockedDevices)
        }
    }

    /** A half-typed field must not flash a number that says everybody is affected. */
    @Test
    fun `an empty candidate floor blocks nothing`() = runTest {
        runAsUser1 { client.registerDevice("phone", appVersion = "1.2.0") }

        runAsAdmin {
            assertEquals(0, client.appVersionReach(AppPlatform.ANDROID, minimum = "").blockedDevices)
            assertEquals(0, client.appVersionReach(AppPlatform.ANDROID, minimum = "1.").blockedDevices)
        }
    }

    /**
     * A build too old to report its version is exactly the population a floor is aimed at,
     * and exactly the one that must not be counted as blocked: the gate lets it run.
     */
    @Test
    fun `a device that reports no version is counted apart and never blocked`() = runTest {
        runAsUser1 {
            client.registerLegacyDeviceRaw("legacy-phone")
                .apply { assertEquals(HttpStatusCode.Created, status) }
            client.registerDevice("current-phone", appVersion = "1.6.0")
        }

        runAsAdmin {
            val reach = client.appVersionReach(AppPlatform.ANDROID, minimum = "9.9.9")

            assertEquals(2, reach.devices)
            assertEquals(1, reach.unknownDevices)
            assertEquals(1, reach.blockedDevices, "the version-less device was counted as blocked")
        }
        // and the gate agrees: it would let that build run
        runAsAdmin { client.saveAppVersion(AppPlatform.ANDROID, minimum = "9.9.9", latest = "9.9.9") }
        assertEquals(AppVersionStatus.OK, client.checkAppVersion(AppPlatform.ANDROID, "").status)
    }

    /**
     * Nothing prunes `devices` on a timer, so without the window a handset replaced two
     * years ago would keep inflating what every floor appears to cost.
     */
    @Test
    fun `a device nobody has opened in months is left out`() = runTest {
        runAsUser1 {
            client.registerDevice("live-phone", appVersion = "1.2.0")
            client.registerDevice("abandoned-phone", appVersion = "1.0.0")
        }
        backdate("abandoned-phone", days = 200)

        runAsAdmin {
            val reach = client.appVersionReach(AppPlatform.ANDROID, minimum = "1.4.0")

            assertEquals(1, reach.devices)
            assertEquals(1, reach.blockedDevices)
            assertEquals(90, reach.windowDays)
        }
    }

    @Test
    fun `reach counts only the platform asked about`() = runTest {
        runAsUser1 {
            client.registerDevice("android-phone", platform = DevicePlatform.ANDROID, appVersion = "1.2.0")
            client.registerDevice("iphone", platform = DevicePlatform.IOS, appVersion = "1.2.0")
            // The web client is gated by nothing, so it belongs to neither figure
            client.registerDevice("browser", platform = DevicePlatform.WEB, appVersion = "1.2.0")
        }

        runAsAdmin {
            assertEquals(1, client.appVersionReach(AppPlatform.ANDROID, "1.4.0").blockedDevices)
            assertEquals(1, client.appVersionReach(AppPlatform.IOS, "1.4.0").blockedDevices)
        }
    }

    /** The list a floor is chosen from: newest build first, and the unreadable one last. */
    @Test
    fun `the distribution is ordered newest first, numerically, with unknown last`() = runTest {
        runAsUser1 {
            client.registerDevice("a", appVersion = "1.9.0")
            client.registerDevice("b", appVersion = "1.10.0")
            client.registerLegacyDeviceRaw("c")
            client.registerDevice("d", appVersion = "1.2.0")
        }

        runAsAdmin {
            val versions = client.appVersionReach(AppPlatform.ANDROID, "1.9.0")
                .distribution.map { it.version }

            assertEquals(listOf("1.10.0", "1.9.0", "1.2.0", ""), versions)
        }
    }

    /**
     * The unit the tab reads the distribution in: how many *people* are on each build.
     *
     * Not the device count, as soon as anybody owns two handsets, and counted the same way
     * as `blockedUsers` — one row per person with at least one device on that build — so
     * the two figures on that panel answer the same question and can be compared. The
     * consequence is that a person split across two builds is in both rows, which is why
     * the per-build totals here come to more than the base and why the tab says so.
     */
    @Test
    fun `the distribution counts people per build, and counts one twice when their devices disagree`() = runTest {
        runAsUser1 {
            client.registerDevice("phone", appVersion = "1.2.0")
            client.registerDevice("tablet", appVersion = "1.6.0")
        }
        runAsUser2 {
            client.registerDevice("second-phone", appVersion = "1.6.0")
            client.registerDevice("second-tablet", appVersion = "1.6.0")
        }

        runAsAdmin {
            val reach = client.appVersionReach(AppPlatform.ANDROID, minimum = "1.4.0")
            val rows = reach.distribution.associateBy { it.version }

            assertEquals(4, reach.devices)
            assertEquals(2, reach.users)

            // Three handsets on 1.6.0, but only two people: one of them carries two of them
            assertEquals(3, rows.getValue("1.6.0").devices)
            assertEquals(2, rows.getValue("1.6.0").users)
            assertEquals(1, rows.getValue("1.2.0").devices)
            assertEquals(1, rows.getValue("1.2.0").users)

            // user1 is on both builds, so the rows total more than the base does
            assertEquals(3, reach.distribution.sumOf { it.users })
            assertEquals(1, reach.blockedUsers)
        }
    }

    @Test
    fun `each build in the distribution says whether the candidate floor stops it`() = runTest {
        runAsUser1 {
            client.registerDevice("old", appVersion = "1.2.0")
            client.registerDevice("new", appVersion = "1.6.0")
        }

        runAsAdmin {
            val rows = client.appVersionReach(AppPlatform.ANDROID, "1.4.0")
                .distribution.associate { it.version to it.blocked }

            assertEquals(mapOf("1.6.0" to false, "1.2.0" to true), rows)
        }
    }

    // ------------------------------------------------------ reporting a version

    /** Re-registering is what keeps the figures about the build that is installed now. */
    @Test
    fun `a device that updated is counted as the build it is on now`() = runTest {
        runAsUser1 {
            client.registerDevice("phone", appVersion = "1.2.0")
            client.registerDevice("phone", appVersion = "1.6.0")
        }

        assertEquals(1, QDevice().findCount())
        assertEquals("1.6.0", QDevice().token.eq("phone").findOne()?.appVersion)
    }

    /** An app that shipped before this column existed still has to be able to register. */
    @Test
    fun `a client that sends no version at all still registers`() = runTest {
        runAsUser1 {
            client.registerLegacyDeviceRaw("legacy-phone")
                .apply { assertEquals(HttpStatusCode.Created, status) }
        }

        assertEquals("", QDevice().token.eq("legacy-phone").findOne()?.appVersion)
    }
}
