package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.AppVersionApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What this build is being asked to do about its version. */
enum class UpdateRequirement {
    /** Nothing. Up to date, ahead of the store, ungated, or we could not find out. */
    NONE,

    /** A newer build exists. Said once per launch and dismissible. */
    SUGGESTED,

    /** Too old to run. The app stops here. */
    REQUIRED,
}

/**
 * The verdict, plus what the screen needs to act on it.
 *
 * [storeUrl] can be blank even when an update is required — a gate is saved with one, but
 * a response we could not fully read should not cost the user the message — so the button
 * that opens it is hidden rather than dead.
 */
data class AppUpdate(
    val requirement: UpdateRequirement = UpdateRequirement.NONE,
    val latestVersion: String = "",
    val storeUrl: String = "",
)

/**
 * Whether this build is still allowed to run, as the backend sees it.
 *
 * Asked once per launch, and every failure answers [UpdateRequirement.NONE]: offline, a
 * 500, a verdict this build predates, a platform the backend does not gate. Blocking the
 * app is the one outcome here that a user cannot work around — there is no other screen to
 * reach and no setting to change — so it happens only when the server has actually said so.
 *
 * The state outlives no more than the process, which is what makes "dismissed" mean
 * "dismissed for this launch": a suggestion is worth repeating next time, and a block is
 * re-derived from the server rather than remembered.
 */
class AppVersionRepository(
    private val appVersionApi: AppVersionApi,
) {

    private val _update = MutableStateFlow(AppUpdate())
    val update: StateFlow<AppUpdate> = _update.asStateFlow()

    private var dismissed = false

    suspend fun refresh() {
        val info = runCatching { appVersionApi.check() }
            .onFailure { log("could not check the app version: ${it.message}") }
            .getOrNull() ?: return

        val requirement = when (info.status) {
            "UPDATE_REQUIRED" -> UpdateRequirement.REQUIRED
            "UPDATE_AVAILABLE" -> if (dismissed) UpdateRequirement.NONE else UpdateRequirement.SUGGESTED
            // "OK", and anything a later backend adds that this build has never heard of
            else -> UpdateRequirement.NONE
        }

        _update.value = AppUpdate(
            requirement = requirement,
            latestVersion = info.latestVersion.orEmpty(),
            storeUrl = info.storeUrl.orEmpty(),
        )
    }

    /**
     * Puts a suggestion away for this launch.
     *
     * Only a suggestion: [UpdateRequirement.REQUIRED] has no dismiss on screen, and would
     * come straight back on the next refresh anyway.
     */
    fun dismissSuggestion() {
        dismissed = true
        _update.update {
            if (it.requirement == UpdateRequirement.SUGGESTED) it.copy(requirement = UpdateRequirement.NONE)
            else it
        }
    }

    private fun log(message: String) = println("CookncoVersion: $message")
}
