package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.UserApi
import com.xavierclavel.cooknco.network.dto.FollowInfoDto
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.network.dto.McpClientInfo
import com.xavierclavel.cooknco.network.dto.UserSettingsDTO
import kotlinx.coroutines.flow.first

class UserRepository(
    private val userApi: UserApi,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun token(): String? = tokenDataStore.tokenFlow.first()
    private suspend fun requireToken(): String = token() ?: throw IllegalStateException("Not authenticated")

    suspend fun getUser(userId: Long): Result<UserInfo> = runCatching {
        userApi.getUser(userId)
    }

    suspend fun updateUser(username: String, bio: String): Result<UserInfo> = runCatching {
        userApi.updateUser(requireToken(), username, bio)
    }

    suspend fun uploadProfileImage(userId: Long, imageBytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        userApi.uploadProfileImage(requireToken(), userId, imageBytes, mimeType)
    }

    suspend fun isFollowing(userId: Long): Result<Boolean> = runCatching {
        userApi.isFollowing(requireToken(), userId)
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

    suspend fun getUserRecipes(profileUserId: Long, page: Int): Result<List<RecipeOverview>> = runCatching {
        userApi.getUserRecipes(token(), profileUserId, page)
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

    suspend fun getSettings(): Result<UserSettingsDTO> = runCatching {
        userApi.getSettings(requireToken())
    }

    suspend fun updateSettings(settings: UserSettingsDTO): Result<UserSettingsDTO> = runCatching {
        userApi.updateSettings(requireToken(), settings)
    }
}
