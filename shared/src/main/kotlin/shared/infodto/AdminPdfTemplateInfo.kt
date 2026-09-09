package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.Locale

/**
 * One kind of document, as the backoffice documents tab shows it.
 *
 * There is no `builtIn` flag, unlike [AdminEmailTemplateInfo]: every kind is emitted by
 * code and packaged with the app, so every one of them can always be restored and none can
 * be removed.
 */
@Serializable
data class AdminPdfTemplateInfo(
    val key: String,
    /** Names the layout may refer to. See `PdfVariable` for how a section is written. */
    val variables: List<String>,
    /** One entry per locale the app renders in, always in the same order. */
    val locales: List<AdminPdfTemplateLocale>,
)

/** The layout rendered for one locale, and where it comes from. */
@Serializable
data class AdminPdfTemplateLocale(
    val locale: Locale,
    val body: String,
    /** False while the layout packaged with the app is the one being rendered. */
    val custom: Boolean,
    /** Epoch seconds. Null while nothing has been saved over the packaged layout. */
    val updatedAt: Long? = null,
)
