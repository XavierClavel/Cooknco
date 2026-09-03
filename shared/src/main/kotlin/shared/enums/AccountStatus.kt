package shared.enums

/** Account state as shown in the admin backoffice. */
enum class AccountStatus {
    /** Signed up but never confirmed their mail address. */
    UNVERIFIED,
    ACTIVE,
    SUSPENDED,
    BANNED,
}
