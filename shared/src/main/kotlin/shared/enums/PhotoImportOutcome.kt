package shared.enums

/**
 * How one photo import that reached the model ended. Every one of them was billed, which is
 * why each is recorded whatever it came to.
 *
 * Stored by ordinal, like every enum here: append, never reorder.
 */
enum class PhotoImportOutcome {
    /** A recipe came back and was handed to the editor. */
    READ,

    /** The model answered, and there was no recipe on the page. The cook was told to retake it. */
    NOTHING_READ,

    /** The model answered with something that was not the transcription. Refunded to the cook. */
    FAILED,
}
