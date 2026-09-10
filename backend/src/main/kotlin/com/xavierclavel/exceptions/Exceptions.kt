package com.xavierclavel.exceptions

class UnauthorizedException(cause: UnauthorizedCause): Exception(cause.key)

class ForbiddenException(cause: ForbiddenCause): Exception(cause.key)

class BadRequestException(cause: BadRequestCause): Exception(cause.key)

class NotFoundException(cause: NotFoundCause): Exception(cause.key)

/**
 * Something the request needs is not answering right now.
 *
 * Distinct from a 500 on purpose: nothing here is wrong with the request or with this
 * service, and the caller is being told to try again rather than to change what it sent.
 */
class ServiceUnavailableException(cause: ServiceUnavailableCause): Exception(cause.key)

enum class UnauthorizedCause(val key: String) {
    SESSION_NOT_FOUND("session_not_found"),
    USER_NOT_VERIFIED("user_not_verified"),
    INVALID_PASSWORD("invalid_password"),
    INVALID_MAIL_OR_PASSWORD("invalid_mail_or_password"),
    INVALID_TOKEN("invalid_token"),
    OAUTH_FAILED("oauth_failed"),
    OAUTH_NOT_SETUP("oauth_not_setup"),
    ACCOUNT_SUSPENDED("account_suspended"),
    ACCOUNT_BANNED("account_banned"),
}

enum class ForbiddenCause(val key: String) {
    NOT_ALLOWED_TO_EDIT_RECIPE("not_allowed_to_edit_recipe"),
    NOT_ALLOWED_TO_REMOVE_RECIPE("not_allowed_to_remove_recipe"),
    NOT_ALLOWED_TO_EDIT_USER("not_allowed_to_edit_user"),
    NOT_ALLOWED_TO_SEE_RECIPE("not_allowed_to_see_recipe"),
    NOT_ALLOWED_TO_SEE_COOKBOOK("not_allowed_to_see_cookbook"),
    NOT_MEMBER_OF_COOKBOOK("not_member_of_cookbook"),
    ACCOUNT_NOT_PUBLIC("account_not_public"),
    MUST_BE_COOKBOOK_ADMINISTRATOR("must_be_cookbook_administrator"),
    NOT_ALLOWED_TO_DEMOTE_LAST_ADMIN("not_allowed_to_demote_last_admin"),
    NOT_ALLOWED_TO_MODERATE_ADMIN("not_allowed_to_moderate_admin"),
}

enum class NotFoundCause(val key: String) {
    USER_NOT_FOUND("user_not_found"),
    RECIPE_NOT_FOUND("recipe_not_found"),
    COOKBOOK_NOT_FOUND("cookbook_not_found"),
    INGREDIENT_NOT_FOUND("ingredient_not_found"),
    MAIL_NOT_FOUND("mail_not_found"),
    FOLLOW_NOT_FOUND("follow_not_found"),
    NOTES_NOT_FOUND("notes_not_found"),
    REPORT_NOT_FOUND("report_not_found"),
    REPORT_TARGET_NOT_FOUND("report_target_not_found"),
    MAIL_TEMPLATE_NOT_FOUND("mail_template_not_found"),
    PDF_TEMPLATE_NOT_FOUND("pdf_template_not_found"),
    NOTIFICATION_NOT_FOUND("notification_not_found"),
}

enum class ServiceUnavailableCause(val key: String) {
    PDF_RENDERER_UNAVAILABLE("pdf_renderer_unavailable"),
    PDF_RENDERER_BUSY("pdf_renderer_busy"),
    PDF_RENDERER_FAILED("pdf_renderer_failed"),
}

enum class BadRequestCause (val key: String) {
    INVALID_REQUEST("invalid_request"),
    INVALID_IMAGE("invalid_image"),

    TOKEN_MISSING("token_missing"),
    MAIL_MISSING("mail_missing"),

    USER_ALREADY_FOLLOWED("user_already_followed"),
    USER_NOT_FOLLOWED("user_not_followed"),
    NO_FOLLOW_REQUEST("no_follow_request"),

    NOT_APPLICABLE_ON_SELF("not_applicable_on_self"),
    MAIL_ALREADY_USED("mail_already_used"),
    USERNAME_ALREADY_USED("username_already_used"),
    RECIPE_ALREADY_IN_COOKBOOK("recipe_already_in_cookbook"),
    RECIPE_NOT_IN_COOKBOOK("recipe_not_in_cookbook"),

    OAUTH_ONLY("oauth_only"),

    ALREADY_REPORTED("already_reported"),
    CANNOT_REPORT_OWN_CONTENT("cannot_report_own_content"),
    REPORT_ALREADY_RESOLVED("report_already_resolved"),
    ACTION_NOT_APPLICABLE_TO_TARGET("action_not_applicable_to_target"),
    INVALID_INGREDIENT_ROW("invalid_ingredient_row"),
    CUSTOM_INGREDIENT_NAME_TOO_LONG("custom_ingredient_name_too_long"),
    UNIT_NOT_ALLOWED_FOR_INGREDIENT("unit_not_allowed_for_ingredient"),
    INVALID_AMOUNT("invalid_amount"),
    INVALID_CONVERSION_FACTOR("invalid_conversion_factor"),

    INVALID_MAIL_ADDRESS("invalid_mail_address"),
    MAIL_TEMPLATE_KEY_INVALID("mail_template_key_invalid"),
    MAIL_TEMPLATE_ALREADY_EXISTS("mail_template_already_exists"),
    MAIL_TEMPLATE_IS_BUILT_IN("mail_template_is_built_in"),
    MAIL_TEMPLATE_HAS_NO_PACKAGED_WORDING("mail_template_has_no_packaged_wording"),
    MAIL_TEMPLATE_EMPTY("mail_template_empty"),
    MAIL_TEMPLATE_TOO_LONG("mail_template_too_long"),
    MAIL_TEMPLATE_MISSING_PLACEHOLDER("mail_template_missing_placeholder"),

    NOTIFICATION_EMPTY("notification_empty"),
    NOTIFICATION_TOO_LONG("notification_too_long"),
    NOTIFICATION_HAS_NO_AUDIENCE("notification_has_no_audience"),
    NOTIFICATION_HAS_NO_DEVICE("notification_has_no_device"),

    PDF_TEMPLATE_EMPTY("pdf_template_empty"),
    PDF_TEMPLATE_TOO_LONG("pdf_template_too_long"),
    PDF_TEMPLATE_MALFORMED("pdf_template_malformed"),

    APP_VERSION_INVALID("app_version_invalid"),
    APP_VERSION_MINIMUM_ABOVE_LATEST("app_version_minimum_above_latest"),
    APP_VERSION_STORE_URL_INVALID("app_version_store_url_invalid"),

}