package com.xavierclavel.utils

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import javax.crypto.spec.SecretKeySpec


data class Configuration(
    val smtp: Smtp,
    val frontend: Frontend,
    val backend: Backend,
    val encryption: Encryption,
    val oauth: OAuth,

    /**
     * Defaulted whole, like [Frontend.internalUrl] and for the same reason: the production
     * `application.yaml` lives on the cluster rather than in the image, so a section this
     * one required would crash the backend on the first rollout that shipped it.
     */
    val pdf: Pdf = Pdf(),

    /** Defaulted whole, and off by default, for the reason [pdf] is. See [Push]. */
    val push: Push = Push(),
) {
    /**
     * Firebase Cloud Messaging, which delivers the app's push notifications.
     *
     * Off unless an install says otherwise, because it cannot work without a service
     * account key that only the cluster has: a developer running the backend locally, and
     * the test suite, both get [com.xavierclavel.services.NoopPushSender] and a backend
     * that stores notifications without pushing them.
     */
    data class Push(
        val enabled: Boolean = false,

        /**
         * The service account key downloaded from the Firebase console, mounted from the
         * `cooknco-fcm` secret (`k8s/base/backend.yaml`). Never in the image: it is a
         * private key that can send notifications to every install of the app.
         */
        val credentialsPath: String = "/app/config/fcm-service-account.json",

        /**
         * The Firebase project to send under. Blank takes it from the key file, which is
         * the normal case — the two disagreeing is a misconfiguration, not a feature.
         */
        val projectId: String = "",

        /**
         * Pushes in flight at once.
         *
         * FCM's v1 API takes one device per request, so a broadcast is one request per
         * device. This is what keeps that from opening a connection per device at once;
         * the pushes themselves run in the background, so a low number costs latency
         * nobody is waiting on.
         */
        val maxConcurrentSends: Int = 8,
    )

    data class Pdf(
        /**
         * In-cluster address of the Gotenberg that turns a rendered sheet into a PDF
         * (`k8s/base/gotenberg.yaml`, and the `gotenberg` service in `compose.yaml`).
         */
        val gotenbergUrl: String = "http://cooknco-gotenberg:3000",

        /**
         * How many sheets this backend will have printed at once.
         *
         * Matches `--chromium-max-concurrency` on the renderer (`k8s/base/gotenberg.yaml`):
         * every print past that number is a request Gotenberg holds in a queue of its own,
         * where it occupies a connection and can time out. Waiting here instead is cheaper,
         * and it is the only limit that holds — the backoffice paces its live preview, but
         * a second operator, a reloaded tab or anything else calling the endpoint does not.
         */
        val maxConcurrentRenders: Int = 2,

        /** How long a print waits for its turn before the caller is told to come back. */
        val renderQueueSeconds: Long = 20,
    )

    data class OAuth(
        val google: OAuthProvider
    ) {
        data class OAuthProvider(
            val clientId: String,
            val clientSecret: String,
        )
    }

    data class Frontend(
        val url: String,

        /**
         * In-cluster address of the nginx that serves the built SPA, read by
         * [com.xavierclavel.services.AppShellSource] to fetch the `index.html` it injects
         * link-preview tags into.
         *
         * Defaulted rather than required: the production `application.yaml` lives on the
         * cluster and is not deployed with the image (k8s/README.md), so a mandatory field
         * would crash the backend on the first rollout that shipped it.
         */
        val internalUrl: String = "http://cooknco-frontend",
    )

    data class Backend(
        val url: String,
    )


    data class Smtp(
        val email: String,
        val password: String,
    )

    data class Encryption(
        val key: String,
    ) {
        val aesKey = SecretKeySpec(key.toByteArray(), "AES")
    }
}



fun loadConfig(): Configuration {
    return ConfigLoaderBuilder.default()
        .addFileSource("/app/config/application.yaml", true)
        .addResourceSource("/application.yaml", true)
        .addResourceSource("/application-test.yaml", true)
        .build()
        .loadConfigOrThrow<Configuration>()
}
