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
) {
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
