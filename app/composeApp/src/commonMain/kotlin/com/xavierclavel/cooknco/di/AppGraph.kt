package com.xavierclavel.cooknco.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.xavierclavel.cooknco.data.AppVersionRepository
import com.xavierclavel.cooknco.data.AuthRepository
import com.xavierclavel.cooknco.data.CookSession
import com.xavierclavel.cooknco.data.CookSessionStore
import com.xavierclavel.cooknco.data.CookTimer
import com.xavierclavel.cooknco.data.CookTimerStore
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.DevicePreferences
import com.xavierclavel.cooknco.data.ExportRepository
import com.xavierclavel.cooknco.data.OfflineImages
import com.xavierclavel.cooknco.data.OfflineStore
import com.xavierclavel.cooknco.data.OfflineSync
import com.xavierclavel.cooknco.data.PushRepository
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.ReportRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.AppVersionApi
import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.ExportApi
import com.xavierclavel.cooknco.network.NotificationApi
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.ReportApi
import com.xavierclavel.cooknco.network.UserApi
import com.xavierclavel.cooknco.platform.offlineRoot

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

    /**
     * The one preferences store, shared by everything that keeps something on the device —
     * DataStore refuses a second instance over the same file, so this is created once here
     * rather than per consumer.
     */
    private val preferences: DataStore<Preferences> by lazy {
        val factory = checkNotNull(dataStoreFactory) {
            "AppGraph.init() must be called before the object graph is used"
        }
        factory()
    }

    val tokenDataStore: TokenDataStore by lazy { TokenDataStore(preferences) }

    val devicePreferences: DevicePreferences by lazy { DevicePreferences(preferences) }

    /**
     * The recipes kept on this device, as files.
     *
     * Deliberately *not* in [preferences]: DataStore rewrites its whole file on every edit, so
     * a few hundred recipes in it would be paid for on every read of the token. See
     * [OfflineStore].
     */
    val offlineStore: OfflineStore by lazy { OfflineStore(offlineRoot()) }

    /** The pinned pictures, and what to hand Coil for one. See [OfflineImages]. */
    val offlineImages: OfflineImages by lazy { OfflineImages(offlineStore, ApiClient.httpClient) }

    /**
     * The cook mode timer. Lives here rather than in a view model because it outlives every
     * screen: it is still counting with cook mode closed, and it is reached from a
     * notification action arriving on a process that has no screens at all.
     */
    val cookTimer: CookTimer by lazy { CookTimer(CookTimerStore(preferences)) }

    /**
     * The recipe being cooked, step by step. Here for the same reason the timer is, only
     * more so: the notification that carries it is also what moves it, so the one thing that
     * is guaranteed to be running when it changes is a broadcast receiver with no screens.
     */
    val cookSession: CookSession by lazy { CookSession(CookSessionStore(preferences)) }

    private val appVersionApi by lazy { AppVersionApi(ApiClient.httpClient) }
    private val authApi by lazy { AuthApi(ApiClient.httpClient) }
    private val userApi by lazy { UserApi(ApiClient.httpClient) }
    private val cookbookApi by lazy { CookbookApi(ApiClient.httpClient) }
    private val notificationApi by lazy { NotificationApi(ApiClient.httpClient) }
    private val reportApi by lazy { ReportApi(ApiClient.httpClient) }
    private val exportApi by lazy { ExportApi(ApiClient.httpClient) }

    val recipeApi by lazy { RecipeApi(ApiClient.httpClient) }

    /**
     * Registered before [authRepository] uses it: signing out has to detach this device
     * while the call is still authenticated. See [PushRepository.unregisterCurrentDevice].
     */
    val pushRepository by lazy { PushRepository(notificationApi, tokenDataStore) }

    val authRepository by lazy {
        AuthRepository(authApi, tokenDataStore, pushRepository, devicePreferences, offlineStore)
    }
    val userRepository by lazy { UserRepository(userApi, tokenDataStore, offlineStore) }
    val recipeRepository by lazy { RecipeRepository(recipeApi, tokenDataStore, offlineStore) }
    val cookbookRepository by lazy { CookbookRepository(cookbookApi, tokenDataStore, offlineStore) }

    /**
     * Keeps the offline copy up to date. Asked at launch, at sign-in and on a pull to refresh;
     * it decides for itself whether there is anything to do. See [OfflineSync].
     */
    val offlineSync by lazy {
        OfflineSync(recipeApi, cookbookApi, tokenDataStore, offlineStore, offlineImages, devicePreferences)
    }
    val unitRepository by lazy { UnitRepository(recipeApi) }
    val reportRepository by lazy { ReportRepository(reportApi, tokenDataStore) }

    /** The PDF exports. Only an admin's session can obtain one — see [ExportRepository]. */
    val exportRepository by lazy { ExportRepository(exportApi, tokenDataStore) }

    /**
     * Whether this build may still run. Needs no session and no data store, so it is
     * reachable from the very first frame — which is when it is asked.
     */
    val appVersionRepository by lazy { AppVersionRepository(appVersionApi) }
}
