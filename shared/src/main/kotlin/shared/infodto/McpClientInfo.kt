package shared.infodto

import kotlinx.serialization.Serializable

/**
 * An MCP client this account has approved, as the settings screen lists it.
 *
 * [clientName] is what the client called itself when it registered (RFC 7591), so it is
 * attacker-controlled text exactly like the consent page's — anything rendering it escapes
 * it, and it is shown next to [redirectUris], which is the part a client cannot lie about
 * because that is where its codes actually go.
 */
@Serializable
data class McpClientInfo(
    val clientId: String,
    val clientName: String,
    val redirectUris: List<String>,
    /** Epoch seconds, like every other timestamp the clients read. */
    val grantedAt: Long,
    val lastUsedAt: Long,
)
