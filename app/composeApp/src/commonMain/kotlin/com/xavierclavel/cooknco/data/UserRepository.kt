package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.UserApi
import com.xavierclavel.cooknco.network.dto.FollowInfoDto
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.network.dto.McpClientInfo
import com.xavierclavel.cooknco.network.dto.UserSettingsDTO
import com.xavierclavel.cooknco.network.isOffline
import kotlinx.coroutines.flow.first

class UserRepository(
    private val userApi: UserApi,
    private val tokenDataStore: TokenDataStore,
    private val offlineStore: OfflineStore,
) {
    private suspend fun token(): String? = tokenDataStore.tokenFlow.first()
    private suspend fun requireToken(): String = token() ?: throw IllegalStateException("Not authenticated")

    /**
     * A profile. Offline, only the signed-in cook's own — it is the only one stored, and it is
     * the only profile that has anything behind it to show.
     */
    suspend fun getUser(userId: Long): Result<UserInfo> {
        val result = OfflineState.observe(runCatching { userApi.getUser(userId) })
        val error = result.exceptionOrNull() ?: return result
        if (!error.isOffline) return result
        val session = offlineStore.readSession()?.takeIf { it.id == userId } ?: return result
        return Result.success(session)
    }

    suspend fun updateUser(username: String, bio: String): Result<UserInfo> = runCatching {
        userApi.updateUser(requireToken(), username, bio)
    }

    suspend fun uploadProfileImage(userId: Long, imageBytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        userApi.uploadProfileImage(requireToken(), userId, imageBytes, mimeType)
    }

    suspend fun isFollowing(userId: Long, currentUserId: Long): Result<Boolean> = runCatching {
        userApi.isFollowing(requireToken(), userId, currentUserId)
    }

    suspend fun follow(userId: Long): Result<Unit> = runCatching {
        userApi.follow(requireToken(), userId)
    }

    suspend fun unfollow(userId: Long): Result<Unit> = runCatching {
        userApi.unfollow(requireToken(), userId)
    }

    suspend fun getFollowers(userId: Long, page: Int): Result<List<FollowInfoDto>> = runCatching {
        userApi.getFollowers(token(), userId, page)
    }

    suspend fun getFollows(userId: Long, page: Int): Result<List<FollowInfoDto>> = runCatching {
        userApi.getFollows(token(), userId, page)
    }

    suspend fun acceptFollowRequest(followerId: Long): Result<Unit> = runCatching {
        userApi.acceptFollowRequest(requireToken(), followerId)
    }

    suspend fun declineFollowRequest(followerId: Long): Result<Unit> = runCatching {
        userApi.declineFollowRequest(requireToken(), followerId)
    }

    /**
     * A profile grid, page by page — and offline, the whole of whichever list was stored.
     *
     * The store keeps each list in the order the server sent it, so page zero can be answered
     * with all of it and every later page with nothing. That is what the paging above this
     * reads as "there is no more", which is true: there is no more on this phone.
     *
     * Only for the cook's own profile. Somebody else's recipes were never pinned, and
     * answering their grid with this one's would be showing the wrong person's cooking.
     */
    suspend fun getUserRecipes(
        profileUserId: Long,
        page: Int,
        liked: Boolean = false,
    ): Result<List<RecipeOverview>> {
        val result = OfflineState.observe(runCatching {
            userApi.getUserRecipes(token(), profileUserId, page, liked = liked)
        })
        val error = result.exceptionOrNull() ?: return result
        if (!error.isOffline) return result
        if (offlineStore.readIndex()?.userId != profileUserId) return result
        val lists = offlineStore.readLists() ?: return result
        return Result.success(if (page > 0) emptyList() else if (liked) lists.liked else lists.own)
    }

    suspend fun getMcpClients(): Result<List<McpClientInfo>> = runCatching {
        userApi.getMcpClients(requireToken())
    }

    suspend fun revokeMcpClient(clientId: String): Result<Unit> = runCatching {
        userApi.revokeMcpClient(requireToken(), clientId)
    }

    suspend fun updatePassword(old: String, new: String): Result<Unit> = runCatching {
        userApi.updatePassword(requireToken(), old, new)
    }

    /**
     * Deletes the account. The token is dead the moment this returns, so the caller signs
     * out straight after — see [com.xavierclavel.cooknco.ui.user.UserSettingsViewModel.deleteAccount].
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        userApi.deleteAccount(requireToken())
    }

    suspend fun getSettings(): Result<UserSettingsDTO> = runCatching {
        userApi.getSettings(requireToken())
    }

    /** The server answers with nothing, so neither does this. See [UserApi.updateSettings]. */
    suspend fun updateSettings(settings: UserSettingsDTO): Result<Unit> = runCatching {
        userApi.updateSettings(requireToken(), settings)
    }
}
