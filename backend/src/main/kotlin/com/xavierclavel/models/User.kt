package com.xavierclavel.models

import com.xavierclavel.models.jointables.CookbookUser
import com.xavierclavel.models.jointables.Follow
import com.xavierclavel.models.jointables.Like
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import shared.infodto.AdminUserInfo
import shared.infodto.UserInfo
import shared.enums.AccountStatus
import shared.enums.UserRole
import shared.overviewdto.UserOverview
import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
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

    //Moderation
    @DbDefault("false")
    var isBanned: Boolean = false,
    /** Set while the account is temporarily locked out; null once the suspension is lifted. */
    var suspendedUntil: LocalDateTime? = null,
    @Column(length = 1023)
    @DbDefault("")
    var moderationNote: String = "",

    var joinDate: LocalDateTime = LocalDateTime.now(),
    var lastActivityDate: LocalDateTime = LocalDateTime.now(),

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

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    var dietaryRestrictions: DietaryRestrictions = DietaryRestrictions(),

    @DbDefault("0")
    var locale: Locale = Locale.FR,


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

    fun registerNewActivity() =
        this.apply {
            lastActivityDate = LocalDateTime.now()
        }

    fun toInfo() =
        UserInfo(
            id = this.id,
            version = this.imageVersion,
            username = this.username,
            role = this.role,
            joinDate = this.joinDate.toEpochSecond(ZoneOffset.UTC),
            bio = this.bio,
            recipesCount = this.recipes.size,
            likesCount = this.likes.size,
            cookbooksCount = this.cookbooks.size,
            followersCount = this.followers.count { !it.pending },
            followsCount = this.follows.count { !it.pending },
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
    }

    // Public accounts have nothing to gate, so they never hold follow requests pending
    fun autoAcceptsFollowRequests() = isAccountPublic || autoAcceptFollowRequests

    /** True while a temporary suspension is still running. */
    fun isSuspended(): Boolean = suspendedUntil?.isAfter(LocalDateTime.now()) == true

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

    fun toAdminInfo(mail: String, reportsAgainstCount: Int) = AdminUserInfo(
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
        bio = this.bio,
        locale = this.locale,
        isAccountPublic = this.isAccountPublic,
        joinDate = this.joinDate.toEpochSecond(ZoneOffset.UTC),
        lastActivityDate = this.lastActivityDate.toEpochSecond(ZoneOffset.UTC),
        recipesCount = this.recipes.size,
        likesCount = this.likes.size,
        cookbooksCount = this.cookbooks.size,
        followersCount = this.followers.count { !it.pending },
        followsCount = this.follows.count { !it.pending },
        reportsAgainstCount = reportsAgainstCount,
    )

    fun getSettings() = UserSettingsDTO(
        autoAcceptFollowRequests = this.autoAcceptFollowRequests,
        isAccountPublic = this.isAccountPublic,
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