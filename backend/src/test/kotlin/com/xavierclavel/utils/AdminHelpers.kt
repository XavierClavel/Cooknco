package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import shared.dto.EffectiveEmailTemplate
import shared.dto.EmailPreviewDTO
import shared.dto.EmailTemplateDTO
import shared.dto.EmailTemplateKeyDTO
import shared.dto.EmailTestDTO
import shared.dto.LogPage
import shared.dto.ModerationReasonDTO
import shared.dto.ReportDTO
import shared.dto.ReportResolutionDTO
import shared.dto.SearchResult
import shared.dto.StorageCleanupDTO
import shared.dto.SuspensionDTO
import shared.enums.ModerationAction
import shared.enums.ReportReason
import shared.enums.ReportStatus
import shared.enums.DefaultImage
import shared.enums.ImageBucket
import shared.enums.ImageSort
import shared.enums.ImageStatus
import shared.enums.Locale
import shared.enums.ReportTargetType
import shared.enums.UserRole
import shared.infodto.AdminDefaultImageInfo
import shared.infodto.AdminEmailTemplateInfo
import shared.infodto.AdminImageInfo
import shared.infodto.AdminIngredientInfo
import shared.infodto.AdminOverview
import shared.infodto.AdminStorageCleanupResult
import shared.infodto.AdminStorageOverview
import shared.infodto.AdminRecipeInfo
import shared.infodto.AdminTrends
import shared.infodto.AdminUserInfo
import shared.infodto.EmailPreviewInfo
import shared.infodto.ReportInfo
import shared.utils.URL.ADMIN_URL
import shared.utils.URL.INTERNAL_MAIL_TEMPLATES_URL
import shared.utils.URL.REPORT_URL
import kotlin.test.assertEquals
import io.ktor.client.statement.bodyAsBytes
import shared.infodto.AdminPdfTemplateInfo
import shared.dto.PdfTemplateDTO
import shared.dto.PdfPreviewDTO

private val json = Json { ignoreUnknownKeys = true }

// ------------------------------------------------------------------- reporting

suspend fun HttpClient.reportRaw(
    targetType: ReportTargetType,
    targetId: Long,
    reason: ReportReason = ReportReason.SPAM,
    comment: String = "",
) = this.post(REPORT_URL) {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(ReportDTO(targetType = targetType, targetId = targetId, reason = reason, comment = comment))
}

suspend fun HttpClient.report(
    targetType: ReportTargetType,
    targetId: Long,
    reason: ReportReason = ReportReason.SPAM,
    comment: String = "",
): ReportInfo =
    this.reportRaw(targetType, targetId, reason, comment).let {
        assertEquals(HttpStatusCode.Created, it.status)
        json.decodeFromString<ReportInfo>(it.bodyAsText())
    }

// -------------------------------------------------------------------- overview

suspend fun HttpClient.getAdminOverviewRaw(): HttpResponse = this.get("$ADMIN_URL/overview")

suspend fun HttpClient.getAdminOverview(): AdminOverview =
    this.getAdminOverviewRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminOverview>(it.bodyAsText())
    }

// -------------------------------------------------------------------- trends

suspend fun HttpClient.getTrendsRaw(granularity: String? = null, buckets: Int? = null) =
    this.get("$ADMIN_URL/trends") {
        url {
            granularity?.let { parameters.append("granularity", it) }
            buckets?.let { parameters.append("buckets", it.toString()) }
        }
    }

suspend fun HttpClient.getTrends(granularity: String? = null, buckets: Int? = null): AdminTrends =
    this.getTrendsRaw(granularity, buckets).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminTrends>(it.bodyAsText())
    }

// ----------------------------------------------------------------------- users

suspend fun HttpClient.listAdminUsers(
    query: String? = null,
    role: UserRole? = null,
    status: String? = null,
): SearchResult<AdminUserInfo> =
    this.get("$ADMIN_URL/users") {
        url {
            query?.let { parameters.append("query", it) }
            role?.let { parameters.append("role", it.name) }
            status?.let { parameters.append("status", it) }
        }
    }.let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<SearchResult<AdminUserInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.getAdminUser(id: Long): AdminUserInfo =
    this.get("$ADMIN_URL/users/$id").let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminUserInfo>(it.bodyAsText())
    }

suspend fun HttpClient.suspendUserRaw(id: Long, days: Int = 7, reason: String = "") =
    this.post("$ADMIN_URL/users/$id/suspend") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(SuspensionDTO(days = days, reason = reason))
    }

suspend fun HttpClient.banUserRaw(id: Long, reason: String = "") =
    this.post("$ADMIN_URL/users/$id/ban") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(ModerationReasonDTO(reason = reason))
    }

suspend fun HttpClient.reinstateUserRaw(id: Long) = this.post("$ADMIN_URL/users/$id/reinstate")

suspend fun HttpClient.setUserRoleRaw(id: Long, role: UserRole) =
    this.put("$ADMIN_URL/users/$id/role/${role.name}")

suspend fun HttpClient.deleteUserAsAdminRaw(id: Long) = this.delete("$ADMIN_URL/users/$id")

// --------------------------------------------------------------------- recipes

suspend fun HttpClient.listAdminRecipes(
    query: String? = null,
    hidden: Boolean? = null,
    reported: Boolean? = null,
): SearchResult<AdminRecipeInfo> =
    this.get("$ADMIN_URL/recipes") {
        url {
            query?.let { parameters.append("query", it) }
            hidden?.let { parameters.append("hidden", it.toString()) }
            reported?.let { parameters.append("reported", it.toString()) }
        }
    }.let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<SearchResult<AdminRecipeInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.hideRecipeRaw(id: Long, reason: String = "") =
    this.post("$ADMIN_URL/recipes/$id/hide") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(ModerationReasonDTO(reason = reason))
    }

suspend fun HttpClient.hideRecipe(id: Long, reason: String = ""): AdminRecipeInfo =
    this.hideRecipeRaw(id, reason).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminRecipeInfo>(it.bodyAsText())
    }

suspend fun HttpClient.unhideRecipe(id: Long): AdminRecipeInfo =
    this.post("$ADMIN_URL/recipes/$id/unhide").let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminRecipeInfo>(it.bodyAsText())
    }

suspend fun HttpClient.deleteRecipeAsAdminRaw(id: Long) = this.delete("$ADMIN_URL/recipes/$id")

// ----------------------------------------------------------------- ingredients

suspend fun HttpClient.listAdminIngredients(query: String? = null): SearchResult<AdminIngredientInfo> =
    this.get("$ADMIN_URL/ingredients") {
        url { query?.let { parameters.append("query", it) } }
    }.let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<SearchResult<AdminIngredientInfo>>(it.bodyAsText())
    }

// ------------------------------------------------------------------ moderation

suspend fun HttpClient.listReportsRaw(status: ReportStatus? = null, targetType: ReportTargetType? = null) =
    this.get("$ADMIN_URL/reports") {
        url {
            status?.let { parameters.append("status", it.name) }
            targetType?.let { parameters.append("targetType", it.name) }
        }
    }

suspend fun HttpClient.listReports(
    status: ReportStatus? = null,
    targetType: ReportTargetType? = null,
): SearchResult<ReportInfo> =
    this.listReportsRaw(status, targetType).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<SearchResult<ReportInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.resolveReportRaw(
    id: Long,
    action: ModerationAction,
    note: String = "",
    suspensionDays: Int = 7,
) = this.post("$ADMIN_URL/reports/$id/resolve") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(ReportResolutionDTO(action = action, note = note, suspensionDays = suspensionDays))
}

suspend fun HttpClient.resolveReport(
    id: Long,
    action: ModerationAction,
    note: String = "",
    suspensionDays: Int = 7,
): ReportInfo =
    this.resolveReportRaw(id, action, note, suspensionDays).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<ReportInfo>(it.bodyAsText())
    }

// ------------------------------------------------------------------------ logs

suspend fun HttpClient.getLogsRaw(level: String? = null, search: String? = null, logger: String? = null) =
    this.get("$ADMIN_URL/logs") {
        url {
            level?.let { parameters.append("level", it) }
            search?.let { parameters.append("search", it) }
            logger?.let { parameters.append("logger", it) }
        }
    }

suspend fun HttpClient.getLogs(level: String? = null, search: String? = null, logger: String? = null): LogPage =
    this.getLogsRaw(level, search, logger).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<LogPage>(it.bodyAsText())
    }

// --------------------------------------------------------------------- storage

suspend fun HttpClient.getStorageOverviewRaw(): HttpResponse = this.get("$ADMIN_URL/storage")

suspend fun HttpClient.getStorageOverview(): AdminStorageOverview =
    this.getStorageOverviewRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminStorageOverview>(it.bodyAsText())
    }

suspend fun HttpClient.listImagesRaw(
    bucket: ImageBucket? = null,
    status: ImageStatus? = null,
    query: String? = null,
    sort: ImageSort? = null,
) = this.get("$ADMIN_URL/storage/images") {
    url {
        bucket?.let { parameters.append("bucket", it.name) }
        status?.let { parameters.append("status", it.name) }
        query?.let { parameters.append("query", it) }
        sort?.let { parameters.append("sort", it.name) }
    }
}

suspend fun HttpClient.listImages(
    bucket: ImageBucket? = null,
    status: ImageStatus? = null,
    query: String? = null,
    sort: ImageSort? = null,
): SearchResult<AdminImageInfo> =
    this.listImagesRaw(bucket, status, query, sort).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<SearchResult<AdminImageInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.deleteImageRaw(bucket: ImageBucket?, file: String?) =
    this.delete("$ADMIN_URL/storage/images") {
        url {
            bucket?.let { parameters.append("bucket", it.name) }
            file?.let { parameters.append("file", it) }
        }
    }

suspend fun HttpClient.cleanupStorageRaw(
    buckets: List<ImageBucket> = emptyList(),
    statuses: List<ImageStatus> = listOf(ImageStatus.STALE, ImageStatus.ORPHAN),
    dryRun: Boolean = false,
) = this.post("$ADMIN_URL/storage/cleanup") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(StorageCleanupDTO(buckets = buckets, statuses = statuses, dryRun = dryRun))
}

suspend fun HttpClient.cleanupStorage(
    buckets: List<ImageBucket> = emptyList(),
    statuses: List<ImageStatus> = listOf(ImageStatus.STALE, ImageStatus.ORPHAN),
    dryRun: Boolean = false,
): AdminStorageCleanupResult =
    this.cleanupStorageRaw(buckets, statuses, dryRun).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AdminStorageCleanupResult>(it.bodyAsText())
    }

// ------------------------------------------------------------ default images

suspend fun HttpClient.listDefaultImagesRaw(): HttpResponse = this.get("$ADMIN_URL/storage/defaults")

suspend fun HttpClient.listDefaultImages(): List<AdminDefaultImageInfo> =
    this.listDefaultImagesRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<List<AdminDefaultImageInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.uploadDefaultImageRaw(image: DefaultImage, bytes: ByteArray) =
    this.post("$ADMIN_URL/storage/defaults/${image.name}") {
        setBody(MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                append(HttpHeaders.ContentDisposition, "filename=\"default.png\"")
            })
        }))
    }

suspend fun HttpClient.resetDefaultImageRaw(image: DefaultImage) =
    this.delete("$ADMIN_URL/storage/defaults/${image.name}")

// -------------------------------------------------------------- mail templates

private const val MAIL_TEMPLATES_URL = "$ADMIN_URL/mails/templates"

suspend fun HttpClient.listMailTemplatesRaw(): HttpResponse = this.get(MAIL_TEMPLATES_URL)

suspend fun HttpClient.listMailTemplates(): List<AdminEmailTemplateInfo> =
    this.listMailTemplatesRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<List<AdminEmailTemplateInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.mailTemplate(key: String): AdminEmailTemplateInfo =
    this.listMailTemplates().first { it.key == key }

suspend fun HttpClient.addMailTemplateRaw(key: String) = this.post(MAIL_TEMPLATES_URL) {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(EmailTemplateKeyDTO(key = key))
}

suspend fun HttpClient.deleteMailTemplateRaw(key: String) = this.delete("$MAIL_TEMPLATES_URL/$key")

suspend fun HttpClient.saveMailTemplateRaw(key: String, locale: Locale, subject: String, body: String) =
    this.put("$MAIL_TEMPLATES_URL/$key/${locale.name}") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(EmailTemplateDTO(subject = subject, body = body))
    }

suspend fun HttpClient.restoreMailTemplateRaw(key: String, locale: Locale) =
    this.delete("$MAIL_TEMPLATES_URL/$key/${locale.name}")

suspend fun HttpClient.previewMailTemplateRaw(key: String, subject: String, body: String) =
    this.post("$MAIL_TEMPLATES_URL/$key/preview") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(EmailPreviewDTO(subject = subject, body = body))
    }

suspend fun HttpClient.previewMailTemplate(key: String, subject: String, body: String): EmailPreviewInfo =
    this.previewMailTemplateRaw(key, subject, body).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<EmailPreviewInfo>(it.bodyAsText())
    }

suspend fun HttpClient.sendTestMailRaw(key: String, recipient: String, locale: Locale = Locale.EN) =
    this.post("$MAIL_TEMPLATES_URL/$key/test") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(EmailTestDTO(recipient = recipient, locale = locale))
    }

/** What mail-service reads. Deliberately not under [ADMIN_URL], and not behind its gate. */
suspend fun HttpClient.getInternalMailTemplatesRaw(): HttpResponse = this.get(INTERNAL_MAIL_TEMPLATES_URL)

suspend fun HttpClient.getInternalMailTemplates(): List<EffectiveEmailTemplate> =
    this.getInternalMailTemplatesRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<List<EffectiveEmailTemplate>>(it.bodyAsText())
    }

// ---------------------------------------------------------- document templates

private const val PDF_TEMPLATES_URL = "$ADMIN_URL/documents/templates"

suspend fun HttpClient.listPdfTemplatesRaw(): HttpResponse = this.get(PDF_TEMPLATES_URL)

suspend fun HttpClient.listPdfTemplates(): List<AdminPdfTemplateInfo> =
    this.listPdfTemplatesRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<List<AdminPdfTemplateInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.pdfTemplate(key: String): AdminPdfTemplateInfo =
    this.listPdfTemplates().first { it.key == key }

suspend fun HttpClient.savePdfTemplateRaw(key: String, locale: Locale, body: String) =
    this.put("$PDF_TEMPLATES_URL/$key/${locale.name}") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(PdfTemplateDTO(body = body))
    }

suspend fun HttpClient.restorePdfTemplateRaw(key: String, locale: Locale) =
    this.delete("$PDF_TEMPLATES_URL/$key/${locale.name}")

suspend fun HttpClient.previewPdfTemplateRaw(key: String, locale: Locale, body: String, recipeId: Long? = null) =
    this.post("$PDF_TEMPLATES_URL/$key/${locale.name}/preview") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(PdfPreviewDTO(body = body, recipeId = recipeId))
    }

suspend fun HttpClient.previewPdfTemplate(key: String, locale: Locale, body: String, recipeId: Long? = null): ByteArray =
    this.previewPdfTemplateRaw(key, locale, body, recipeId).let {
        assertEquals(HttpStatusCode.OK, it.status)
        it.bodyAsBytes()
    }
