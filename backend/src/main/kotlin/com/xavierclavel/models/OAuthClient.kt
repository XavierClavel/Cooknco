package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.WhenModified
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * An MCP client that registered itself to ask for access (RFC 7591).
 *
 * A client is not a user and holds no rights: it is a name and a set of redirect URIs, created
 * without anyone's permission — dynamic registration is open, which is what lets a client
 * connect knowing only the MCP URL. Everything that matters happens later, when a *user* is
 * asked to authorize one of these and the code comes back to a URI checked against this row.
 *
 * These live in Postgres rather than in Redis, where the codes and tokens live, because a
 * client keeps its [clientId] on disk indefinitely: losing the row would leave working
 * installations holding an id the server no longer knows, and `invalid_client` is not an error
 * a client can recover from without the user re-adding the server by hand.
 *
 * No secret is stored because none is issued. Public clients — a CLI, a desktop app — cannot
 * keep one, so PKCE binds the code to the caller instead (`OAuthService.redeemCode`).
 */
@Entity
@Table(name = "oauth_clients")
class OAuthClient(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /** The public identifier the client sends on every authorize and token request. */
    @Column(unique = true, nullable = false, length = 64)
    var clientId: String = "",

    /**
     * What the consent page calls this client. Client-supplied and therefore untrusted: it is
     * escaped on the way into the page like any other user text, and it is deliberately shown
     * next to the redirect URI it registered, so a client naming itself after something it is
     * not cannot hide where the code would actually go.
     */
    @Column(nullable = false, length = 255)
    var clientName: String = "",

    /**
     * Where an authorization code may be sent, matched exactly and in full — no prefix, no
     * wildcard. This is the whole defence against a code being handed to somebody else.
     */
    @ElementCollection
    var redirectUris: List<String> = listOf(),

    var registeredAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Last time this client redeemed a code or refreshed a token, which on a registration
     * nothing has used yet is when it was registered. Nothing prunes on it: it is what a later
     * cleanup of clients nobody uses would read, and what tells an operator whether a
     * registration is live.
     */
    @WhenModified
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),

    ) : Model()
