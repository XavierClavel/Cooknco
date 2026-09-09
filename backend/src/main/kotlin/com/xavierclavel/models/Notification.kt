package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.enums.NotificationKind
import shared.infodto.UserNotificationInfo
import shared.utils.NotificationWordings
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * One notification, as delivered to one recipient.
 *
 * The wording is stored rendered rather than as a kind plus values. It is what the push
 * carried, so re-rendering it later — against a recipe since retitled, or a user since
 * renamed — would make the in-app list disagree with the notification already sitting on
 * the recipient's device.
 *
 * A row is written whether or not the push reached anything, which is what makes the list
 * the durable half of the feature: a user with no device, or one whose token had died,
 * still finds the notification when they next open the app.
 */
@Entity
@Table(name = "notifications")
class Notification(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(optional = false)
    var user: User? = null,

    @Column(nullable = false)
    var kind: NotificationKind = NotificationKind.ANNOUNCEMENT,

    @Column(nullable = false, length = NotificationWordings.MAX_TITLE_LENGTH)
    var title: String = "",

    @Column(nullable = false, length = NotificationWordings.MAX_BODY_LENGTH)
    var body: String = "",

    /** App-relative path the notification opens, or blank. See `AnnouncementDTO.link`. */
    @Column(nullable = false, length = 511)
    @DbDefault("")
    var link: String = "",

    /**
     * Whoever caused the notification. Null on an announcement, and null once the account
     * that caused it has been deleted — the notification outlives it, like a report does.
     */
    @ManyToOne
    var actor: User? = null,

    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** Null while unread. A timestamp rather than a flag, so "when" is answerable later. */
    var readAt: LocalDateTime? = null,

) : Model() {

    fun markRead() = this.apply {
        if (readAt == null) readAt = LocalDateTime.now()
    }

    fun toInfo() = UserNotificationInfo(
        id = this.id,
        kind = this.kind,
        title = this.title,
        body = this.body,
        link = this.link,
        actor = this.actor?.toOverview(),
        createdAt = this.createdAt.toEpochSecond(ZoneOffset.UTC),
        read = this.readAt != null,
    )
}
