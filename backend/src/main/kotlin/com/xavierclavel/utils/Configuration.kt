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

    /** Defaulted whole, for the reason [pdf] is. */
    val images: Images = Images(),

    /** Defaulted whole, for the reason [pdf] is. */
    val backups: Backups = Backups(),

    /** Defaulted whole, and off by default, for the reason [push] is. See [Ai]. */
    val ai: Ai = Ai(),
) {
    /**
     * The model provider behind the AI features — today the premium recipe scan, which reads a
     * picture of a recipe into the editor — and the bounds they share.
     *
     * **Provider-agnostic by configuration.** Any service speaking the OpenAI chat-completions
     * API works — Scaleway, OVHcloud, Mistral, DeepSeek, OpenRouter — and moving between them
     * is [baseUrl], [apiKey] and [model], with no release. Which one is a decision about price
     * and where the photographs are processed; `docs/pending-setup.md` has the options.
     *
     * Off unless all three are set, because the key is a credential that bills somebody: a
     * developer's backend and the test suite get a reader that answers 503, and the rest of
     * the product is unaffected.
     */
    data class Ai(
        /** Up to and including the version segment, e.g. `https://api.scaleway.ai/v1`. */
        val baseUrl: String = "",
        /** Lives in the cluster's `cooknco-config` secret with the rest of this file. Never logged. */
        val apiKey: String = "",
        /** The provider's own model id, e.g. `mistral-small-3.2-24b-instruct-2506`. */
        val model: String = "",

        /** See `OpenAiCompatiblePhotoReader.jsonMode`. */
        val jsonMode: Boolean = true,
        /** See `OpenAiCompatiblePhotoReader.maxOutputTokens`. */
        val maxOutputTokens: Int = 4096,
        val timeoutSeconds: Long = 90,

        /**
         * Pages one recipe scan may carry. A recipe over a spread is two; the app's scanner stops
         * at four, and this matches it — every page is another image billed.
         */
        val maxPhotos: Int = 4,

        /**
         * The largest single page read into memory. The app sends pages scaled down to what
         * a model actually looks at (providers resize to about 1.3 megapixels anyway), which
         * is a few hundred kilobytes; this is a bound on what a premium route will hold, set
         * well above it.
         */
        val maxPhotoBytes: Long = 8L * 1024 * 1024,

        /**
         * AI requests one account may make per UTC day, across every feature, admins included —
         * **until the backoffice AI tab saves its own** (`AiSettings`), which then wins. The
         * month's budget and the token prices live only there: they are an operator's to change
         * without a restart.
         *
         * Every request is a paid call, so this is what turns "premium" into a bounded cost
         * rather than an open tap — a script on a premium account would otherwise spend
         * whatever the provider's own limit allows. Generous next to any cook's use: nobody
         * types in thirty recipes a day.
         */
        val dailyRequestsPerUser: Int = 30,

        /**
         * Requests to the model in flight at once, across the backend and every feature. Each holds a request open for
         * several seconds; past this they wait, and past [queueSeconds] they are told to
         * come back, for the reason the PDF renderer bounds its prints.
         */
        val maxConcurrentRequests: Int = 4,
        val queueSeconds: Long = 20,
    ) {
        val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    /**
     * What the backoffice measures the database dumps against.
     *
     * Every value here restates a line of `k8s/base/backup.yaml`, because the backend
     * reads the volume and never the CronJob: it has no way to ask what the schedule is.
     * They are configuration rather than constants so that a change to the manifest can be
     * matched without a release — but the manifest is the thing that decides, and these
     * drifting from it only makes the page wrong about a job that is working fine.
     */
    data class Backups(
        /** The CronJob's schedule: `15 2 * * *`, so one dump per database per night. */
        val intervalHours: Long = 24,

        /**
         * How long past its window a dump may be before it is called late.
         *
         * Covers `startingDeadlineSeconds: 3600` — a run displaced by an hour is still the
         * night's backup — plus the dump's own runtime. Without it, every morning the job
         * started a minute late would report a missed night.
         */
        val graceHours: Long = 2,

        /** Matches `find /backups -name '*.dump' -mtime +14`, i.e. how many nights are kept. */
        val retentionDays: Int = 14,

        /**
         * How much smaller than its predecessor a dump has to be to be flagged.
         *
         * Set well past anything ordinary use produces: a database that loses a third of
         * its content overnight is news either way, but half is not something deleting
         * recipes does, and a threshold that cries wolf is one nobody reads.
         */
        val shrinkRatio: Double = 0.5,
    )

    data class Images(
        /**
         * The largest picture the ticket endpoint reads into memory
         * (`ImageController.redeemRecipeImageTicket`).
         *
         * Matches `client_max_body_size` in `frontend/nginx.conf`, which is what stops a
         * larger one reaching the backend at all: this is the same bound restated where a
         * caller gets a reason for it instead of the edge's own 413, and it is enforced
         * here because that endpoint is the one reachable without an account behind it.
         * Lowering it below the proxy's is safe; raising it above only moves the refusal.
         */
        val maxUploadBytes: Long = 20L * 1024 * 1024,
    )

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

        /**
         * How many recipes a cookbook may hold and still be exported as one book.
         *
         * A bound rather than a truncation: a book printed silently short is one whose
         * missing half nobody notices until it is handed over. Past this the export is
         * refused and says so.
         *
         * Sized against the print, which is what actually fails: every recipe is a page
         * with a picture posted alongside it, so a cookbook of a few hundred would send
         * tens of megabytes into Chromium and time the request out (`requestTimeoutMillis`
         * in [com.xavierclavel.services.GotenbergPdfRenderer]) rather than come back short.
         */
        val maxCookbookRecipes: Int = 100,
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

        /**
         * The bcrypt work factor every password is hashed at.
         *
         * A production value, and the default is the one to ship: it is what makes a stolen
         * `password_hash` column expensive to walk. It is configurable for the test suite
         * alone, which overrides it in `application-test.yaml` — at 12 a single hash costs
         * ~300ms, and a suite that creates two fixture users and an admin before every one
         * of its tests spends nearly all of its wall clock here rather than on what it is
         * testing. The factor is encoded in the hash itself, so nothing verifies
         * differently and a hash written at one cost still checks out at another.
         */
        val passwordCost: Int = 12,
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
