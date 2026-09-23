package com.xavierclavel.cooknco.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xavierclavel.cooknco.data.AppLocale
import com.xavierclavel.cooknco.ui.home.DateGroupKey
import com.xavierclavel.cooknco.network.IngredientSort
import com.xavierclavel.cooknco.network.ReportReason
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
    val units: String
    val unitsNote: String
    val unitsMetric: String
    val unitsImperial: String
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
    val privacyPolicy: String
    val appVersion: String
    fun clientCount(count: Int): String
    val logOutMessage: String
    val deleteAccount: String
    val deleteAccountMessage: String
    val deleteAccountConfirm: String
    val deleteAccountFailed: String

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

    // The editor's overflow menu: the two ways of filling it in from somewhere else.
    // The subtitle heads both of them, because the promise they make is the same one.
    val newRecipeSubtitle: String

    // Scanning a printed recipe into the editor.
    val scanARecipe: String
    val scanning: String
    val scanFoundNothing: String
    val scanFailed: String
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

    // ── Cook mode ─────────────────────────────────────────────────────────────
    fun stepOf(step: Int, total: Int): String
    /**
     * The same count as [stepOf], for somewhere that is not shouting it.
     *
     * [stepOf] is the screen's label and is set in capitals to sit above the step; this one
     * is a line of the notification, next to the recipe's title, and reads as a sentence.
     */
    fun stepProgress(step: Int, total: Int): String
    val nextStep: String
    val previousStep: String
    val finish: String
    /**
     * What the cook session's notification calls its timer button, for a screen reader
     * rather than for the eye — the button itself is a mark. See `CookSessionNotifier`.
     */
    val startTimer: String
    /** The ingredients of a step past the few the shade has room for. */
    fun andMoreIngredients(count: Int): String
    /**
     * One tick box of the notification's checklist, said aloud.
     *
     * A row there is a line of text with a picture of a box beside it, so a screen reader is
     * told what the box is showing — which is the whole state of the thing being pressed.
     */
    fun ingredientTicked(line: String, ticked: Boolean): String
    fun nextIs(step: String): String
    val timerLabel: String
    val usedInThisStep: String
    val stepTimerPlaceholder: String
    val addToStep: String
    val stepIngredientsLabel: String
    val removeStepIngredients: String
    val noIngredientsToPickYet: String
    val removeStepTimer: String
    val stepPhotoLabel: String
    val removeStepPhoto: String
    val recipeStepPhoto: String
    val minutesShort: String
    val start: String
    val pause: String
    val resume: String
    val stopTimer: String
    /** The button a pan that is not quite done is answered with. */
    val addAMinute: String
    val timerPaused: String
    val timerTimeIsUp: String
    val timerRingOnTimeTitle: String
    val timerRingOnTimeMessage: String
    val timerRingOnTimeConfirm: String
    val timerRingOnTimeDismiss: String

    // ── Reporting something to the moderators ─────────────────────────────────
    val reportRecipe: String
    val reportAccount: String
    val reportTitle: String
    val reportDescription: String
    val reportReasonLabel: String
    /**
     * Takes the enum rather than its name: the moderation queue groups on these, so the
     * set is closed, and an unnamed one is a compile error instead of a screen showing
     * `INAPPROPRIATE_CONTENT` to the person trying to report it.
     */
    fun reportReasonName(reason: ReportReason): String
    val reportDetails: String
    val reportDetailsPlaceholder: String
    val sendReport: String
    val reportSentTitle: String
    val reportSentMessage: String
    val alreadyReported: String
    val cannotReportOwnContent: String
    val reportTargetGone: String
    val reportFailed: String

    // ── No network, and what the app is showing instead ───────────────────────
    /**
     * The banner over content that came off this phone rather than off the server.
     *
     * It names the age of what is on screen, and that is the part that matters. Content of
     * unstated age is worse than an error: nothing on the screen would otherwise tell a cook
     * that the recipe in front of them is the version from before the correction they made
     * last night.
     */
    fun offlineShowingSaved(age: String): String
    /** When the store has never been written — there is nothing to say how old it is. */
    val offlineShowingSavedUnknown: String
    /** Ages, for the banner above. Deliberately coarse: nobody needs the minute. */
    val offlineJustNow: String
    fun offlineHoursAgo(hours: Int): String
    fun offlineDaysAgo(days: Int): String
    /** A recipe reached offline that this phone was not keeping. */
    val offlineRecipeNotSaved: String
    /** Any action that writes, tapped with no network. */
    val offlineCannotDoThat: String

    // ── Keeping recipes on the phone, from the settings screen ────────────────
    val offlineSettingsTitle: String
    val offlineSettingsSubtitle: String
    fun offlineSettingsHolding(recipes: Int, megabytes: Int): String
    val offlineSyncNow: String
    val offlineSyncing: String

    // ── Premium, and what a locked feature says when it is tapped ─────────────
    /** The padlock on a locked action sheet row, for a screen reader. */
    val premiumLocked: String
    val premiumFeatureTitle: String
    /**
     * Names the feature that was tapped rather than saying "this feature": the sheet is
     * gone by the time the dialog is up, so the row it came from cannot be pointed at.
     */
    fun premiumFeatureMessage(feature: String): String

    // ── Exporting a PDF, which a premium account is offered ───────────────────
    val exportRecipePdf: String
    val exportCookbookPdf: String
    /**
     * The same recipe as a Cooklang file — the format other cooking apps read, rather than
     * a sheet to print. Named as a format and not as "export", so the two rows on the sheet
     * say what tells them apart.
     */
    val exportRecipeCooklang: String

    // ── Importing a Cooklang file, which needs no subscription ────────────────
    val importCooklang: String
    val importing: String
    val importCooklangDone: String
    /**
     * How many ingredients arrived as free text. They work; they simply carry no nutrition
     * and no search will find them, which is worth one sentence and not a warning.
     */
    fun importCooklangUnmatched(count: Int): String
    val importCooklangSplit: String
    val importCooklangEmpty: String
    val importCooklangFailed: String
    val preparingPdf: String
    val exportFailedTitle: String
    val exportFailed: String
    /**
     * The one refusal the person asking can do something about: a cookbook past the bound
     * the backend prints (`Configuration.Pdf.maxCookbookRecipes`) is refused rather than
     * printed short. Deliberately vague about the number, which is the server's to change.
     */
    val cookbookTooLargeToExport: String
    val exportRendererBusy: String

    // ── The cook timer, as Android's notification settings list it ────────────
    val timerChannelName: String
    val timerChannelDescription: String
    val timerDoneChannelName: String
    val timerDoneChannelDescription: String

    // ── The recipe being cooked, in the same list ─────────────────────────────
    val cookSessionChannelName: String
    val cookSessionChannelDescription: String
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
