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
) {
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
