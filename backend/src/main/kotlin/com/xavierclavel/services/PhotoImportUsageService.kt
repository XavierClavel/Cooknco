package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.models.PhotoImport
import com.xavierclavel.models.PhotoImportSettings
import com.xavierclavel.models.User
import com.xavierclavel.models.query.QPhotoImport
import com.xavierclavel.models.query.QPhotoImportSettings
import com.xavierclavel.utils.Configuration
import io.ebean.DB
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.PhotoImportOutcome
import shared.infodto.AdminPhotoImportOverview
import shared.infodto.PhotoImportSettingsDTO
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * What the photo import costs, and the limits an operator puts on it.
 *
 * Kept apart from [PhotoImportService], which reads photos, because this is the money side:
 * the settings the backoffice edits, one [PhotoImport] row per billed answer, and the sums the
 * monthly budget and the backoffice tab are read from. Every boundary is UTC, the same one the
 * daily allowance is counted in, so "today" means one thing on both sides.
 */
class PhotoImportUsageService : KoinComponent {
    private val configuration: Configuration by inject()

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
    fun settings(): PhotoImportSettingsDTO =
        savedRow()?.toDTO() ?: PhotoImportSettingsDTO(dailyLimitPerUser = configuration.photoImport.dailyReadsPerUser)

    private fun savedRow(): PhotoImportSettings? = QPhotoImportSettings().setMaxRows(1).findOne()

    /**
     * Replaces the settings. Each field is refused on its own cause, so the form can point at
     * the one that is wrong.
     */
    fun saveSettings(dto: PhotoImportSettingsDTO): PhotoImportSettingsDTO {
        if (dto.dailyLimitPerUser !in 0..MAX_DAILY_LIMIT) throw BadRequestException(BadRequestCause.PHOTO_IMPORT_LIMIT_INVALID)
        // A budget of zero would be "closed", which the daily limit already says more plainly.
        val budget = dto.monthlyBudget
        if (budget != null && !(budget > 0.0 && budget.isFinite())) {
            throw BadRequestException(BadRequestCause.PHOTO_IMPORT_BUDGET_INVALID)
        }
        if (listOf(dto.inputPricePerMillion, dto.outputPricePerMillion).any { it < 0.0 || !it.isFinite() }) {
            throw BadRequestException(BadRequestCause.PHOTO_IMPORT_PRICE_INVALID)
        }
        val currency = dto.currency.trim().uppercase()
        if (!CURRENCY.matches(currency)) throw BadRequestException(BadRequestCause.PHOTO_IMPORT_CURRENCY_INVALID)

        val row = savedRow() ?: PhotoImportSettings()
        row.dailyLimitPerUser = dto.dailyLimitPerUser
        row.monthlyBudget = dto.monthlyBudget?.toBigDecimal()?.setScale(2, RoundingMode.HALF_UP)
        row.inputPricePerMillion = dto.inputPricePerMillion.toBigDecimal()
        row.outputPricePerMillion = dto.outputPricePerMillion.toBigDecimal()
        row.currency = currency
        if (row.id == 0L) row.insert() else row.update()
        return row.toDTO()
    }

    private fun PhotoImportSettings.toDTO() = PhotoImportSettingsDTO(
        dailyLimitPerUser = dailyLimitPerUser,
        monthlyBudget = monthlyBudget?.toDouble(),
        inputPricePerMillion = inputPricePerMillion.toDouble(),
        outputPricePerMillion = outputPricePerMillion.toDouble(),
        currency = currency,
    )

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * Writes down one billed answer, at the prices in force now.
     *
     * Missing token counts cost nothing rather than being guessed at: a provider that reports
     * no usage makes the spend an underestimate, and the tab shows the gap as a dash.
     */
    fun record(user: User, pages: Int, reading: RecipePhotoReading, outcome: PhotoImportOutcome): PhotoImport {
        val settings = settings()
        val cost = costOf(reading.inputTokens, settings.inputPricePerMillion) +
            costOf(reading.outputTokens, settings.outputPricePerMillion)
        return PhotoImport(
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
     * `maxConcurrentReads` imports, which is the price of not holding a lock across a call
     * that takes seconds.
     */
    fun isBudgetExhausted(): Boolean {
        val budget = settings().monthlyBudget ?: return false
        return usageSince(startOfMonth()).cost >= budget
    }

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)
    private fun startOfMonth(): LocalDateTime = today().withDayOfMonth(1).atStartOfDay()

    // ── The backoffice tab ────────────────────────────────────────────────────

    fun overview(): AdminPhotoImportOverview {
        val photoImport = configuration.photoImport
        val saved = savedRow()
        val monthStart = startOfMonth()
        val dayStart = today().atStartOfDay()
        return AdminPhotoImportOverview(
            provider = AdminPhotoImportOverview.Provider(
                configured = photoImport.isConfigured,
                baseUrl = photoImport.baseUrl,
                model = photoImport.model,
            ),
            settings = settings(),
            settingsSaved = saved != null,
            settingsUpdatedAt = saved?.updatedAt?.toEpochSecond(ZoneOffset.UTC),
            month = usageSince(monthStart),
            today = usageSince(dayStart),
            days = days(),
            topUsers = topUsers(monthStart, dayStart),
            recent = recent(),
        )
    }

    /*
     * The sums below are aggregates returning no entity, which is what DB.sqlQuery is kept
     * for (CLAUDE.md, "All queries go through query beans"). Plain SQL names the real columns.
     */

    private fun usageSince(since: LocalDateTime): AdminPhotoImportOverview.Usage {
        val row = DB.sqlQuery(
            """
            select count(*) as imports,
                   coalesce(sum(input_tokens), 0) as input_tokens,
                   coalesce(sum(output_tokens), 0) as output_tokens,
                   coalesce(sum(cost), 0) as cost
            from photo_imports where created_at >= :since
            """.trimIndent()
        ).setParameter("since", since).findOne()
        return AdminPhotoImportOverview.Usage(
            since = since.toEpochSecond(ZoneOffset.UTC),
            imports = row?.getLong("imports")?.toInt() ?: 0,
            inputTokens = row?.getLong("input_tokens") ?: 0,
            outputTokens = row?.getLong("output_tokens") ?: 0,
            cost = row?.getBigDecimal("cost")?.toDouble() ?: 0.0,
        )
    }

    /** Every day of the chart, the empty ones included, so the bars line up with the calendar. */
    private fun days(): List<AdminPhotoImportOverview.Day> {
        val first = today().minusDays((CHART_DAYS - 1).toLong())
        val byDay = DB.sqlQuery(
            """
            select to_char(created_at, 'YYYY-MM-DD') as day,
                   count(*) as imports,
                   coalesce(sum(input_tokens), 0) as input_tokens,
                   coalesce(sum(output_tokens), 0) as output_tokens,
                   coalesce(sum(cost), 0) as cost
            from photo_imports where created_at >= :since
            group by to_char(created_at, 'YYYY-MM-DD')
            """.trimIndent()
        ).setParameter("since", first.atStartOfDay()).findList()
            .associateBy { it.getString("day") }
        return (0 until CHART_DAYS).map { offset ->
            val day = first.plusDays(offset.toLong())
            val row = byDay[day.toString()]
            AdminPhotoImportOverview.Day(
                date = day.toString(),
                imports = row?.getLong("imports")?.toInt() ?: 0,
                inputTokens = row?.getLong("input_tokens") ?: 0,
                outputTokens = row?.getLong("output_tokens") ?: 0,
                cost = row?.getBigDecimal("cost")?.toDouble() ?: 0.0,
            )
        }
    }

    private fun topUsers(monthStart: LocalDateTime, dayStart: LocalDateTime): List<AdminPhotoImportOverview.UserUsage> =
        DB.sqlQuery(
            """
            select p.user_id as user_id,
                   u.username as username,
                   count(*) as imports,
                   count(*) filter (where p.created_at >= :day) as imports_today,
                   coalesce(sum(p.input_tokens), 0) as input_tokens,
                   coalesce(sum(p.output_tokens), 0) as output_tokens,
                   coalesce(sum(p.cost), 0) as cost
            from photo_imports p left join users u on u.id = p.user_id
            where p.created_at >= :month
            group by p.user_id, u.username
            order by sum(p.cost) desc, count(*) desc
            limit :limit
            """.trimIndent()
        ).setParameter("month", monthStart).setParameter("day", dayStart).setParameter("limit", TOP_USERS)
            .findList()
            .map {
                AdminPhotoImportOverview.UserUsage(
                    userId = it.getLong("user_id"),
                    username = it.getString("username"),
                    imports = it.getLong("imports").toInt(),
                    importsToday = it.getLong("imports_today").toInt(),
                    inputTokens = it.getLong("input_tokens"),
                    outputTokens = it.getLong("output_tokens"),
                    cost = it.getBigDecimal("cost").toDouble(),
                )
            }

    private fun recent(): List<AdminPhotoImportOverview.Entry> =
        QPhotoImport()
            .user.fetch()
            .orderBy().createdAt.desc()
            .orderBy().id.desc()
            .setMaxRows(RECENT_ENTRIES)
            .findList()
            .map {
                AdminPhotoImportOverview.Entry(
                    id = it.id,
                    createdAt = it.createdAt.toEpochSecond(ZoneOffset.UTC),
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

    /** Keeps an account's spend in the totals while forgetting whose it was. See [PhotoImport]. */
    fun detachUser(userId: Long) {
        QPhotoImport().user.id.eq(userId).findList().forEach { it.user = null; it.update() }
    }
}
