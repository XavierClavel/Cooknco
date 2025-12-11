package com.xavierclavel

import com.xavierclavel.plugins.DatabaseManager
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.CustomIngredientService
import com.xavierclavel.services.DashboardService
import com.xavierclavel.services.EncryptionService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.IngredientService
import com.xavierclavel.services.LikeService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
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
import main.com.xavierclavel.containers.RedisTestContainer
import main.com.xavierclavel.utils.login
import main.com.xavierclavel.utils.logout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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
    val encryptionService: EncryptionService by inject()
    val eventProducer: EventProducer by inject()
    val mockEventProducer by lazy{ eventProducer as MockEventProducer}

    companion object {
        const val USER1 = "user1"
        const val USER2 = "user2"
        const val password = "Passw0rd"

        @BeforeAll
        @JvmStatic
        fun startKoin() {
            val testModules = module {
                single { RecipeService() }
                single { UserService() }
                single { IngredientService() }
                single { ImageService() }
                single { ExportService() }
                single { LikeService() }
                single { CookbookService() }
                single { DashboardService() }
                single { RecipeIngredientService() }
                single { CustomIngredientService() }
                single { FollowService() }
                single { RedisService(getProperty("redis.url", "redis://redis:6379")) }
                single { loadConfig() }
                single { EncryptionService() }
                single<EventProducer> { MockEventProducer() }
            }

            startKoin {
                modules(testModules)
                properties(mapOf("redis.url" to RedisTestContainer.getRedisUri()))
            }
        }

        @AfterAll
        @JvmStatic
        fun stopKoinApplication() {
            stopKoin()
        }
    }



    @BeforeEach
    fun cleanDb() {
        DatabaseManager.getTables().forEach {
            it.findList().forEach { it.delete() }
        }
        DB.sqlUpdate("CREATE EXTENSION IF NOT EXISTS unaccent").execute()
        DB.sqlUpdate("CREATE EXTENSION IF NOT EXISTS pg_trgm").execute()
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
            val wrapper = TestBuilderWrapper(this)
            wrapper.block() // Use the wrapper in the block
        }
    }


}


class TestBuilderWrapper(private val builder: ApplicationTestBuilder) {
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