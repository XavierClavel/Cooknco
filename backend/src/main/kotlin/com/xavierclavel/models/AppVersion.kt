package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.WhenModified
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import shared.enums.AppPlatform
import shared.infodto.AdminAppVersionInfo
import shared.utils.AppVersions
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The builds of one client platform an operator allows to run.
 *
 * At most one row per platform, and a platform with no row is not gated at all — the same
 * override-over-a-floor shape as [PdfTemplate] and [EmailTemplate], for a sharper reason:
 * the floor here is "every build runs". A fresh install, a restored backup or a wiped table
 * therefore lets everybody in rather than locking everybody out, which is the only failure
 * direction that is recoverable from a phone.
 */
@Entity
@Table(name = "app_versions")
class AppVersion(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(unique = true, nullable = false)
    var platform: AppPlatform = AppPlatform.ANDROID,

    /** The oldest build allowed to run. Anything below it is told to update and stop. */
    @Column(name = "minimum_version", nullable = false, length = AppVersions.MAX_LENGTH)
    var minimumVersion: String = "",

    /** The newest build published. Between this and [minimumVersion] is a nudge. */
    @Column(name = "latest_version", nullable = false, length = AppVersions.MAX_LENGTH)
    var latestVersion: String = "",

    /** Where a build that has to update is sent. Never empty: see `AppVersionService.save`. */
    @Column(name = "store_url", nullable = false, length = 511)
    var storeUrl: String = "",

    @WhenModified
    var updatedAt: LocalDateTime = LocalDateTime.now(),

) : Model() {
    fun toInfo() = AdminAppVersionInfo(
        platform = platform,
        configured = true,
        minimumVersion = minimumVersion,
        latestVersion = latestVersion,
        storeUrl = storeUrl,
        updatedAt = updatedAt.toEpochSecond(ZoneOffset.UTC),
    )
}
