package main.com.xavierclavel.utils

import com.xavierclavel.services.RecipePhoto
import com.xavierclavel.services.RecipePhotoReader
import com.xavierclavel.services.RecipePhotoReading

/**
 * Stands in for the vision model, which no test should pay for or wait on.
 *
 * What it answers is set per test ([answer], or [failure] to throw instead), and what it was
 * sent is recorded, so a test can assert both that a refused request never reached the model
 * — the point of checking premium before the body is read — and which pages did.
 */
class FakeRecipePhotoReader : RecipePhotoReader {
    private val lock = Any()
    private val received = mutableListOf<List<RecipePhoto>>()

    /** The model's text, verbatim: a test can hand back a fenced or malformed answer too. */
    @Volatile var answer: String = """{"ingredients": [], "steps": []}"""
    @Volatile var failure: Exception? = null

    /** Every request's pages, in order. */
    val calls: List<List<RecipePhoto>> get() = synchronized(lock) { received.toList() }

    override suspend fun read(photos: List<RecipePhoto>): RecipePhotoReading {
        synchronized(lock) { received += photos }
        failure?.let { throw it }
        return RecipePhotoReading(answer, inputTokens = 1000, outputTokens = 200)
    }

    fun reset() {
        synchronized(lock) { received.clear() }
        answer = """{"ingredients": [], "steps": []}"""
        failure = null
    }
}
