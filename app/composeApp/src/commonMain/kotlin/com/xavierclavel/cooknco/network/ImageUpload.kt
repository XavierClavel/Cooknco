package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.platform.PickedImage
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

/**
 * What every picture upload sends: the file as it was picked, and the part of it to keep.
 *
 * The crop goes in the query rather than as a second part of the body, because the backend
 * refuses any part that is not the file — so a build that sends one is still understood by a
 * backend that predates it, which saves the picture centred.
 */
internal fun HttpRequestBuilder.imageUpload(image: PickedImage, filename: String) {
    image.crop?.let { parameter("crop", it.toQueryValue()) }
    setBody(MultiPartFormDataContent(formData {
        append("file", image.bytes, Headers.build {
            append(HttpHeaders.ContentType, image.mimeType)
            append(HttpHeaders.ContentDisposition, "filename=$filename")
        })
    }))
}
