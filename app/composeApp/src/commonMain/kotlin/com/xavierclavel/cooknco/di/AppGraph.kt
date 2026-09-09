package com.xavierclavel.cooknco.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.xavierclavel.cooknco.data.AuthRepository
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.PushRepository
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.NotificationApi
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.UserApi

/**
 * Single object graph for the app, replacing the per-ViewModel `Context` plumbing
 * that the Android-only version used.
 *
 * Each platform entry point calls [init] with a factory for its preferences store;
 * everything below is created lazily so the store is instantiated exactly once
 * (DataStore rejects two instances over the same file).
 */
object AppGraph {

    private var dataStoreFactory: (() -> DataStore<Preferences>)? = null

    fun init(dataStoreFactory: () -> DataStore<Preferences>) {
        if (this.dataStoreFactory == null) {
            this.dataStoreFactory = dataStoreFactory
        }
    }

    val tokenDataStore: TokenDataStore by lazy {
        val factory = checkNotNull(dataStoreFactory) {
            "AppGraph.init() must be called before the object graph is used"
        }
        TokenDataStore(factory())
    }

    private val authApi by lazy { AuthApi(ApiClient.httpClient) }
    private val userApi by lazy { UserApi(ApiClient.httpClient) }
    private val cookbookApi by lazy { CookbookApi(ApiClient.httpClient) }
    private val notificationApi by lazy { NotificationApi(ApiClient.httpClient) }

    val recipeApi by lazy { RecipeApi(ApiClient.httpClient) }

    /**
     * Registered before [authRepository] uses it: signing out has to detach this device
     * while the call is still authenticated. See [PushRepository.unregisterCurrentDevice].
     */
    val pushRepository by lazy { PushRepository(notificationApi, tokenDataStore) }

    val authRepository by lazy { AuthRepository(authApi, tokenDataStore, pushRepository) }
    val userRepository by lazy { UserRepository(userApi, tokenDataStore) }
    val recipeRepository by lazy { RecipeRepository(recipeApi, tokenDataStore) }
    val cookbookRepository by lazy { CookbookRepository(cookbookApi, tokenDataStore) }
    val unitRepository by lazy { UnitRepository(recipeApi) }
}
