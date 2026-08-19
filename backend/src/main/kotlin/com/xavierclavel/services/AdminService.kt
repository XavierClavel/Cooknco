package com.xavierclavel.services

import com.xavierclavel.logging.LogBuffer
import com.xavierclavel.models.query.QIngredient
import com.xavierclavel.models.query.QRecipe
import com.xavierclavel.models.query.QUser
import io.ebean.DB
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.AccountStatus
import shared.enums.IngredientType
import shared.enums.LogLevel
import shared.enums.ReportTargetType
import shared.enums.Sort
import shared.enums.TimeGranularity
import shared.enums.UserRole
import shared.infodto.AdminIngredientInfo
import shared.infodto.AdminOverview
import shared.infodto.AdminTrendPoint
import shared.infodto.AdminTrends
import shared.infodto.AdminRecipeInfo
import shared.infodto.AdminUserInfo
import java.time.LocalDateTime
import java.util.Properties

/**
 * Read side of the admin backoffice: aggregate figures and the account, recipe and
 * ingredient listings. Anything that changes state lives in [ModerationService] or in the
 * existing per-domain services.
 */
class AdminService: KoinComponent {
    val userService: UserService by inject()
    val recipeService: RecipeService by inject()
    val ingredientService: IngredientService by inject()
    val cookbookService: CookbookService by inject()
    val likeService: LikeService by inject()
    val followService: FollowService by inject()
    val moderationService: ModerationService by inject()

    fun buildOverview(): AdminOverview {
        val oneWeekAgo = LocalDateTime.now().minusWeeks(1)
        return AdminOverview(
            usersCount = userService.countAll(),
            activeUsersCount = userService.countActiveUsers(),
            unverifiedUsersCount = userService.countUnverifiedUsers(),
            suspendedUsersCount = userService.countSuspendedUsers(),
            bannedUsersCount = userService.countBannedUsers(),
            recipesCount = recipeService.countAll(),
            hiddenRecipesCount = recipeService.countHidden(),
            ingredientsCount = ingredientService.countAll(),
            cookbooksCount = cookbookService.countAll(),
            likesCount = likeService.countAll(),
            followsCount = followService.countAll(),
            pendingReportsCount = moderationService.countPendingReports(),
            newUsersLastWeek = userService.countUsersJoinedSince(oneWeekAgo),
            newRecipesLastWeek = recipeService.countCreatedSince(oneWeekAgo),
            errorsInLogBuffer = LogBuffer.countAtLeast(LogLevel.ERROR),
            version = readVersion(),
        )
    }

    // ----------------------------------------------------------------- trends

    companion object {
        /** Guards against a request asking for an unbounded number of buckets. */
        const val MIN_BUCKETS = 2
        const val MAX_BUCKETS = 120
    }

    /**
     * Activity per time bucket, for the dashboard trend charts.
     *
     * Buckets are generated rather than derived from the rows, so a period with no
     * activity comes back as an explicit zero instead of a missing point — a chart
     * that silently skips empty weeks misreads as continuous activity.
     *
     * @param bucketCount how many buckets back from the current one, clamped to
     *   [MIN_BUCKETS]..[MAX_BUCKETS]
     */
    fun buildTrends(granularity: TimeGranularity, bucketCount: Int): AdminTrends {
        val count = bucketCount.coerceIn(MIN_BUCKETS, MAX_BUCKETS)
        // Both come from the enum, never from request text
        val unit = granularity.sqlUnit
        val step = granularity.sqlInterval

        val sql = """
            with buckets as (
                select generate_series(
                    date_trunc('$unit', now()) - interval '$step' * (:count - 1),
                    date_trunc('$unit', now()),
                    interval '$step'
                ) as bucket_start
            )
            select
                to_char(b.bucket_start, 'YYYY-MM-DD') as bucket,
                (select count(*) from users u
                    where date_trunc('$unit', u.join_date) = b.bucket_start) as new_users,
                (select count(*) from recipes r
                    where date_trunc('$unit', r.creation_date) = b.bucket_start) as new_recipes,
                (select count(*) from reports rp
                    where date_trunc('$unit', rp.creation_date) = b.bucket_start) as new_reports,
                (select count(*) from users u
                    where u.join_date < b.bucket_start + interval '$step') as total_users,
                (select count(*) from recipes r
                    where r.creation_date < b.bucket_start + interval '$step') as total_recipes
            from buckets b
            order by b.bucket_start
        """.trimIndent()

        val points = DB.sqlQuery(sql)
            .setParameter("count", count)
            .findList()
            .map {
                AdminTrendPoint(
                    bucket = it.getString("bucket"),
                    newUsers = it.getLong("new_users").toInt(),
                    newRecipes = it.getLong("new_recipes").toInt(),
                    newReports = it.getLong("new_reports").toInt(),
                    totalUsers = it.getLong("total_users").toInt(),
                    totalRecipes = it.getLong("total_recipes").toInt(),
                )
            }

        return AdminTrends(
            granularity = granularity,
            points = points,
            previous = previousWindowTotals(granularity, count),
        )
    }

    /** Totals for the window immediately before the charted one, for the deltas. */
    private fun previousWindowTotals(granularity: TimeGranularity, count: Int): AdminTrends.AdminTrendTotals {
        val unit = granularity.sqlUnit
        val step = granularity.sqlInterval
        val sql = """
            with window_bounds as (
                select
                    date_trunc('$unit', now()) - interval '$step' * (:count * 2 - 1) as start_at,
                    date_trunc('$unit', now()) - interval '$step' * (:count - 1)     as end_at
            )
            select
                (select count(*) from users u, window_bounds w
                    where u.join_date >= w.start_at and u.join_date < w.end_at) as new_users,
                (select count(*) from recipes r, window_bounds w
                    where r.creation_date >= w.start_at and r.creation_date < w.end_at) as new_recipes,
                (select count(*) from reports rp, window_bounds w
                    where rp.creation_date >= w.start_at and rp.creation_date < w.end_at) as new_reports
        """.trimIndent()

        val row = DB.sqlQuery(sql).setParameter("count", count).findOne()
            ?: return AdminTrends.AdminTrendTotals(0, 0, 0)
        return AdminTrends.AdminTrendTotals(
            newUsers = row.getLong("new_users").toInt(),
            newRecipes = row.getLong("new_recipes").toInt(),
            newReports = row.getLong("new_reports").toInt(),
        )
    }

    // ------------------------------------------------------------------ users

    /**
     * @param query matched against the username, or against the exact mail address when it
     *   looks like one — mail is stored encrypted, so it is only searchable by exact match
     */
    fun searchUsers(
        query: String?,
        role: UserRole?,
        status: AccountStatus?,
        paging: Paging,
    ): Pair<Int, List<AdminUserInfo>> {
        val ebeanQuery = QUser()
            .apply {
                if (query.isNullOrBlank()) return@apply
                if (query.contains("@")) this.mailHash.eq(userService.hashMail(query))
                else this.username.ilike("%$query%")
            }
            .apply { if (role != null) this.role.eq(role) }
            .applyStatus(status)

        val count = ebeanQuery.findCount()
        val users = ebeanQuery
            .orderBy().joinDate.desc()
            .setPaging(paging)
            .findList()

        val reportCounts = moderationService.countReportsAgainstUsers(users.map { it.id })
        return Pair(
            count,
            users.map { it.toAdminInfo(userService.readMail(it), reportCounts[it.id] ?: 0) },
        )
    }

    fun getUser(id: Long): AdminUserInfo =
        moderationService.adminInfoOf(userService.getEntityById(id))

    /** Mirrors the precedence in `User.accountStatus()`, so filters agree with the badge shown. */
    private fun QUser.applyStatus(status: AccountStatus?): QUser = when (status) {
        null -> this
        AccountStatus.BANNED -> this.isBanned.eq(true)
        AccountStatus.SUSPENDED -> this.isBanned.eq(false).suspendedUntil.gt(LocalDateTime.now())
        AccountStatus.UNVERIFIED -> this.notSuspended().isVerified.eq(false)
        AccountStatus.ACTIVE -> this.notSuspended().isVerified.eq(true)
    }

    private fun QUser.notSuspended(): QUser =
        this.isBanned.eq(false)
            .or()
                .suspendedUntil.isNull()
                .suspendedUntil.le(LocalDateTime.now())
            .endOr()

    // ---------------------------------------------------------------- recipes

    /**
     * @param hidden when set, restricts to hidden or to visible recipes
     * @param reportedOnly when true, only recipes with at least one pending report
     */
    fun searchRecipes(
        query: String?,
        ownerId: Long?,
        hidden: Boolean?,
        reportedOnly: Boolean,
        sort: Sort,
        paging: Paging,
    ): Pair<Int, List<AdminRecipeInfo>> {
        val reportedRecipeIds by lazy { moderationService.pendingReportTargetIds(ReportTargetType.RECIPE) }

        val ebeanQuery = QRecipe()
            .apply { if (!query.isNullOrBlank()) this.title.ilike("%$query%") }
            .apply { if (ownerId != null) this.owner.id.eq(ownerId) }
            .apply { if (hidden != null) this.isHidden.eq(hidden) }
            .apply {
                if (!reportedOnly) return@apply
                // An empty id set would be an unbounded `in ()`, so short-circuit on 0
                if (reportedRecipeIds.isEmpty()) this.id.eq(-1) else this.id.`in`(reportedRecipeIds)
            }
            .adminSort(sort)

        val count = ebeanQuery.findCount()
        val recipes = ebeanQuery.setPaging(paging).findList()
        val reportCounts = moderationService.countPendingReportsOn(ReportTargetType.RECIPE, recipes.map { it.id })
        return Pair(count, recipes.map { it.toAdminInfo(reportCounts[it.id] ?: 0) })
    }

    fun getRecipe(id: Long): AdminRecipeInfo =
        recipeService.getEntityById(id)
            .toAdminInfo(moderationService.countPendingReportsOn(ReportTargetType.RECIPE, id))

    private fun QRecipe.adminSort(sort: Sort): QRecipe = when (sort) {
        Sort.NAME_ASCENDING -> this.orderBy().title.asc()
        Sort.NAME_DESCENDING -> this.orderBy().title.desc()
        Sort.DATE_ASCENDING -> this.orderBy().creationDate.asc()
        else -> this.orderBy().creationDate.desc()
    }

    // ------------------------------------------------------------ ingredients

    /**
     * Ingredient catalogue with usage counts, resolved in one grouped query so the table
     * does not cost a count per row.
     */
    fun searchIngredients(
        query: String?,
        type: IngredientType?,
        paging: Paging,
    ): Pair<Int, List<AdminIngredientInfo>> {
        val ebeanQuery = QIngredient()
            .apply {
                if (query.isNullOrBlank()) return@apply
                // Matched with an exists rather than by navigating to translations: this
                // search spans every locale, and a join would return an ingredient once
                // per matching translation
                this.raw(
                    """
                    exists (
                        select 1 from ingredient_translation t
                        where t.ingredient_id = ${QIngredient.Alias.id}
                        and t.name ilike ?
                    )
                    """.trimIndent(),
                    "%$query%",
                )
            }
            .apply { if (type != null) this.type.eq(type) }

        val count = ebeanQuery.findCount()
        val ingredients = ebeanQuery
            .orderBy().id.asc()
            .setPaging(paging)
            .findList()

        val usage = ingredientService.countRecipesByIngredient(ingredients.map { it.id })
        return Pair(
            count,
            ingredients.map {
                AdminIngredientInfo(
                    id = it.id,
                    name = it.translations.associate { t -> t.locale to t.name },
                    type = it.type,
                    calories = it.calories,
                    recipesCount = usage[it.id] ?: 0,
                    allowedTypes = it.allowedTypes(),
                    defaultUnit = it.defaultUnit,
                )
            },
        )
    }

    private fun readVersion(): String =
        try {
            Properties()
                .apply { load(this@AdminService.javaClass.getResourceAsStream("/version.properties")) }
                .getProperty("version") ?: "unknown"
        } catch (e: Exception) {
            "dev"
        }
}
