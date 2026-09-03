package com.xavierclavel.controllers

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.services.StorageService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.json
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.inject
import shared.dto.SearchResult
import shared.dto.StorageCleanupDTO
import shared.enums.ImageBucket
import shared.enums.ImageSort
import shared.enums.ImageStatus
import shared.infodto.AdminImageInfo

/**
 * The image volume, for the backoffice storage tab.
 *
 * Read endpoints report what is on disk against what the database still points at; the two
 * write endpoints remove a single file, or sweep every file the app has finished with.
 */
object AdminStorageController: Controller("storage") {
    val storageService: StorageService by inject(StorageService::class.java)

    override fun Route.routes() {
        getOverview()
        route("/images") {
            searchImages()
            deleteImage()
        }
        cleanup()
    }

    private fun Route.getOverview() = get {
        call.respond(storageService.buildOverview())
    }

    /**
     * @param bucket restricts to one directory of the volume; absent scans them all
     * @param status restricts to one relation between file and owner
     * @param query matched against the filename and the owner's name
     * @param sort defaults to largest first — the table exists to find what to reclaim
     */
    private fun Route.searchImages() = get {
        val paging = getPaging()
        val (count, images) = storageService.searchImages(
            bucket = getEnumQueryParam<ImageBucket>("bucket"),
            status = getEnumQueryParam<ImageStatus>("status"),
            query = getStringQueryParam("query"),
            sort = getEnumQueryParam<ImageSort>("sort") ?: ImageSort.SIZE_DESCENDING,
            paging = paging,
        )
        val result = SearchResult(count, paging.pageIndex(), paging.pageSize(), images)
        call.respond(json.encodeToString(SearchResult.serializer(AdminImageInfo.serializer()), result))
    }

    /**
     * Removes one file. Both parameters are required: a bucket-wide delete has to go
     * through [cleanup], where it is scoped by status rather than by directory.
     */
    private fun Route.deleteImage() = delete {
        val bucket = getEnumQueryParam<ImageBucket>("bucket") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val filename = getStringQueryParam("file") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        if (!storageService.deleteFile(bucket, filename)) call.respond(HttpStatusCode.NotFound)
        else call.respond(HttpStatusCode.OK)
    }

    /** Sweeps superseded, orphaned or unrecognised files. Accepts `dryRun` to count first. */
    private fun Route.cleanup() = post("/cleanup") {
        call.respond(storageService.cleanup(call.receive<StorageCleanupDTO>()))
    }
}
