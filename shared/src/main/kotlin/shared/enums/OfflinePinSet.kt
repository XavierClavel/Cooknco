package shared.enums

/**
 * Which of an account's three collections keeps a recipe on its phone.
 *
 * A recipe is commonly in more than one — your own recipe, in your own cookbook — and leaves
 * the phone only when it is in none. Stored and sent by name, and the app's own `PinSet`
 * spells the same three, so the two decode into each other.
 */
enum class OfflinePinSet {
    OWN,
    LIKED,
    COOKBOOK,
}
