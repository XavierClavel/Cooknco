package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.enums.AiFeature
import shared.enums.AiUsageOutcome
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * One request that reached a model, which feature made it, and what it cost.
 *
 * What the backoffice AI tab's spending figures are summed from, and what the monthly budget
 * is checked against — so it is written for every answer the provider billed, whatever the
 * user got out of it ([outcome]). A request that never reached a model costs nothing and
 * leaves no row. Every AI feature writes here ([feature]), so there is one bill to watch.
 *
 * [cost] is fixed when the row is written, at the prices then in force
 * ([AiSettings]): a month's spend must not change after the fact because an
 * operator corrected a price today.
 *
 * [user] outlives nothing: an account's deletion detaches its rows rather than deleting them
 * (`UserService.deleteUserById`), so the month's total stays what was actually spent while
 * nothing about the person remains.
 */
@Entity
@Table(name = "ai_usage")
class AiUsage(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(optional = true)
    var user: User? = null,

    /** UTC, like the day and month boundaries the allowance and the budget are counted in. */
    @Index
    var createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),

    @Column(nullable = false)
    var feature: AiFeature = AiFeature.PHOTO_IMPORT,

    /** How many images the request carried — pages, for a photo import. */
    @Column(nullable = false)
    var pages: Int = 0,

    /** The model id as configured when it ran, so a change of provider shows in the history. */
    @Column(nullable = false, length = 255)
    var model: String = "",

    /** As the provider reported them, or null when it reported none. */
    var inputTokens: Int? = null,
    var outputTokens: Int? = null,

    @Column(nullable = false, precision = 14, scale = 6)
    var cost: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var outcome: AiUsageOutcome = AiUsageOutcome.READ,

) : Model()
