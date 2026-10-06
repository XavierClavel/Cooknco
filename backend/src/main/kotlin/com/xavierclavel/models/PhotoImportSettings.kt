package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.WhenModified
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * The operator's limits and prices for the photo import, edited in the backoffice.
 *
 * At most one row, and no row means the defaults (`Configuration.PhotoImport` for the daily
 * allowance, no budget and no prices) — the override-over-a-floor shape [AppVersion] and
 * [PdfTemplate] have, so a wiped table degrades to the configured behaviour rather than to
 * none.
 *
 * The prices are the operator's statement of what the provider charges, not something read
 * from it: no provider's API says what a token costs. They are what turns tokens into the
 * spend the budget is checked against, so with no prices set the budget can never be reached
 * — the backoffice says so.
 */
@Entity
@Table(name = "photo_import_settings")
class PhotoImportSettings(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /** Imports one account may run per UTC day. Zero closes the feature to everybody. */
    @Column(nullable = false)
    var dailyLimitPerUser: Int = 0,

    /** The most all accounts together may spend in a UTC month, or null for no ceiling. */
    @Column(precision = 12, scale = 2)
    var monthlyBudget: BigDecimal? = null,

    @Column(nullable = false, precision = 12, scale = 6)
    var inputPricePerMillion: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 6)
    var outputPricePerMillion: BigDecimal = BigDecimal.ZERO,

    /** What the prices and the budget are in. A label: nothing converts between currencies. */
    @Column(nullable = false, length = 3)
    var currency: String = "EUR",

    @WhenModified
    var updatedAt: LocalDateTime = LocalDateTime.now(),

) : Model()
