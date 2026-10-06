package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.models.AiUsage
import com.xavierclavel.models.AiSettings
import com.xavierclavel.models.User
import com.xavierclavel.models.query.QAiUsage
import com.xavierclavel.models.query.QAiSettings
import com.xavierclavel.utils.Configuration
import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.exceptions.TooManyRequestsCause
import com.xavierclavel.exceptions.TooManyRequestsException
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.utils.logger
import io.ebean.DB
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.AiFeature
import shared.enums.AiUsageOutcome
import shared.infodto.AdminAiOverview
import shared.infodto.AiSettingsDTO
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * What the AI features cost, and the limits an operator puts on them.
 *
 * Generic over the feature on purpose: every AI feature shares one provider and one bill, so
 * it records here, spends from one monthly budget and one per-account daily allowance, and
 * shows on one backoffice tab. A feature says which it is ([AiFeature]) and nothing else here
 * changes for it.
 *
 * Kept apart from [PhotoImportService], which reads photos, because this is the money side:
 * the settings the backoffice edits, one [AiUsage] row per billed answer, and the sums the
 * monthly budget and the backoffice tab are read from. Every boundary is UTC, the same one the
 * daily allowance is counted in, so "today" means one thing on both sides.
 */
class AiUsageService : KoinComponent {
    private val configuration: Configuration by inject()
    private val redisService: RedisService by inject()

    companion object {
        /** How many days the tab's chart covers, today included. */
        const val CHART_DAYS = 30
        const val TOP_USERS = 10
        const val RECENT_ENTRIES = 50

        /** Far above anybody's use; a bound so a typo cannot open the tap entirely. */
        const val MAX_DAILY_LIMIT = 1000

        private val CURRENCY = Regex("^[A-Z]{3}$")
        private val MILLION = BigDecimal(1_000_000)
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    /** The settings in force: the saved row, or the defaults when there is none. */
    fun settings(): AiSettingsDTO =
        savedRow()?.toDTO() ?: AiSettingsDTO(dailyLimitPerUser = configuration.ai.dailyRequestsPerUser)

    private fun savedRow(): AiSettings? = QAiSettings().setMaxRows(1).findOne()

    /**
     * Replaces the settings. Each field is refused on its own cause, so the form can point at
     * the one that is wrong.
     */
    fun saveSettings(dto: AiSettingsDTO): AiSettingsDTO {
        if (dto.dailyLimitPerUser !in 0..MAX_DAILY_LIMIT) throw BadRequestException(BadRequestCause.AI_LIMIT_INVALID)
        // A budget of zero would be "closed", which the daily limit already says more plainly.
        val budget = dto.monthlyBudget
        if (budget != null && !(budget > 0.0 && budget.isFinite())) {
            throw BadRequestException(BadRequestCause.AI_BUDGET_INVALID)
        }
        if (listOf(dto.inputPricePerMillion, dto.outputPricePerMillion).any { it < 0.0 || !it.isFinite() }) {
            throw BadRequestException(BadRequestCause.AI_PRICE_INVALID)
        }
        val currency = dto.currency.trim().uppercase()
        if (!CURRENCY.matches(currency)) throw BadRequestException(BadRequestCause.AI_CURRENCY_INVALID)

        val row = savedRow() ?: AiSettings()
        row.dailyLimitPerUser = dto.dailyLimitPerUser
        row.monthlyBudget = dto.monthlyBudget?.toBigDecimal()?.setScale(2, RoundingMode.HALF_UP)
        row.inputPricePerMillion = dto.inputPricePerMillion.toBigDecimal()
        row.outputPricePerMillion = dto.outputPricePerMillion.toBigDecimal()
        row.currency = currency
        if (row.id == 0L) row.insert() else row.update()
        return row.toDTO()
    }

    private fun AiSettings.toDTO() = AiSettingsDTO(
        dailyLimitPerUser = dailyLimitPerUser,
        monthlyBudget = monthlyBudget?.toDouble(),
        inputPricePerMillion = inputPricePerMillion.toDouble(),
        outputPricePerMillion = outputPricePerMillion.toDouble(),
        currency = currency,
    )

    // ── Admission ─────────────────────────────────────────────────────────────

    /** One AI request let through, counted against [userId]'s [day]; what [refund] gives back. */
    class Admission(val userId: Long, val day: String)

    /**
     * Lets one AI request through, or refuses it — the two checks every feature makes before it
     * calls a model, in the order that refuses the most people for the least work.
     *
     * @throws ServiceUnavailableException with `ai_budget_exhausted` once the month's spend has
     *   reached the budget: nobody's fault, so a 503 the app reads as "unavailable"
     * @throws TooManyRequestsException with `ai_daily_limit` past the account's day
     */
    suspend fun admit(userId: Long): Admission {
        if (isBudgetExhausted()) {
            logger.warn { "AI request refused for user $userId: the month's budget is spent" }
            throw ServiceUnavailableException(ServiceUnavailableCause.AI_BUDGET_EXHAUSTED)
        }
        val day = today().toString()
        if (redisService.countAiRequest(userId, day) > settings().dailyLimitPerUser) {
            // Counted and then given back, so somebody hammering the button past the limit
            // does not push tomorrow's first request over it as well.
            redisService.refundAiRequest(userId, day)
            throw TooManyRequestsException(TooManyRequestsCause.AI_DAILY_LIMIT)
        }
        return Admission(userId, day)
    }

    /**
     * Gives an admitted request back to the account's day, when it got no usable answer — the
     * provider down, or an answer that was not what was asked for. The month keeps whatever
     * was billed: that is spent whoever is to blame.
     */
    suspend fun refund(admission: Admission) = redisService.refundAiRequest(admission.userId, admission.day)

    // ── In flight ─────────────────────────────────────────────────────────────

    /** See `Configuration.Ai.maxConcurrentRequests`; shared by every feature, as the provider is. */
    private val slots by lazy { Semaphore(configuration.ai.maxConcurrentRequests) }

    /**
     * Runs one call to the model inside the backend-wide bound on calls in flight, or refuses
     * with `recipe_reader_busy` once it has waited `queueSeconds` for a turn.
     */
    suspend fun <T> withRequestSlot(block: suspend () -> T): T {
        withTimeoutOrNull(configuration.ai.queueSeconds * 1_000) { slots.acquire() }
            ?: throw ServiceUnavailableException(ServiceUnavailableCause.RECIPE_READER_BUSY)
        try {
            return block()
        } finally {
            slots.release()
        }
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * Writes down one billed answer, at the prices in force now.
     *
     * Missing token counts cost nothing rather than being guessed at: a provider that reports
     * no usage makes the spend an underestimate, and the tab shows the gap as a dash.
     */
    fun record(feature: AiFeature, user: User, pages: Int, reading: RecipePhotoReading, outcome: AiUsageOutcome): AiUsage {
        val settings = settings()
        val cost = costOf(reading.inputTokens, settings.inputPricePerMillion) +
            costOf(reading.outputTokens, settings.outputPricePerMillion)
        return AiUsage(
            feature = feature,
            user = user,
            createdAt = LocalDateTime.now(ZoneOffset.UTC),
            pages = pages,
            model = reading.model,
            inputTokens = reading.inputTokens,
            outputTokens = reading.outputTokens,
            cost = cost,
            outcome = outcome,
        ).also { it.insert() }
    }

    private fun costOf(tokens: Int?, pricePerMillion: Double): BigDecimal =
        if (tokens == null) BigDecimal.ZERO
        else BigDecimal(tokens).multiply(pricePerMillion.toBigDecimal()).divide(MILLION, 6, RoundingMode.HALF_UP)

    // ── The budget ────────────────────────────────────────────────────────────

    /**
     * Whether this month's spend has reached the budget. Checked before a reading starts, so
     * the readings already in flight can take the month slightly past it — by at most
     * `maxConcurrentRequests` requests, which is the price of not holding a lock across a call
     * that takes seconds.
     */
    fun isBudgetExhausted(): Boolean {
        val budget = settings().monthlyBudget ?: return false
        return usageSince(startOfMonth()).cost >= budget
    }

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)
    private fun startOfMonth(): LocalDateTime = today().withDayOfMonth(1).atStartOfDay()

    // ── The backoffice tab ────────────────────────────────────────────────────

    fun overview(): AdminAiOverview {
        val ai = configuration.ai
        val saved = savedRow()
        val monthStart = startOfMonth()
        val dayStart = today().atStartOfDay()
        return AdminAiOverview(
            provider = AdminAiOverview.Provider(
                configured = ai.isConfigured,
                baseUrl = ai.baseUrl,
                model = ai.model,
            ),
            settings = settings(),
            settingsSaved = saved != null,
            settingsUpdatedAt = saved?.updatedAt?.toEpochSecond(ZoneOffset.UTC),
            month = usageSince(monthStart),
            today = usageSince(dayStart),
            days = days(),
            features = features(monthStart),
            topUsers = topUsers(monthStart, dayStart),
            recent = recent(),
        )
    }

    /*
     * The sums below are aggregates returning no entity, which is what DB.sqlQuery is kept
     * for (CLAUDE.md, "All queries go through query beans"). Plain SQL names the real columns.
     */

    private fun usageSince(since: LocalDateTime): AdminAiOverview.Usage {
        val row = DB.sqlQuery(
            """
            select count(*) as requests,
                   coalesce(sum(input_tokens), 0) as input_tokens,
                   coalesce(sum(output_tokens), 0) as output_tokens,
                   coalesce(sum(cost), 0) as cost
            from ai_usage where created_at >= :since
            """.trimIndent()
        ).setParameter("since", since).findOne()
        return AdminAiOverview.Usage(
            since = since.toEpochSecond(ZoneOffset.UTC),
            requests = row?.getLong("requests")?.toInt() ?: 0,
            inputTokens = row?.getLong("input_tokens") ?: 0,
            outputTokens = row?.getLong("output_tokens") ?: 0,
            cost = row?.getBigDecimal("cost")?.toDouble() ?: 0.0,
        )
    }

    /** Every day of the chart, the empty ones included, so the bars line up with the calendar. */
    private fun days(): List<AdminAiOverview.Day> {
        val first = today().minusDays((CHART_DAYS - 1).toLong())
        val byDay = DB.sqlQuery(
            """
            select to_char(created_at, 'YYYY-MM-DD') as day,
                   count(*) as requests,
                   coalesce(sum(input_tokens), 0) as input_tokens,
                   coalesce(sum(output_tokens), 0) as output_tokens,
                   coalesce(sum(cost), 0) as cost
            from ai_usage where created_at >= :since
            group by to_char(created_at, 'YYYY-MM-DD')
            """.trimIndent()
        ).setParameter("since", first.atStartOfDay()).findList()
            .associateBy { it.getString("day") }
        return (0 until CHART_DAYS).map { offset ->
            val day = first.plusDays(offset.toLong())
            val row = byDay[day.toString()]
            AdminAiOverview.Day(
                date = day.toString(),
                requests = row?.getLong("requests")?.toInt() ?: 0,
                inputTokens = row?.getLong("input_tokens") ?: 0,
                outputTokens = row?.getLong("output_tokens") ?: 0,
                cost = row?.getBigDecimal("cost")?.toDouble() ?: 0.0,
            )
        }
    }

    /** Every feature, the idle ones at zero, so a feature nobody uses still shows as one. */
    private fun features(monthStart: LocalDateTime): List<AdminAiOverview.FeatureUsage> {
        val byFeature = DB.sqlQuery(
            """
            select feature,
                   count(*) as requests,
                   coalesce(sum(input_tokens), 0) as input_tokens,
                   coalesce(sum(output_tokens), 0) as output_tokens,
                   coalesce(sum(cost), 0) as cost
            from ai_usage where created_at >= :since
            group by feature
            """.trimIndent()
        ).setParameter("since", monthStart).findList()
            // Stored by ordinal, which is what plain SQL hands back.
            .associateBy { it.getInteger("feature") }
        return AiFeature.entries.map { feature ->
            val row = byFeature[feature.ordinal]
            AdminAiOverview.FeatureUsage(
                feature = feature,
                requests = row?.getLong("requests")?.toInt() ?: 0,
                inputTokens = row?.getLong("input_tokens") ?: 0,
                outputTokens = row?.getLong("output_tokens") ?: 0,
                cost = row?.getBigDecimal("cost")?.toDouble() ?: 0.0,
            )
        }
    }

    private fun topUsers(monthStart: LocalDateTime, dayStart: LocalDateTime): List<AdminAiOverview.UserUsage> =
        DB.sqlQuery(
            """
            select p.user_id as user_id,
                   u.username as username,
                   count(*) as requests,
                   count(*) filter (where p.created_at >= :day) as requests_today,
                   coalesce(sum(p.input_tokens), 0) as input_tokens,
                   coalesce(sum(p.output_tokens), 0) as output_tokens,
                   coalesce(sum(p.cost), 0) as cost
            from ai_usage p left join users u on u.id = p.user_id
            where p.created_at >= :month
            group by p.user_id, u.username
            order by sum(p.cost) desc, count(*) desc
            limit :limit
            """.trimIndent()
        ).setParameter("month", monthStart).setParameter("day", dayStart).setParameter("limit", TOP_USERS)
            .findList()
            .map {
                AdminAiOverview.UserUsage(
                    userId = it.getLong("user_id"),
                    username = it.getString("username"),
                    requests = it.getLong("requests").toInt(),
                    requestsToday = it.getLong("requests_today").toInt(),
                    inputTokens = it.getLong("input_tokens"),
                    outputTokens = it.getLong("output_tokens"),
                    cost = it.getBigDecimal("cost").toDouble(),
                )
            }

    private fun recent(): List<AdminAiOverview.Entry> =
        QAiUsage()
            .user.fetch()
            .orderBy().createdAt.desc()
            .orderBy().id.desc()
            .setMaxRows(RECENT_ENTRIES)
            .findList()
            .map {
                AdminAiOverview.Entry(
                    id = it.id,
                    createdAt = it.createdAt.toEpochSecond(ZoneOffset.UTC),
                    feature = it.feature,
                    userId = it.user?.id,
                    username = it.user?.username,
                    pages = it.pages,
                    model = it.model,
                    inputTokens = it.inputTokens,
                    outputTokens = it.outputTokens,
                    cost = it.cost.toDouble(),
                    outcome = it.outcome,
                )
            }

    /** Keeps an account's spend in the totals while forgetting whose it was. See [AiUsage]. */
    fun detachUser(userId: Long) {
        QAiUsage().user.id.eq(userId).findList().forEach { it.user = null; it.update() }
    }
}
