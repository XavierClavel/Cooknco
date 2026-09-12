package com.xavierclavel.cooknco.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xavierclavel.cooknco.data.AppLocale
import com.xavierclavel.cooknco.ui.home.DateGroupKey
import com.xavierclavel.cooknco.network.IngredientSort
import com.xavierclavel.cooknco.ui.recipe.SearchScope
import com.xavierclavel.cooknco.network.RecipeSort
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
    val portionsUnit: String
    val minutesUnit: String
    val degreesUnit: String
    fun portions(count: Int): String
    fun publishedOn(date: String): String
    fun dayAndMonth(date: LocalDate): String
    val yourRecipe: String
    val prepCaps: String
    val cookCaps: String
    val ovenCaps: String
    fun minutes(value: Int): String
    fun degrees(value: Int): String
    val serves: String

    // ── Feed, continued ───────────────────────────────────────────────────────
    fun fullDate(date: LocalDate): String
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

    // ── Settings ──────────────────────────────────────────────────────────────
    val settings: String
    val language: String
    val languageNote: String
    val privacy: String
    val publicAccount: String
    val publicAccountNote: String
    val autoAcceptFollows: String
    val autoAcceptAlwaysOn: String
    val autoAcceptAnyone: String
    val notifications: String
    val pushOnThisDevice: String
    val pushNote: String
    val emailNotifications: String
    val emailNotificationsNote: String
    val account: String
    val changePassword: String
    val mcpAccess: String
    val appVersion: String
    fun clientCount(count: Int): String
    val logOutMessage: String

    // ── Change password ───────────────────────────────────────────────────────
    val currentPasswordCaps: String
    val newPasswordCaps: String
    val newPasswordAgainCaps: String
    val atLeastEightCharacters: String
    val savePasswordCaps: String
    val changingPasswordSignsOut: String
    val notYourCurrentPassword: String
    val couldNotChangePassword: String

    // ── MCP access ────────────────────────────────────────────────────────────
    val mcpIntro: String
    val mcpEmpty: String
    fun revokeQuestion(client: String): String
    val revokeMessage: String
    val revoke: String

    // ── Followers and following ───────────────────────────────────────────────
    fun followersTab(count: Int): String
    fun followingTab(count: Int): String
    fun followingSince(days: Long): String
    fun requestedSince(days: Long): String
    fun followedSince(days: Long): String
    fun connectedSince(days: Long): String
    fun usedSince(days: Long): String
    val pendingRequests: String
    fun acceptedCount(count: Int): String
    val noFollowersYet: String
    val requestedWaiting: String
    fun followingCount(count: Int): String
    val notFollowingAnyone: String
    val unfollow: String
    fun unfollowQuestion(name: String): String
    fun unfollowMessage(name: String): String
    val accept: String
    val decline: String

    // ── Cookbooks ─────────────────────────────────────────────────────────────
    val newCookbook: String
    val addToCookbookHint: String
    val inThisCookbook: String
    val noCookbooksYet: String
    val done: String
    val editCookbook: String
    val shared: String
    val recipes: String
    val members: String
    fun cookbookCount(count: Int, capped: Boolean = false): String
    val noCookbooksFound: String
    val noIngredientsFound: String
    fun memberCount(count: Int): String
    val leaveCookbook: String
    val leaveCookbookQuestion: String
    val leave: String
    fun deleteCookbookQuestion(title: String): String
    fun deleteCookbookMessage(members: Int, recipes: Int): String
    val deleteCookbook: String
    fun addedBy(name: String): String
    val you: String
    val admin: String
    val cookbookPhoto: String
    val changeCover: String
    val addCover: String
    val uploadedWhenYouSave: String
    val titleRequiredCaps: String
    val title: String
    val descriptionCaps: String
    val visibilityCaps: String
    val membersCaps: String
    val addMember: String
    val visibilityPrivate: String
    val visibilityProtected: String
    val visibilityPublic: String
    val removeMember: String
    val searchAMember: String

    // ── Search ────────────────────────────────────────────────────────────────
    val searchARecipe: String
    val searchEllipsis: String
    val clear: String
    val everything: String
    fun noResultsFor(query: String): String
    val nothingHereYet: String
    val ingredientsCaps: String
    val recipesCaps: String
    val peopleCaps: String
    val cookbooksCaps: String
    fun seeAll(count: Int, approximate: Boolean): String
    val myRecipes: String
    val liked: String
    val noLikesYet: String
    val noRecipesYet: String
    val noUsersYet: String
    fun recipeCount(count: Int): String
    fun userCount(count: Int): String

    fun searchScopeName(scope: SearchScope): String
    fun recipeSortName(sort: RecipeSort): String
    fun byAuthor(name: String): String
    fun ingredientCount(count: Int): String
    fun ingredientSortName(sort: IngredientSort): String
    // ── Profile ───────────────────────────────────────────────────────────────
    val editProfile: String
    val shareProfile: String
    val follow: String
    fun followersLink(count: Int): String
    fun followingLink(count: Int): String
    val followers: String
    val following: String
    val followingState: String
    val profilePhoto: String
    val changePhoto: String
    val tapToChangePhoto: String
    val usernameCaps: String
    val bioCaps: String

    // ── Update, verification ──────────────────────────────────────────────────
    val timeToUpdate: String
    val updateRequiredMessage: String
    fun latestVersion(version: String): String
    val updateNow: String
    val updateAvailable: String
    val update: String
    val notNow: String
    val checkYourEmail: String
    val verificationSent: String
    val backToLogin: String

    // ── Recipe editor ─────────────────────────────────────────────────────────
    val editRecipeTitle: String
    val stepBasics: String
    val stepIngredients: String
    val stepSteps: String
    val stepPhoto: String
    val theBasics: String
    val theBasicsSubtitle: String
    val nameYourRecipe: String
    val aLineAboutTheDish: String
    val timesAndYield: String
    val dishClass: String
    val ingredientsSubtitle: String
    fun addedCount(count: Int): String
    val nothingYetSearchAbove: String
    val searchAnIngredient: String
    fun addAsCustom(name: String): String
    fun removeNamed(name: String): String
    val stepsSubtitle: String
    val addStep: String
    val tipsOptional: String
    val tipsPlaceholder: String
    val describeThisStep: String
    val dragToReorder: String
    val removeStep: String
    val photoAndPublish: String
    val photoSubtitle: String
    val recipePhoto: String
    val tapToAddPhoto: String
    val takeAPhoto: String
    val chooseAnother: String
    val readyToPublish: String
    val noIngredientsYet: String
    fun ingredientsAllFromCatalogue(count: Int): String
    fun ingredientsAdded(count: Int): String
    fun cookMinutes(minutes: String): String
    val untitledRecipe: String
    val publishCaps: String
    fun nextStepLabel(step: String): String
    fun dishClassName(value: String): String
    fun unitName(value: String): String
    val yieldLabel: String
    val yieldHint: String
    val prepTime: String
    val prepHint: String
    val cookTime: String
    val cookHint: String
    val ovenTemp: String
    val ovenHint: String
    fun totalMinutes(total: Int): String
    val noTimesYet: String
    val sectionWeight: String
    val sectionVolume: String
    val sectionCount: String
    fun setValue(label: String): String
    val clearValue: String
    val custom: String
    fun ingredientTypeName(type: String): String

    // ── One ingredient ────────────────────────────────────────────────────────
    val measuredIn: String
    fun unitDefault(unit: String): String
    val inYourRecipes: String
    val popularWithThis: String
    val nothingCookedWithThis: String
    fun ingredientType(type: String): String

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
