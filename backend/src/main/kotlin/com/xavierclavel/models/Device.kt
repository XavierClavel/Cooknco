package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.WhenModified
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.enums.DevicePlatform
import shared.enums.Locale
import java.time.LocalDateTime

/**
 * Somewhere a user can be pushed to.
 *
 * The [token] is unique across the table rather than per user: FCM issues one per app
 * install, so two rows holding the same one would mean pushing twice to the same handset,
 * and a device that changed hands would keep receiving the previous owner's notifications.
 * Registering a token that already exists therefore re-points it at the caller.
 *
 * Rows are removed either by the client (a sign-out) or by [com.xavierclavel.services.PushSender]
 * when FCM reports the token as dead. Nothing expires them on a timer: a token stays valid
 * across app restarts and reboots, so an install that is simply idle is not stale.
 */
@Entity
@Table(name = "devices")
class Device(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(optional = false)
    var user: User? = null,

    /**
     * Registration token. 4096 rather than the ~163 characters FCM issues today: the format
     * is opaque and documented as growable, and a truncated token is a silently dead device.
     */
    @Column(unique = true, nullable = false, length = 4095)
    var token: String = "",

    @Column(nullable = false)
    var platform: DevicePlatform = DevicePlatform.ANDROID,

    /**
     * The language this client is running in, as it reported at registration.
     *
     * The only locale any client actually reports: nothing in the app assigns `users.locale`,
     * so it sits at its default for everybody and is useless to write notifications from.
     * `DeviceService.readingLocaleOf` therefore picks the most recently registered device's,
     * and an announcement's language filter narrows on this column.
     *
     * A user whose devices disagree still gets one wording, because there is one
     * `notifications` row per recipient and it has to be what was pushed. The most recent
     * device wins, on the grounds that it is the client they last used.
     */
    @Column(nullable = false)
    var locale: Locale = Locale.EN,

    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** Touched on every re-registration, which clients do at each launch. */
    @WhenModified
    var lastSeenAt: LocalDateTime = LocalDateTime.now(),

) : Model()
