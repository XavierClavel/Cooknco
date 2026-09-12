package com.xavierclavel.cooknco.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xavierclavel.cooknco.data.AppLocale
import com.xavierclavel.cooknco.ui.home.DateGroupKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Every word the app says, in one place, once per language.
 *
 * **Why not `stringResource` and `composeResources/values-fr`.** Compose Multiplatform picks
 * its resource locale from the platform at startup: switching language would mean restarting
 * the app, and the language here is a *setting* that has to take effect when it is tapped.
 * A plain interface also reaches places a `@Composable` cannot — view models produce copy
 * too ("Failed to save", the feed's "Today"), and they would otherwise have to hand back
 * enum cases for the UI to translate.
 *
 * **Why an interface rather than a map.** A missing translation is a compile error. There is
 * no fallback path that silently prints English inside a French screen, because there is no
 * way to leave one out.
 *
 * Anything with a value in it is a function, not a template with `%s` in it: the argument
 * lands where that language puts it, which is not always where English does.
 */
interface Strings {

    // ── Words that belong to no screen in particular ──────────────────────────
    val cancel: String
    val save: String
    val saveCaps: String
    val delete: String
    val edit: String
    val close: String
    val back: String
    val next: String
    val retry: String
    val loading: String
    val search: String
    val show: String
    val hide: String
    val somethingWentWrong: String

    // ── Sign in / sign up ─────────────────────────────────────────────────────
    val logIn: String
    val logInCaps: String
    val logOut: String
    val continueWithGoogle: String
    val or: String
    val emailAddress: String
    val password: String
    val forgottenPassword: String
    val newHere: String
    val createAnAccount: String
    val username: String
    val signUp: String
    val alreadyHaveAnAccount: String

    // ── Bottom navigation ─────────────────────────────────────────────────────
    val navFeed: String
    val navSearch: String
    val navBooks: String
    val navMe: String
    val newRecipe: String

    // ── Feed ──────────────────────────────────────────────────────────────────
    val today: String
    val yesterday: String
    fun daysAgo(days: Long): String
    val feedEmpty: String

    // ── Recipe ────────────────────────────────────────────────────────────────
    val ingredients: String
    val steps: String
    val notes: String
    val startCooking: String
    val addToCookbook: String
    val shareRecipe: String
    val editRecipe: String
    val deleteRecipe: String
    fun deleteRecipeQuestion(title: String): String
    val deleteRecipeWarning: String
    val deleteThisRecipe: String
    fun deleteRecipeMessage(title: String): String
    val keepIt: String
    val like: String
    val more: String
    val share: String
    val shareLink: String
    val scaledFor: String
    val decrease: String
    val increase: String
    fun stepNumber(index: Int): String
    val noStepsYet: String
    val myNotesOnlyYouSeeThese: String
    fun fromTheAuthor(name: String): String
    val writeNotesHere: String
    val writeANoteHere: String
    fun stepsCount(count: Int): String
    fun aboutMinutesInTotal(minutes: Int): String
    fun ovenAt(temperature: Int): String
    val serves: String

    // ── Feed, continued ───────────────────────────────────────────────────────
    val whatsCooking: String
    val yourProfile: String
    fun newRecipesCount(count: Int): String
    fun weekday(day: DayOfWeek): String
    fun onDate(date: LocalDate): String

    /** What a feed group of recipes is called — see `DateGroupKey`. */
    fun dateGroup(key: DateGroupKey): String

    // ── Sign in / sign up, continued ──────────────────────────────────────────
    val confirmPassword: String
    val passwordMinEight: String
    val passwordMinEightPlaceholder: String
    val invalidEmailOrPassword: String
    val emailAlreadyRegistered: String
    val usernameAlreadyTaken: String
    val verifyEmailFirst: String
    val accountUsesGoogle: String
    val passwordsDoNotMatch: String
    val fillInAllFields: String
    val anErrorOccurred: String
    val cookbooks: String
    val profile: String

    // ── Cook mode ─────────────────────────────────────────────────────────────
    fun stepOf(step: Int, total: Int): String
    val nextStep: String
    val finish: String
    fun nextIs(step: String): String
    val timerFromThisStep: String
    val start: String
    val pause: String
}

/**
 * The language the tree below is written in.
 *
 * `staticCompositionLocalOf`: it changes rarely and everything reads it, so a change should
 * recompose the whole tree rather than have every reader tracked individually.
 */
val LocalStrings: ProvidableCompositionLocal<Strings> = staticCompositionLocalOf { EnStrings }

/** The copy for the current language. `val s = strings()` at the top of a composable. */
@Composable
@ReadOnlyComposable
fun strings(): Strings = LocalStrings.current

/** The copy for a language, for the places that have no composition to read it from. */
fun stringsFor(locale: AppLocale): Strings = when (locale) {
    AppLocale.FR -> FrStrings
    AppLocale.EN -> EnStrings
}
