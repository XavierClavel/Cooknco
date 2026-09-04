package eu.cooknco

import eu.cooknco.models.EmailTemplate
import eu.cooknco.models.query.QEmailTemplate
import kotlinx.serialization.json.Json
import shared.dto.EffectiveEmailTemplate
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.utils.EmailTemplates
import shared.utils.URL.INTERNAL_MAIL_TEMPLATES_URL
import shared.utils.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The wordings this service sends its mails with.
 *
 * Every mail has two possible sources, in order: the wording an operator saved in the
 * backoffice, and the one packaged in `shared`. The backend owns the first — it is where
 * the backoffice writes — so this reads them from it, and keeps the answer in memory and
 * in [EmailTemplate] rows.
 *
 * The mirror is the point of the design rather than an optimisation. Sending a mail must
 * not depend on the backend being up: a refresh that fails leaves the last wording that
 * did arrive in service, and only an install that has never once reached the backend falls
 * all the way back to the packaged text.
 */
object MailTemplateStore {
    /**
     * How long a fetched set is trusted before the next mail refreshes it.
     *
     * Mails are rare enough that this is at most one request a minute, and an operator
     * rewording a mail should not have to wait long to see it take effect.
     */
    private val TTL = Duration.ofMinutes(1)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The JDK client rather than Ktor's: the caller is the Kafka consumer loop, which is
     * blocking, and this way that stays true without a `runBlocking` around every send.
     */
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val endpoint: String by lazy {
        val base = System.getenv("BACKEND_INTERNAL_URL")?.trimEnd('/') ?: "http://cooknco-backend:8080"
        "$base/$INTERNAL_MAIL_TEMPLATES_URL"
    }

    @Volatile
    private var overrides: Map<Pair<String, Locale>, Pair<String, String>> = emptyMap()

    @Volatile
    private var fetchedAt: Long = 0

    /**
     * Loads the mirror so the first mail after a restart is sent with the wording that was
     * in service before it, whether or not the backend answers.
     */
    fun init() {
        overrides = readMirror()
        logger.info { "Loaded ${overrides.size} mail template(s) from the local mirror" }
        refreshIfStale()
    }

    /** The subject and body to send for a mail the app itself emits. Always something. */
    fun get(kind: EmailTemplateKind, locale: Locale): Pair<String, String> {
        refreshIfStale()
        return overrides[kind.key to locale] ?: EmailTemplates.packaged(kind, locale)
    }

    /**
     * The subject and body to send for any key, or null when there is nothing to send — a
     * kind an operator added and never gave a wording for in this locale.
     */
    fun get(key: String, locale: Locale): Pair<String, String>? {
        EmailTemplateKind.of(key)?.let { return get(it, locale) }
        refreshIfStale()
        return overrides[key to locale]
    }

    // ------------------------------------------------------------------ refreshing

    private fun refreshIfStale() {
        if (System.currentTimeMillis() - fetchedAt < TTL.toMillis()) return
        synchronized(this) {
            // Another thread may have refreshed while this one waited on the lock
            if (System.currentTimeMillis() - fetchedAt < TTL.toMillis()) return
            fetch()?.let { adopt(it) }
            // Stamped whether or not the fetch worked: a backend that is down must not be
            // asked again on every mail, and the wording in memory is still good.
            fetchedAt = System.currentTimeMillis()
        }
    }

    private fun fetch(): List<EffectiveEmailTemplate>? = try {
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            logger.error { "Mail templates refused by $endpoint with status ${response.statusCode()}" }
            null
        } else {
            json.decodeFromString<List<EffectiveEmailTemplate>>(response.body())
        }
    } catch (e: Exception) {
        // Nothing here is worth failing a mail over: the wording already in memory is sent
        logger.error(e) { "Could not refresh mail templates from $endpoint" }
        null
    }

    private fun adopt(fetched: List<EffectiveEmailTemplate>) {
        val next = fetched.associate { (it.key to it.locale) to (it.subject to it.body) }
        if (next != overrides) {
            logger.info { "Mail templates updated: ${next.size} saved wording(s)" }
            writeMirror(fetched)
        }
        overrides = next
    }

    // --------------------------------------------------------------------- mirror

    private fun readMirror(): Map<Pair<String, Locale>, Pair<String, String>> = try {
        QEmailTemplate().findList().associate { (it.key to it.locale) to (it.subject to it.body) }
    } catch (e: Exception) {
        logger.error(e) { "Could not read the mail template mirror" }
        emptyMap()
    }

    /**
     * Replaces the mirror wholesale.
     *
     * A wording an operator restored to the packaged one is absent from what the backend
     * sends, so anything not in the new set has to go — updating in place would leave the
     * deleted override behind and keep sending it.
     */
    private fun writeMirror(fetched: List<EffectiveEmailTemplate>) {
        try {
            QEmailTemplate().delete()
            fetched.forEach {
                EmailTemplate(key = it.key, locale = it.locale, subject = it.subject, body = it.body).save()
            }
        } catch (e: Exception) {
            // The wording is already in memory; the mirror only matters at the next restart
            logger.error(e) { "Could not write the mail template mirror" }
        }
    }
}
