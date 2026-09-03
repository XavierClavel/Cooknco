package shared.enums

/** How a file on the image volume relates to the entity that should own it. */
enum class ImageStatus {
    /** In use: the owner exists and points at this exact version, or the file is a shipped default. */
    CURRENT,

    /** The owner exists but has moved on to another version — a superseded upload. */
    STALE,

    /** No entity carries this id any more; whatever owned the file is gone. */
    ORPHAN,

    /** The owner points at a version that is not on disk — a broken image. */
    MISSING,

    /** Not named like anything the app writes. Left alone unless explicitly cleaned up. */
    UNKNOWN,
    ;

    /** Only these can be removed in bulk: [CURRENT] is live and [MISSING] has no file. */
    fun isReclaimable() = this == STALE || this == ORPHAN || this == UNKNOWN
}
