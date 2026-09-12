package com.xavierclavel.models

import io.ebean.Model
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * One account having approved one MCP client — what the settings screen lists and what
 * "Revoke" deletes.
 *
 * The tokens themselves stay in Redis, where their expiry and rotation are the store's
 * problem. They are not what is listed here, and deliberately so: a client that has been
 * connected for a month holds a different access token every hour, so a list of tokens is
 * not a list of connections, and an account's own record of what it has allowed must not
 * disappear because a token aged out overnight.
 *
 * That split is also what makes revocation work without hunting opaque keys: a row here is
 * checked on every MCP request and on every refresh ([com.xavierclavel.services.OAuthService.tokenFor]),
 * so deleting it stops tokens that have already been issued, which is the only kind that
 * matters at the moment somebody revokes.
 *
 * One row per (user, client) pair: approving the same client twice is the same grant, and
 * the second approval refreshes [grantedAt] rather than adding a second entry the user
 * would have to revoke twice.
 */
@Entity
@Table(
    name = "oauth_grants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "client_id"])],
)
class OAuthGrant(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(optional = false)
    var user: User? = null,

    /**
     * The client's public id rather than a link to [OAuthClient]: the grant is against what
     * the client presents on every request, which is what has to be compared with no join.
     */
    @Column(name = "client_id", nullable = false, length = 64)
    var clientId: String = "",

    /** When the user last pressed Allow for this client. */
    var grantedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Last time a token was issued or refreshed under this grant — not written by
     * `@WhenModified`, because the row is only otherwise touched when the user re-approves,
     * and "last used" has to mean the client rather than the person.
     */
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),

) : Model()
