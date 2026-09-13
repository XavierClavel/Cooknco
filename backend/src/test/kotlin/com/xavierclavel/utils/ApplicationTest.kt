package com.xavierclavel

import com.xavierclavel.plugins.DatabaseManager
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.AdminService
import com.xavierclavel.services.AppShellSource
import com.xavierclavel.services.AppVersionService
import com.xavierclavel.services.BackupService
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.DashboardService
import com.xavierclavel.services.DefaultImageService
import com.xavierclavel.services.DeviceService
import com.xavierclavel.services.EmailTemplateService
import com.xavierclavel.services.EncryptionService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.GotenbergPdfRenderer
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.ImageUploadTicketService
import com.xavierclavel.services.IngredientService
import com.xavierclavel.services.LikeService
import com.xavierclavel.services.LinkPreviewService
import com.xavierclavel.services.MailService
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.NotificationService
import com.xavierclavel.services.OAuthService
import com.xavierclavel.services.PdfRenderer
import com.xavierclavel.services.PushSender
import com.xavierclavel.services.PdfTemplateService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeNotesService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.RecipeStepService
import com.xavierclavel.services.StorageService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.loadConfig
import io.ebean.DB
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.Plugin
import io.ktor.server.testing.*
import io.ktor.utils.io.KtorDsl
import kotlinx.serialization.json.Json
import main.com.xavierclavel.containers.GotenbergTestContainer
import main.com.xavierclavel.containers.RedisTestContainer
import main.com.xavierclavel.utils.FakeAppShellSource
import main.com.xavierclavel.utils.FakePushSender
import main.com.xavierclavel.utils.login
import main.com.xavierclavel.utils.logout
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import shared.events.EventProducer
import shared.test.MockEventProducer
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApplicationTest: KoinTest {
    val userService: UserService by inject()
    val configuration: Configuration by inject()
    val encryptionService: EncryptionService by inject()
    val eventProducer: EventProducer by inject()
    val mockEventProducer by lazy{ eventProducer as MockEventProducer}
    val appShellSource: AppShellSource by inject()
    val fakeAppShellSource by lazy { appShellSource as FakeAppShellSource }
    val notificationService: NotificationService by inject()
    val pushSender: PushSender by inject()
    val fakePushSender by lazy { pushSender as FakePushSender }

    companion object {
        const val USER1 = "user1"
        const val USER2 = "user2"
        const val password = "Passw0rd"

        /**
         * Starts Koin once for the whole test JVM.
         *
         * Controllers are Kotlin `object`s, so their `by inject()` delegates resolve once
         * per process and then keep pointing at whichever Koin instance was live at first
         * use. Restarting Koin per test class would leave them wired to a stopped context,
         * and a class would then assert against services the running app no longer uses.
         * Isolation between tests comes from [cleanDb], not from recreating the container.
         */
        @BeforeAll
        @JvmStatic
        fun startKoin() {
            if (GlobalContext.getOrNull() != null) return

            val testModules = module {
                single { RecipeService() }
                single { UserService() }
                single { IngredientService() }
                single { ImageService() }
                single { ImageUploadTicketService() }
                single { DefaultImageService() }
                single { ExportService() }
                single { LikeService() }
                single { MailService() }
                single { CookbookService() }
                single { DashboardService() }
                single { RecipeIngredientService() }
                single { RecipeStepService() }
                single { FollowService() }
                single { RecipeNotesService() }
                single { ModerationService() }
                single { AdminService() }
                single { StorageService() }
                single { EmailTemplateService() }
                single { PdfTemplateService() }
                single { LinkPreviewService() }
                single { DeviceService() }
                single { BackupService() }
                single { AppVersionService() }
                single { NotificationService() }
                single { OAuthService() }
                single<AppShellSource> { FakeAppShellSource() }
                // Firebase is not reachable from a test, and would not be worth reaching:
                // what matters is the payload, which this records. See FakePushSender.
                single<PushSender> { FakePushSender() }
                // The real Chromium, not a stub: see GotenbergTestContainer.
                single<PdfRenderer> { GotenbergPdfRenderer(getProperty("gotenberg.url", "")) }
                single { RedisService(getProperty("redis.url", "redis://redis:6379")) }
                single { loadConfig() }
                single { EncryptionService() }
                single<EventProducer> { MockEventProducer() }
            }

            startKoin {
                modules(testModules)
                properties(mapOf(
                    "redis.url" to RedisTestContainer.getRedisUri(),
                    "gotenberg.url" to GotenbergTestContainer.getGotenbergUrl(),
                ))
            }
        }

        /**
         * Deliberately leaves Koin running: see [startKoin]. The JVM exiting is what tears
         * it down.
         */
        @AfterAll
        @JvmStatic
        fun stopKoinApplication() {
        }

        /**
         * The one statement [cleanDb] empties the database with.
         *
         * `DatabaseManager.getTables()` names the tables — asking Ebean what each query
         * bean's entity is mapped to, rather than spelling the table names a second time
         * where a rename would not reach them. Its hand-maintained order is what a wipe
         * deleting table by table needs and does not matter to a `TRUNCATE`: one statement
         * empties them together, so nothing is ever briefly pointing at a row that is
         * already gone. A table still has to be listed there to be named here.
         *
         * `CASCADE` covers what the list does not reach: the tables Ebean writes for a
         * recipe's steps are cleared by the cascade from `recipe` rather than by a delete
         * of their own, which matters because `Recipe.delete()` is soft — under the
         * previous row-by-row wipe a recipe's row, its steps and their links all survived
         * every wipe of the run, invisible to queries but never actually gone.
         *
         * Resolved once per JVM: the mapping cannot change while it runs.
         */
        private val truncateEveryTable: String by lazy {
            DatabaseManager.getTables()
                .joinToString(", ") { DB.getDefault().pluginApi().beanType(it.beanType).baseTable() }
                .let { "TRUNCATE TABLE $it CASCADE" }
        }

        /**
         * Installed once, not before every test: `CREATE EXTENSION IF NOT EXISTS` is a
         * no-op after the first, but a no-op still costs a round trip, and there were two
         * of them per test. An extension belongs to the database rather than to the schema
         * ebean-test drop-creates, so once per JVM is the right lifetime.
         */
        private val databaseExtensions: Unit by lazy {
            DB.sqlUpdate("CREATE EXTENSION IF NOT EXISTS unaccent").execute()
            DB.sqlUpdate("CREATE EXTENSION IF NOT EXISTS pg_trgm").execute()
            Unit
        }
    }



    @BeforeEach
    fun cleanDb() {
        // Notification fan-out outlives the request that caused it, so a dispatch from the
        // previous test may still be inserting. Draining it first keeps its rows out of this
        // test, and keeps the wipe below from racing an insert.
        runBlocking { notificationService.awaitDispatches() }
        databaseExtensions
        DB.sqlUpdate(truncateEveryTable).execute()
        setupTestUser(USER1)
        setupTestUser(USER2)
    }

    fun setupTestUser(mail: String, settings: UserSettingsDTO = UserSettingsDTO(true, true)): Long {
        val userDTO1 = UserDTO(username = UUID.randomUUID().toString(), password = password, mail = mail)
        val id1 = userService.createUser(userDTO1, true).id
        userService.updateSettings(id1, settings)
        return id1
    }


    @KtorDsl
    fun runTestAsAdmin(block: suspend TestBuilderWrapper.() -> Unit) = runTest {
        runAsAdmin {
            this.block()
        }
    }

    @KtorDsl
    fun runTestAsUser(block: suspend TestBuilderWrapper.() -> Unit) = runTest {
        runAsUser1 {
            this.block()
        }
    }

    @KtorDsl
    suspend fun TestBuilderWrapper.runAsAdmin(block: suspend TestBuilderWrapper.() -> Unit) = runAs("admin@mail.com", password) {
        this.block()
    }

    @KtorDsl
    suspend fun TestBuilderWrapper.runAsUser1(block: suspend TestBuilderWrapper.() -> Unit) = runAs(USER1, password) {
        this.block()
    }

    @KtorDsl
    suspend fun TestBuilderWrapper.runAsUser2(block: suspend TestBuilderWrapper.() -> Unit) = runAs(USER2, password) {
        this.block()
    }


    @KtorDsl
    suspend fun TestBuilderWrapper.runAs(username: String, password: String = "Passw0rd", block: suspend TestBuilderWrapper.() -> Unit) {
        client.login(username, password)
        this.block()
        client.logout()
    }

    @KtorDsl
    fun runTest(block: suspend TestBuilderWrapper.() -> Unit) {
        return testApplication(EmptyCoroutineContext) {

            userService.setupDefaultAdmin()
            application {
                module()
            }
            mockEventProducer.clear()
            fakeAppShellSource.reset()
            fakePushSender.reset()
            val wrapper = TestBuilderWrapper(this)
            wrapper.block() // Use the wrapper in the block
        }
    }


}


class TestBuilderWrapper(private val builder: ApplicationTestBuilder) {
    /**
     * A client that leaves redirects where they are, for tests that assert on *where* a
     * response sends the browser — the OAuth flow, whose every step is a redirect.
     *
     * Its own client rather than a derived one: `HttpClient.config {}` builds a new client with
     * a fresh cookie jar, so deriving one mid-test silently drops the session and every
     * authenticated request afterwards behaves as though nobody were logged in. A test using
     * this one therefore logs in with it.
     */
    val noRedirectClient: HttpClient by lazy { newNoRedirectClient() }

    /**
     * Another one, with a cookie jar of its own — a second browser, for a test that needs two
     * accounts signed in at once.
     */
    fun newNoRedirectClient(): HttpClient = builder.createClient {
        install(HttpCookies)
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        followRedirects = false
    }

    val client: HttpClient by lazy {
        builder.client.config {
            install(HttpCookies)
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }

    // Delegate install with proper types
    fun <P : Any, B : Any, F : Any> install(plugin: Plugin<Application, B, F>, configure: B.() -> Unit = {}) {
        builder.install(plugin, configure)
    }

    fun application(block: Application.() -> Unit) = builder.application(block)
}