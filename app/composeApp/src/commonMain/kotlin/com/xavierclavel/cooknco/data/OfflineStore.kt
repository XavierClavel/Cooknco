package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.UserInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * The recipes, cookbooks and session this phone is holding, as files.
 *
 * **One file per thing, rather than one blob.** DataStore — where the token, the cook timer
 * and the cook session live — reads and rewrites its whole Preferences file on every touch,
 * so a few hundred recipes in it would be paid for on every read of the token. And a
 * database is a schema, a migration story and an iOS build dependency for a lookup that a
 * filename already performs: nothing here is ever queried, only fetched by id.
 *
 * Two rules it keeps, both inherited from [CookTimerStore]:
 *
 * - **Every write is atomic.** Into `<name>.tmp`, then `atomicMove`. Without it a process
 *   killed mid-write leaves a file that parses *sometimes* — the worst of the three states.
 * - **Anything that will not parse reads as absent.** A build that changed a DTO's shape, a
 *   truncated file, a half-copied restore: all of them are "we do not have this recipe", which
 *   is a thing the app already knows how to say. Throwing instead would crash the screen that
 *   was meant to be the offline one.
 */
class OfflineStore(
    private val root: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // A field added to a DTO since this file was written must read as its default rather
        // than as a parse failure that throws the whole recipe away.
        encodeDefaults = true
    }

    private val recipesDir get() = root / "recipes"
    private val cookbooksDir get() = root / "cookbooks"
    /** Public: [OfflineImages] resolves picture paths against it, and Coil reads them. */
    val imagesDir get() = root / "images"

    // ── recipes ──────────────────────────────────────────────────────────────

    suspend fun readRecipe(id: Long): OfflineRecipe? = read(recipesDir / "$id.json")

    suspend fun writeRecipe(recipe: OfflineRecipe) =
        write(recipesDir / "${recipe.recipe.id}.json", recipe)

    suspend fun deleteRecipe(id: Long) = delete(recipesDir / "$id.json")

    // ── cookbooks ────────────────────────────────────────────────────────────

    suspend fun readCookbook(id: Long): OfflineCookbook? = read(cookbooksDir / "$id.json")

    suspend fun writeCookbook(cookbook: OfflineCookbook) =
        write(cookbooksDir / "${cookbook.cookbook.id}.json", cookbook)

    suspend fun deleteCookbook(id: Long) = delete(cookbooksDir / "$id.json")

    // ── the index, the lists and the session ─────────────────────────────────

    suspend fun readIndex(): OfflineIndex? = read(root / "index.json")

    suspend fun writeIndex(index: OfflineIndex) = write(root / "index.json", index)

    suspend fun readLists(): OfflineLists? = read(root / "lists.json")

    suspend fun writeLists(lists: OfflineLists) = write(root / "lists.json", lists)

    /**
     * The last account this device saw, so a launch with no network can still be signed in.
     *
     * Kept apart from the index because it is read on a different schedule: at the very first
     * frame, by `AuthRepository`, before anything knows whether there is a store worth reading.
     */
    suspend fun readSession(): UserInfo? = read(root / "session.json")

    suspend fun writeSession(user: UserInfo) = write(root / "session.json", user)

    // ── pictures ─────────────────────────────────────────────────────────────

    fun imagePath(name: String): Path = imagesDir / name

    suspend fun hasImage(name: String): Boolean = withContext(dispatcher) {
        fileSystem.exists(imagesDir / name)
    }

    suspend fun writeImage(name: String, bytes: ByteArray) = withContext(dispatcher) {
        runCatching {
            fileSystem.createDirectories(imagesDir)
            val target = imagesDir / name
            val temporary = imagesDir / "$name.tmp"
            fileSystem.write(temporary) { write(bytes) }
            fileSystem.atomicMove(temporary, target)
        }
        Unit
    }

    /** What is on disk, so a sync can delete the pictures nothing points at any more. */
    suspend fun imageNames(): Set<String> = withContext(dispatcher) {
        runCatching { fileSystem.list(imagesDir).map { it.name }.toSet() }.getOrDefault(emptySet())
    }

    suspend fun deleteImage(name: String) = delete(imagesDir / name)

    // ── all of it ────────────────────────────────────────────────────────────

    /**
     * Forgets everything, which is what signing out has to do.
     *
     * A handset is passed around, and this is somebody's whole recipe collection sitting in a
     * directory — the same reasoning that makes [AccountSettings.forget] clear the cached
     * language and ladder, applied to very much more data.
     */
    suspend fun clear() = withContext(dispatcher) {
        runCatching { fileSystem.deleteRecursively(root, mustExist = false) }
        Unit
    }

    /** What the store takes up, for the settings screen to print. Zero if it cannot be read. */
    suspend fun sizeBytes(): Long = withContext(dispatcher) {
        runCatching {
            fun size(directory: Path): Long =
                fileSystem.listOrNull(directory).orEmpty().sumOf { path ->
                    val metadata = fileSystem.metadataOrNull(path)
                    if (metadata?.isDirectory == true) size(path) else metadata?.size ?: 0L
                }
            size(root)
        }.getOrDefault(0L)
    }

    // ── the two operations everything above is made of ───────────────────────

    private suspend inline fun <reified T> read(path: Path): T? = withContext(dispatcher) {
        runCatching {
            json.decodeFromString<T>(fileSystem.read(path) { readUtf8() })
        }.getOrNull()
    }

    private suspend inline fun <reified T> write(path: Path, value: T) = withContext(dispatcher) {
        runCatching {
            fileSystem.createDirectories(path.parent!!)
            val temporary = path.parent!! / "${path.name}.tmp"
            fileSystem.write(temporary) { writeUtf8(json.encodeToString(value)) }
            fileSystem.atomicMove(temporary, path)
        }
        Unit
    }

    private suspend fun delete(path: Path) = withContext(dispatcher) {
        runCatching { fileSystem.delete(path, mustExist = false) }
        Unit
    }
}
