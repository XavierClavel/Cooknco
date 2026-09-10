package shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire shapes of the OAuth 2.1 flow that lets an MCP client sign in.
 *
 * Field names are the specs' own, in snake_case, because they are read by clients that know
 * the RFCs and nothing about this codebase: RFC 9728 for [ProtectedResourceMetadata], RFC 8414
 * for [AuthorizationServerMetadata], RFC 7591 for the registration pair, and OAuth 2.1 for
 * [TokenResponse] and [OAuthError].
 *
 * Every response here is serialized with the API's own Json, whose `explicitNulls` is on, so
 * optional fields are nullable *and* omitted only when the serializer is told to — see
 * `OAuthController`, which encodes them with a configuration that drops nulls. A metadata
 * document carrying `"scopes_supported": null` is worse than one carrying nothing.
 */
@Serializable
data class ProtectedResourceMetadata(
    /** Canonical URI of the MCP endpoint, which is what a token's audience must match. */
    val resource: String,
    @SerialName("authorization_servers")
    val authorizationServers: List<String>,
    @SerialName("scopes_supported")
    val scopesSupported: List<String>,
    @SerialName("bearer_methods_supported")
    val bearerMethodsSupported: List<String> = listOf("header"),
    @SerialName("resource_documentation")
    val resourceDocumentation: String? = null,
)

@Serializable
data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("registration_endpoint")
    val registrationEndpoint: String,
    @SerialName("scopes_supported")
    val scopesSupported: List<String>,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String> = listOf("code"),
    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String> = listOf("authorization_code", "refresh_token"),
    /**
     * `none` only: every client here is a public one that registered itself, so there is no
     * secret to authenticate it with, and PKCE is what protects the code instead.
     */
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = listOf("none"),
    /** S256 only. OAuth 2.1 removes `plain`, and accepting it would defeat the point. */
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String> = listOf("S256"),
    /** Says the server understands RFC 8707, so a client can trust the audience binding. */
    @SerialName("resource_indicators_supported")
    val resourceIndicatorsSupported: Boolean = true,
)

/**
 * A client registering itself (RFC 7591).
 *
 * Only [redirectUris] is required, and it is the field that matters: it is checked exactly on
 * every authorization request, so it is what keeps an authorization code from being sent
 * anywhere but back to the client that asked for it.
 */
@Serializable
data class ClientRegistrationRequest(
    @SerialName("redirect_uris")
    val redirectUris: List<String> = listOf(),
    @SerialName("client_name")
    val clientName: String? = null,
    @SerialName("client_uri")
    val clientUri: String? = null,
    @SerialName("grant_types")
    val grantTypes: List<String>? = null,
    @SerialName("response_types")
    val responseTypes: List<String>? = null,
    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String? = null,
    val scope: String? = null,
)

@Serializable
data class ClientRegistrationResponse(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("client_id_issued_at")
    val clientIdIssuedAt: Long,
    @SerialName("redirect_uris")
    val redirectUris: List<String>,
    @SerialName("client_name")
    val clientName: String? = null,
    @SerialName("grant_types")
    val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    @SerialName("response_types")
    val responseTypes: List<String> = listOf("code"),
    /** No secret is issued: see [AuthorizationServerMetadata.tokenEndpointAuthMethodsSupported]. */
    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String = "none",
    val scope: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("expires_in")
    val expiresIn: Long,
    /** Rotated on every use, as OAuth 2.1 requires for a public client. */
    @SerialName("refresh_token")
    val refreshToken: String,
    val scope: String,
)

/** The error shape both the token and registration endpoints answer with. */
@Serializable
data class OAuthError(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String? = null,
)
