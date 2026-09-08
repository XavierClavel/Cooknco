package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import shared.utils.URL.IMAGE_URL
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** A real jpeg, as the browser would send it. */
fun testImageBytes(width: Int = 40, height: Int = 30): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, (x * 255 / width) shl 8)
    return ByteArrayOutputStream().use {
        ImageIO.write(image, "jpg", it)
        it.toByteArray()
    }
}

suspend fun HttpClient.uploadImage(path: String, id: Long, bytes: ByteArray = testImageBytes()): HttpResponse =
    this.post("$IMAGE_URL/$path/$id") {
        setBody(MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=pic.jpg")
            })
        }))
    }

suspend fun HttpClient.uploadRecipeImage(recipeId: Long, bytes: ByteArray = testImageBytes()): HttpResponse =
    uploadImage("recipes", recipeId, bytes)

suspend fun HttpClient.deleteRecipeImage(recipeId: Long): HttpResponse =
    this.delete("$IMAGE_URL/recipes/$recipeId")
