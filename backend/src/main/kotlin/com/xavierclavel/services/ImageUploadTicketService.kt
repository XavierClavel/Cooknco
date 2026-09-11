package com.xavierclavel.services

import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.plugins.ImageUploadTicketData
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.plugins.RedisService.Companion.IMAGE_UPLOAD_TICKET_TTL
import com.xavierclavel.utils.Configuration
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.utils.URL.IMAGE_URL
import java.security.SecureRandom
import java.util.Base64

/**
 * How a caller with no session — an MCP client — is let in to write one recipe's picture.
 *
 * **Why a ticket rather than an argument.** A tool's arguments are JSON the *model* writes,
 * token by token; a photograph base64'd into them is an order of magnitude past what any model
 * can emit, and shrinking it first defeats the point, since the volume stores its own resized
 * copies anyway ([ImageBucket][shared.enums.ImageBucket]). So the tool answers with a URL and
 * the client posts the file to it directly, and the bytes never enter the model's context.
 *
 * **Why that is safe.** The ticket is not a credential for the account: it names one recipe,
 * survives one redemption and expires in [IMAGE_UPLOAD_TICKET_TTL] seconds. It is minted only
 * for a recipe the caller owns, and ownership is checked *again* at redemption — the ticket
 * outlives the request that made it, and in between the recipe can be deleted or handed over.
 *
 * The URL carries the ticket in its path, which is what makes it usable from a shell in one
 * line, and is the same trade a pre-signed storage URL makes: the secret is written down in a
 * shell history and an access log, so it is kept worth as little as possible instead of being
 * kept out of them.
 */
class ImageUploadTicketService: KoinComponent {
    private val redisService: RedisService by inject()
    private val recipeService: RecipeService by inject()
    private val configuration: Configuration by inject()

    companion object {
        private const val TICKET_BYTES = 32
    }

    private val random = SecureRandom()

    /**
     * `frontend.url` rather than `backend.url`, for the reason `OAuthService` gives: nginx
     * serves the app and the API on one origin, and `backend.url` carries the `/api/v1` suffix
     * that `/image/` does not sit under.
     */
    private val origin: String get() = configuration.frontend.url.trimEnd('/')

    /** See [Configuration.Images.maxUploadBytes]. */
    val maxUploadBytes: Long get() = configuration.images.maxUploadBytes

    val expiresInSeconds: Long get() = IMAGE_UPLOAD_TICKET_TTL

    /** A URL that accepts one picture for [recipeId], for a caller who owns it. */
    suspend fun mintRecipeTicket(userId: Long, recipeId: Long): String {
        if (recipeService.getRecipeOwner(recipeId).id != userId) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_EDIT_RECIPE)
        }
        val ticket = newTicket()
        redisService.createImageUploadTicket(
            ticket,
            ImageUploadTicketData(userId = userId, recipeId = recipeId),
        )
        return "$origin/$IMAGE_URL/upload/$ticket"
    }

    /**
     * Spends a ticket and says what it was for, or refuses.
     *
     * A ticket that is unknown, already spent or expired is one answer — `invalid_upload_ticket`
     * — because telling them apart would only help someone guessing at URLs.
     */
    suspend fun redeemRecipeTicket(ticket: String): ImageUploadTicketData {
        val data = redisService.takeImageUploadTicket(ticket)
            ?: throw UnauthorizedException(UnauthorizedCause.INVALID_UPLOAD_TICKET)
        // Re-checked rather than trusted: the recipe may have gone or changed hands since the
        // ticket was minted. A deleted recipe raises recipe_not_found from the lookup itself.
        if (recipeService.getRecipeOwner(data.recipeId).id != data.userId) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_EDIT_RECIPE)
        }
        return data
    }

    private fun newTicket(): String {
        val bytes = ByteArray(TICKET_BYTES).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
