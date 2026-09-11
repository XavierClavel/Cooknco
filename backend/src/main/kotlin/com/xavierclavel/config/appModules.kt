package com.xavierclavel.config

import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.AdminService
import com.xavierclavel.services.AppShellSource
import com.xavierclavel.services.AppVersionService
import com.xavierclavel.services.HttpAppShellSource
import com.xavierclavel.services.LinkPreviewService
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.DashboardService
import com.xavierclavel.services.DefaultImageService
import com.xavierclavel.services.DeviceService
import com.xavierclavel.services.EmailTemplateService
import com.xavierclavel.services.EncryptionService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.FcmAccessTokens
import com.xavierclavel.services.FcmPushSender
import com.xavierclavel.services.GotenbergPdfRenderer
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.ImageUploadTicketService
import com.xavierclavel.services.IngredientService
import com.xavierclavel.services.LikeService
import com.xavierclavel.services.MailService
import com.xavierclavel.services.UnsubscribeService
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.NoopPushSender
import com.xavierclavel.services.NotificationService
import com.xavierclavel.services.OAuthService
import com.xavierclavel.services.PdfRenderer
import com.xavierclavel.services.PushSender
import com.xavierclavel.services.PdfTemplateService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeNotesService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.StorageService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.loadConfig
// Aliased: inside a Koin `single { }` the receiver is a Scope, which has a `logger`
// of its own that would otherwise shadow this one.
import com.xavierclavel.utils.logger as appLogger
import org.koin.dsl.module
import shared.events.EventProducer
import shared.events.KafkaEventProducer

val config = loadConfig()
val appModules = module {
    single { RecipeService() }
    single { UserService() }
    single { IngredientService() }
    single { ImageService() }
    single { ImageUploadTicketService() }
    single { DefaultImageService() }
    single { ExportService() }
    single { LikeService() }
    single { MailService() }
    single { UnsubscribeService() }
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
    single { DeviceService() }
    single { AppVersionService() }
    single { OAuthService() }
    single { NotificationService() }
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
    /**
     * Firebase, when this install has credentials for it.
     *
     * Falls back to a sender that only logs rather than failing to start: an install with
     * no push credentials is a normal one — every developer's, and the test suite's — and
     * notifications are stored either way, so the app degrades to a list without a buzz.
     */
    single<PushSender> {
        if (!config.push.enabled) {
            appLogger.info { "Push notifications are disabled: notifications will be stored, not delivered" }
            NoopPushSender()
        } else runCatching {
            FcmPushSender(
                credentials = FcmAccessTokens(config.push.credentialsPath),
                projectId = config.push.projectId,
                concurrency = config.push.maxConcurrentSends,
            )
        }.getOrElse {
            // Deliberately not fatal: a bad key must not take the whole backend down
            appLogger.error(it) { "Push credentials could not be read; notifications will not be delivered" }
            NoopPushSender()
        }
    }
    single { RedisService(getProperty("redis.url", "redis://:${System.getenv("REDIS_PASSWORD")}@cooknco-redis:6379")) }
    single { config }
    single { EncryptionService() }
    single<EventProducer> { KafkaEventProducer() }
}