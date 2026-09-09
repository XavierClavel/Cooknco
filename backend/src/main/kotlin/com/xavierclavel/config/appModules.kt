package com.xavierclavel.config

import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.AdminService
import com.xavierclavel.services.AppShellSource
import com.xavierclavel.services.HttpAppShellSource
import com.xavierclavel.services.LinkPreviewService
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.DashboardService
import com.xavierclavel.services.DefaultImageService
import com.xavierclavel.services.EmailTemplateService
import com.xavierclavel.services.EncryptionService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.GotenbergPdfRenderer
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.IngredientService
import com.xavierclavel.services.LikeService
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.PdfRenderer
import com.xavierclavel.services.PdfTemplateService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeNotesService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.StorageService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.loadConfig
import org.koin.dsl.module
import shared.events.EventProducer
import shared.events.KafkaEventProducer

val config = loadConfig()
val appModules = module {
    single { RecipeService() }
    single { UserService() }
    single { IngredientService() }
    single { ImageService() }
    single { DefaultImageService() }
    single { ExportService() }
    single { LikeService() }
    single { CookbookService() }
    single { DashboardService() }
    single { RecipeIngredientService() }
    single { FollowService() }
    single { RecipeNotesService() }
    single { ModerationService() }
    single { AdminService() }
    single { StorageService() }
    single { EmailTemplateService() }
    single { PdfTemplateService() }
    single { LinkPreviewService() }
    // Over the cluster network, because the shell it reads is baked into the frontend image
    // and not this one. Tests swap in a stub.
    single<AppShellSource> { HttpAppShellSource(config) }
    // Headless Chromium, in its own pod: printing an operator's layout the way their own
    // browser would is the whole reason the sheet is HTML. See the renderer.
    single<PdfRenderer> {
        GotenbergPdfRenderer(
            baseUrl = config.pdf.gotenbergUrl,
            concurrency = config.pdf.maxConcurrentRenders,
            queueMillis = config.pdf.renderQueueSeconds * 1_000,
        )
    }
    single { RedisService(getProperty("redis.url", "redis://:${System.getenv("REDIS_PASSWORD")}@cooknco-redis:6379")) }
    single { config }
    single { EncryptionService() }
    single<EventProducer> { KafkaEventProducer() }
}