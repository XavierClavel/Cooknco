package com.xavierclavel.models

import com.xavierclavel.models.jointables.CookbookUser
import com.xavierclavel.models.jointables.Follow
import com.xavierclavel.models.jointables.Like
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import shared.infodto.AdminUserInfo
import shared.infodto.UserInfo
import shared.enums.AccountStatus
import shared.enums.PremiumStatus
import shared.enums.UnitSystem
import shared.enums.UserRole
import shared.overviewdto.UserOverview
import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import shared.enums.Locale
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(name = "users")
class User (

    @Id
    var id: Long = 0,

    @DbDefault("0")
    var imageVersion: Long = 0,

    @Column(unique = true)
    var username: String = "",

    @DbDefault(value = "")
    val mailEncrypted: String = "",

    @Column(unique = true)
    @DbDefault(value = "")
    val mailHash: String = "",

    var role: UserRole = UserRole.USER,

    var passwordHash: String? = null,

    var googleId: String? = null,

    var bio: String = "",

    //Validation
    @Column(unique = true)
    var token: String = "",
    var tokenEndValidity: LocalDateTime = LocalDateTime.now().plusDays(1),
    var isVerified: Boolean = false,

    //Privacy
    var isAccountPublic: Boolean = true,
    var autoAcceptFollowRequests : Boolean = false,

    /**
     * Whether to mail this account about what the people it follows are up to.
     *
     * Opt-out by default: an address that never asked for these is not one to mail. Read by
     * the fan-out in `NotificationService.onRecipeCreated`, and never by the account mails —
     * a verification link is not a notification and does not stop for this.
     */
    @DbDefault("false")
    var mailNotificationsEnabled: Boolean = false,

    /**
     * Premium with no end, as an operator grants it.
     *
     * Two columns rather than one, on the shape [isBanned] and [suspendedUntil] already
     * have: a permanent grant and a timed one are different things, and squeezing them
     * into one nullable date would need a sentinel — a year 9999 that reads as a real
     * expiry to every query, every listing and every operator looking at the row.
     *
     * Set here, they cannot contradict each other either: forever wins, exactly as a ban
     * wins over a running suspension, so there is no state a reader has to resolve.
     */
    @DbDefault("false")
    var isPremiumForever: Boolean = false,

    /**
     * When a timed grant runs out. Null means no timed grant was ever made — not that one
     * is running — so a date in the past is a grant that has lapsed and is kept rather
     * than cleared: it is what tells an operator the account *was* premium until then.
     */
    var premiumUntil: LocalDateTime? = null,

    //Moderation
    @DbDefault("false")
    var isBanned: Boolean = false,
    /** Set while the account is temporarily locked out; null once the suspension is lifted. */
    var suspendedUntil: LocalDateTime? = null,
    @Column(length = 1023)
    @DbDefault("")
    var moderationNote: String = "",

    var joinDate: LocalDateTime = LocalDateTime.now(),
    /**
     * When the account last signed in — stamped by
     * [com.xavierclavel.services.UserService.registerUserActivity] on every session
     * creation, and by nothing else.
     *
     * It starts at the account's creation, so an account that has never signed in reads as
     * last seen the day it joined rather than as never seen at all. That is the one case
     * where it legitimately equals [joinDate]; every other one was the stamp not being
     * written, which is the bug this used to have.
     *
     * Defaulted *from* [joinDate] rather than from a second `LocalDateTime.now()`, so that
     * "equals the join date" is true by construction. Two `now()` calls differ by nanoseconds
     * and Postgres keeps microseconds, so they agreed only until the pair happened to
     * straddle one — which made the test of that case fail a few runs in a thousand, on a
     * diff that had nothing to do with it.
     */
    var lastActivityDate: LocalDateTime = joinDate,

    @OneToMany(mappedBy = "owner", cascade = [CascadeType.ALL], orphanRemoval = true)
    var recipes: Set<Recipe> = setOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var likes : Set<Like> = setOf(),

    @OneToMany(mappedBy = "follower", cascade = [CascadeType.ALL], orphanRemoval = true)
    var follows: Set<Follow> = setOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var followers: Set<Follow> = setOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var cookbooks: Set<CookbookUser> = setOf(),

    /** Where this account can be pushed to. Emptied by a sign-out, not by the account's end. */
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var devices: Set<Device> = setOf(),

    /** Notifications addressed to this account. Ones it *caused* hang off `Notification.actor`. */
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var notifications: Set<Notification> = setOf(),

    /**
     * The MCP clients this account has approved. Owned by the account the way its devices
     * are: a grant is one person's approval and means nothing without them, so it ends with
     * them. Mapping it here is also what makes the account deletable at all — `oauth_grants`
     * points at `users` ON DELETE RESTRICT like every other table here does, and nothing
     * else clears those rows.
     */
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var oauthGrants: Set<OAuthGrant> = setOf(),

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    var dietaryRestrictions: DietaryRestrictions = DietaryRestrictions(),

    /**
     * The language this account is written to — mails, and the notifications it is pushed.
     *
     * Nullable because "nobody has told us" is a real state and a wrong guess is not: the
     * column was non-null with a default of FR for its whole life while nothing ever wrote
     * it, which is why every mail this product has sent went out in French regardless of who
     * read it. Null now means exactly that nothing has reported one, and the readers fall
     * back rather than believe it ([Locale.FR] stays the last resort, so an account nothing
     * has ever said anything about is treated as it was before).
     *
     * Three things set it, and none of them overwrite a value already there:
     * - a signup, from the `?locale=` its client already sends;
     * - registering a device, adopting that client's language for an account with none —
     *   this is what keeps a single column answerable to `mail-service`, which reads it
     *   directly and has no devices of its own to consult;
     * - the account's own settings, which is the user saying it themselves.
     *
     * A choice therefore survives every later client report, and `devices.locale` is left to
     * describe the handset rather than the person.
     */
    var locale: Locale? = null,

    /**
     * The units this account reads amounts in.
     *
     * Not nullable, unlike [locale], because nothing ever *reports* one: no client knows a
     * handset's preferred ladder the way it knows its language, so there is no report to
     * keep apart from a choice and the column can simply hold the answer. Metric is what
     * every recipe in the product was authored in while there was nothing to choose, so an
     * account that never opens the setting reads exactly what it read before.
     *
     * Display only. A recipe stores the unit its author picked
     * ([com.xavierclavel.models.jointables.RecipeIngredient.unit]) and this never touches
     * it — otherwise reading somebody's recipe in pounds and saving a line of it would
     * rewrite their recipe in pounds.
     */
    @Enumerated(EnumType.STRING)
    @DbDefault("METRIC")
    var unitSystem: UnitSystem = UnitSystem.METRIC,


    ): Model() {

    companion object {
        fun from(userDTO: UserDTO, token: String, passwordHash: String?, mailEncrypted: String, mailHash: String): User {
            return User(
                username = userDTO.username,
                mailEncrypted = mailEncrypted,
                mailHash = mailHash,
                passwordHash = passwordHash,
                googleId = userDTO.googleId,
                token = token,
            )
        }
    }

    fun setRole(role: UserRole) = this.apply { this.role = role }

    /**
     * @param counts the five figures a profile states, resolved by
     *   [com.xavierclavel.services.UserService.countsOf] rather than read off the five collections
     *   they name. Reading them costs five round trips per account, which a listing pays per row —
     *   see `UserFetchPlanTest`, and the note on `LinkPreviewService.publicUser`, which avoids this
     *   DTO for exactly that reason.
     */
    fun toInfo(counts: UserCounts) =
        UserInfo(
            id = this.id,
            version = this.imageVersion,
            username = this.username,
            role = this.role,
            isPremium = this.hasPremiumAccess(),
            joinDate = this.joinDate.toEpochSecond(ZoneOffset.UTC),
            bio = this.bio,
            recipesCount = counts.recipes,
            likesCount = counts.likes,
            cookbooksCount = counts.cookbooks,
            followersCount = counts.followers,
            followsCount = counts.follows,
        )

    fun toOverview() = UserOverview(
        id = this.id,
        role = this.role,
        version = this.imageVersion,
        username = this.username,
    )

    fun merge(userDTO: UserDTO) = this.apply {
        username = userDTO.username
        bio = userDTO.bio
    }

    fun verify() = this.apply {
        isVerified = true
        tokenEndValidity = LocalDateTime.now().minusDays(1)
    }

    fun updatePassword(encryptedPassword: String) = this.apply {
        passwordHash = encryptedPassword
    }

    fun updateSettings(userSettingsDTO: UserSettingsDTO) = this.apply {
        autoAcceptFollowRequests = userSettingsDTO.autoAcceptFollowRequests
        isAccountPublic = userSettingsDTO.isAccountPublic
        // Null is "not saying", not "forget it": a client that predates the field sends
        // nothing, and must not wipe a language chosen from another one
        userSettingsDTO.locale?.let { locale = it }
        // Absent means "leave as it is", for the same reason
        userSettingsDTO.mailNotificationsEnabled?.let { mailNotificationsEnabled = it }
        userSettingsDTO.unitSystem?.let { unitSystem = it }
    }

    /**
     * Takes a language reported by a client, for an account that has none.
     *
     * Never overwrites: whatever is already there was either chosen in the settings or
     * adopted earlier, and a second client's OS language is no reason to revisit it.
     *
     * @return true when this actually recorded something
     */
    fun adoptLocale(reported: Locale): Boolean {
        if (locale != null) return false
        locale = reported
        return true
    }

    // Public accounts have nothing to gate, so they never hold follow requests pending
    fun autoAcceptsFollowRequests() = isAccountPublic || autoAcceptFollowRequests

    /** True while a temporary suspension is still running. */
    fun isSuspended(): Boolean = suspendedUntil?.isAfter(LocalDateTime.now()) == true

    /** True while a grant of either kind is running. A lapsed one is not premium. */
    fun isPremium(): Boolean = isPremiumForever || premiumUntil?.isAfter(LocalDateTime.now()) == true

    /**
     * Whether a premium feature is open to this account — the single rule every gate asks,
     * and the value `UserInfo.isPremium` carries to the clients so that what a screen
     * offers and what a route allows cannot drift apart.
     *
     * An ADMIN passes without a grant. They already reach the whole backoffice, the export
     * was theirs alone before any of this existed, and making them buy a subscription to
     * keep a tool they moderate with would be a regression dressed as a rule.
     */
    fun hasPremiumAccess(): Boolean = role == UserRole.ADMIN || isPremium()

    fun premiumStatus(): PremiumStatus = when {
        isPremiumForever -> PremiumStatus.FOREVER
        premiumUntil?.isAfter(LocalDateTime.now()) == true -> PremiumStatus.UNTIL
        else -> PremiumStatus.NONE
    }

    /**
     * Grants premium with no end.
     *
     * Clears any running expiry, so the row says one thing: an account granted forever
     * after a trial is not one whose trial is still counting down.
     */
    fun grantPremiumForever() = this.apply {
        isPremiumForever = true
        premiumUntil = null
    }

    /**
     * Grants premium until [until].
     *
     * Also clears a permanent grant, which is what makes this the way to *shorten* one:
     * an operator who granted forever by mistake replaces it with a date rather than
     * having to revoke first and re-grant.
     */
    fun grantPremiumUntil(until: LocalDateTime) = this.apply {
        isPremiumForever = false
        premiumUntil = until
    }

    /**
     * Ends premium now, of either kind.
     *
     * The expiry is wiped rather than set to now: a revocation is somebody deciding this
     * account has no grant, which is exactly what null means, and leaving a date behind
     * would show the backoffice a trial that ran out on a day nothing happened on.
     */
    fun revokePremium() = this.apply {
        isPremiumForever = false
        premiumUntil = null
    }

    fun accountStatus(): AccountStatus = when {
        isBanned -> AccountStatus.BANNED
        isSuspended() -> AccountStatus.SUSPENDED
        !isVerified -> AccountStatus.UNVERIFIED
        else -> AccountStatus.ACTIVE
    }

    fun suspend(until: LocalDateTime, reason: String) = this.apply {
        suspendedUntil = until
        moderationNote = reason.take(1023)
    }

    fun ban(reason: String) = this.apply {
        isBanned = true
        suspendedUntil = null
        moderationNote = reason.take(1023)
    }

    /** Clears both a ban and a running suspension. */
    fun reinstate() = this.apply {
        isBanned = false
        suspendedUntil = null
        moderationNote = ""
    }

    /** @param counts resolved by the caller, for the reason [toInfo] gives. */
    fun toAdminInfo(mail: String, reportsAgainstCount: Int, counts: UserCounts) = AdminUserInfo(
        id = this.id,
        version = this.imageVersion,
        username = this.username,
        mail = mail,
        role = this.role,
        status = this.accountStatus(),
        isVerified = this.isVerified,
        isBanned = this.isBanned,
        suspendedUntil = this.suspendedUntil?.toEpochSecond(ZoneOffset.UTC),
        moderationNote = this.moderationNote,
        premiumStatus = this.premiumStatus(),
        premiumUntil = this.premiumUntil?.toEpochSecond(ZoneOffset.UTC),
        bio = this.bio,
        locale = this.locale,
        isAccountPublic = this.isAccountPublic,
        joinDate = this.joinDate.toEpochSecond(ZoneOffset.UTC),
        lastActivityDate = this.lastActivityDate.toEpochSecond(ZoneOffset.UTC),
        recipesCount = counts.recipes,
        likesCount = counts.likes,
        cookbooksCount = counts.cookbooks,
        followersCount = counts.followers,
        followsCount = counts.follows,
        reportsAgainstCount = reportsAgainstCount,
    )

    fun getSettings() = UserSettingsDTO(
        autoAcceptFollowRequests = this.autoAcceptFollowRequests,
        isAccountPublic = this.isAccountPublic,
        locale = this.locale,
        mailNotificationsEnabled = this.mailNotificationsEnabled,
        unitSystem = this.unitSystem,
    )

    fun useToken() {
        this.apply { tokenEndValidity = LocalDateTime.now().minusDays(1) }.update()
    }

    fun isTokenValid(): Boolean {
        return LocalDateTime.now() < this.tokenEndValidity
    }

    fun updateToken(token:String) {
        this.token = token
        this.tokenEndValidity = LocalDateTime.now().plusDays(1)
        update()
    }

    fun increaseVersion() = apply {
        this.imageVersion++;
    }.update()

    fun resetVersion() = apply {
        this.imageVersion = 0
    }.update()
}