package shared.enums

/** Decision a moderator takes when closing a report. */
enum class ModerationAction {
    /** Nothing wrong with the content: the report is closed as DISMISSED. */
    DISMISS,

    /** Content stays in database but is no longer served to anyone but its owner. */
    HIDE_CONTENT,

    /** Content is removed for good. */
    DELETE_CONTENT,

    /** Author loses access until a date, and their content is hidden. */
    SUSPEND_AUTHOR,

    /** Author loses access permanently, and their content is hidden. */
    BAN_AUTHOR,
}
