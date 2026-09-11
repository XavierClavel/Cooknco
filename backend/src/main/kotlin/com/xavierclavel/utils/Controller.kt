package com.xavierclavel.utils

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import shared.enums.Locale
import shared.enums.Sort
import io.ebean.Paging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

abstract class Controller(val base: String = "") {
    fun serve(route: Route) = route.run {
        route(base) {
            routes()
        }
    }

    abstract fun Route.routes()

    infix fun Route.serve(controller: Controller) = controller.serve(this)
}

fun Route.serve(vararg controller: Controller) {
    controller.forEach {it.serve(this) }
}

fun RoutingContext.getPaging():Paging =
    Paging.of(
        call.request.queryParameters["page"]?.toIntOrNull() ?: 0,
        call.request.queryParameters["size"]?.toIntOrNull() ?: 20
    )

fun RoutingContext.getSort(): Sort =
    Sort.valueOf(call.request.queryParameters["sort"] ?: "NONE")

fun RoutingContext.getPathId(): Long = getIdPathVariable("id") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
fun RoutingContext.getQuery(): String = call.request.queryParameters["query"] ?: ""
fun RoutingContext.getLocale(): Locale = call.request.queryParameters["locale"]?.let { enumValueOfIgnoreCase<Locale>(it) } ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)

inline fun <reified T : Enum<T>> enumValueOfIgnoreCase(key: String): T =
    enumValues<T>().find { it.name.equals(key, ignoreCase = true) }
        ?: throw IllegalArgumentException("no value for key $key")


/**
 * Reads an optional enum-valued query parameter, case-insensitively.
 *
 * @return null when the parameter is absent or blank
 * @throws BadRequestException when present but not a value of [T]
 */
inline fun <reified T : Enum<T>> RoutingContext.getEnumQueryParam(name: String): T? =
    call.request.queryParameters[name]
        ?.takeIf { it.isNotBlank() }
        ?.let {
            enumValues<T>().find { value -> value.name.equals(it, ignoreCase = true) }
                ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        }

/**
 * Reads an enum-valued path segment, case-insensitively.
 *
 * @throws BadRequestException when the segment is absent or not a value of [T]
 */
inline fun <reified T : Enum<T>> RoutingContext.getEnumPathParam(name: String): T =
    call.parameters[name]
        ?.let { enumValues<T>().find { value -> value.name.equals(it, ignoreCase = true) } }
        ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)

/** Reads an optional free-text query parameter, treating blank as absent. */
fun RoutingContext.getStringQueryParam(name: String): String? =
    call.request.queryParameters[name]?.takeIf { it.isNotBlank() }

fun RoutingContext.getIdPathVariable(value: String): Long? = call.parameters[value]?.toLongOrNull()
fun RoutingContext.getPathVariable(value: String): String? = call.parameters[value]

fun RoutingContext.getIdPathVariableSet(value: String): Set<Long> = call.parameters[value]?.split(",")?.map { it.toLong() }?.toSet() ?: setOf()

fun RoutingContext.getIdQueryParam(value: String): Long? = call.queryParameters[value]?.toLongOrNull()

fun RoutingContext.getMandatoryIdQueryParam(value: String): Long = getIdQueryParam(value)
    ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)

fun RoutingContext.getBooleanQueryParam(value: String): Boolean? = call.parameters[value]?.toBooleanStrictOrNull()

suspend fun RoutingContext.handleDeletion(deleted: Boolean?) {
    if (deleted == null) call.respond(HttpStatusCode.NotFound)
    else if (!deleted) call.respond(HttpStatusCode.InternalServerError)
    else call.respond(HttpStatusCode.OK)
}

/**
 * The picture out of a multipart body.
 *
 * [maxBytes] bounds what is read into memory. It is left off where a session is what got the
 * caller here — nginx's `client_max_body_size` is the bound there, as it always was — and set
 * on the ticket endpoint, which anyone holding a URL can reach.
 */
suspend fun RoutingContext.receiveImage(maxBytes: Long? = null): Pair<BufferedImage, Metadata> {
    val multipart = call.receiveMultipart()
    var image: BufferedImage? = null
    var metadata: Metadata? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                val bytes = part.provider().toInputStream().readBounded(maxBytes)
                metadata = ImageMetadataReader.readMetadata(bytes.inputStream())
                image = ImageIO.read(bytes.inputStream())
            }
            else -> {
                logger.error { "Unexpected form data for image" }
                throw BadRequestException(BadRequestCause.INVALID_IMAGE)
            }
        }
        part.dispose()
    }
    if (image == null) {
        logger.error {"Unable to read image"}
        throw BadRequestException(BadRequestCause.INVALID_IMAGE)
    }
    if (metadata == null) {
        logger.error {"No metadata found"}
        throw BadRequestException(BadRequestCause.INVALID_IMAGE)
    }
    return Pair(image, metadata)
}

/**
 * Everything on the stream, or a refusal if that is more than [limit].
 *
 * Stops one byte past the limit rather than trusting the declared `Content-Length`, so what is
 * held in memory is bounded whatever the caller said it was sending — or said nothing at all,
 * as a chunked body does.
 */
private fun InputStream.readBounded(limit: Long?): ByteArray {
    if (limit == null) return readBytes()
    val bytes = readNBytes((limit + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    if (bytes.size > limit) throw BadRequestException(BadRequestCause.IMAGE_TOO_LARGE)
    return bytes
}

suspend fun RoutingCall.respondPDF(filename: String, content: ByteArray) {
    response.header("Content-Disposition", "attachment; filename=\"$filename\"")
    respondBytes(content, contentType = ContentType.Application.Pdf)
}

suspend fun RoutingContext.checkUserEditionRights(userId: Long) {
    val currentUser = getSessionUserId()
    if(currentUser == userId) return
    throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_EDIT_USER)
}

suspend fun RoutingContext.checkRecipeEditionRights(recipeOwner: Long) {
    val currentUser = getSessionUserId()
    if (recipeOwner == currentUser) return
    throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_EDIT_RECIPE)
}
