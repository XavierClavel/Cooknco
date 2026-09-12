package com.xavierclavel.cooknco.ui.i18n

import com.xavierclavel.cooknco.ui.home.DateGroupKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** The app in English. See [Strings]. */
object EnStrings : Strings {

    override val cancel = "Cancel"
    override val save = "Save"
    override val saveCaps = "SAVE"
    override val delete = "Delete"
    override val edit = "Edit"
    override val close = "Close"
    override val back = "Back"
    override val next = "Next"
    override val retry = "Retry"
    override val loading = "Loading"
    override val search = "Search"
    override val show = "Show"
    override val hide = "Hide"
    override val somethingWentWrong = "Something went wrong"

    override val logIn = "Log in"
    override val logInCaps = "LOG IN"
    override val logOut = "Log out"
    override val continueWithGoogle = "Continue with Google"
    override val or = "or"
    override val emailAddress = "Email address"
    override val password = "Password"
    override val forgottenPassword = "Forgotten password?"
    override val newHere = "New here?"
    override val createAnAccount = "Create an account"
    override val username = "Username"
    override val signUp = "Sign up"
    override val alreadyHaveAnAccount = "Already have an account?"

    override val navFeed = "Feed"
    override val navSearch = "Search"
    override val navBooks = "Books"
    override val navMe = "Me"
    override val newRecipe = "New recipe"

    override val today = "Today"
    override val yesterday = "Yesterday"
    override fun daysAgo(days: Long) = "$days days ago"
    override val feedEmpty = "Nothing here yet. Follow a few cooks and their recipes land here."

    override val ingredients = "Ingredients"
    override val steps = "Steps"
    override val notes = "Notes"
    override val startCooking = "Start cooking"
    override val addToCookbook = "Add to a cookbook"
    override val shareRecipe = "Share recipe"
    override val editRecipe = "Edit recipe"
    override val deleteRecipe = "Delete recipe"
    override fun deleteRecipeQuestion(title: String) = "Delete \"$title\"?"
    override val deleteRecipeWarning = "This cannot be undone. The recipe leaves every cookbook it is in."
    override val deleteThisRecipe = "Delete this recipe?"
    override fun deleteRecipeMessage(title: String) = "$title will be removed for everyone. This cannot be undone."
    override val keepIt = "Keep it"
    override val like = "Like"
    override val more = "More"
    override val share = "Share"
    override val shareLink = "Share link"
    override val scaledFor = "Scaled for"
    override val decrease = "Decrease"
    override val increase = "Increase"
    override fun stepNumber(index: Int) = "STEP $index"
    override val noStepsYet = "No steps yet"
    override val myNotesOnlyYouSeeThese = "MY NOTES · ONLY YOU SEE THESE"
    override fun fromTheAuthor(name: String) = "FROM ${name.uppercase()}, THE AUTHOR"
    override val writeNotesHere = "Write notes here!"
    override val writeANoteHere = "Write a note here — it stays on this recipe, on every device."
    override fun stepsCount(count: Int) = if (count == 1) "1 step" else "$count steps"
    override fun aboutMinutesInTotal(minutes: Int) = "about $minutes min in total"
    override fun ovenAt(temperature: Int) = "$temperature °C oven"
    override val serves = "Serves"

    override val whatsCooking = "What's cooking?"
    override val yourProfile = "Your profile"
    override fun newRecipesCount(count: Int) = if (count == 1) "1 new recipe" else "$count new recipes"
    override fun weekday(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        else -> "Sunday"
    }

    override fun onDate(date: LocalDate) = "${monthName(date.month)} ${date.day}, ${date.year}"

    private fun monthName(month: Month) = when (month) {
        Month.JANUARY -> "January"
        Month.FEBRUARY -> "February"
        Month.MARCH -> "March"
        Month.APRIL -> "April"
        Month.MAY -> "May"
        Month.JUNE -> "June"
        Month.JULY -> "July"
        Month.AUGUST -> "August"
        Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "October"
        Month.NOVEMBER -> "November"
        else -> "December"
    }

    override fun dateGroup(key: DateGroupKey) = when (key) {
        DateGroupKey.Today -> today
        DateGroupKey.Yesterday -> yesterday
        is DateGroupKey.Weekday -> weekday(key.dayOfWeek)
        is DateGroupKey.On -> onDate(key.date)
    }

    override val confirmPassword = "Confirm password"
    override val passwordMinEight = "Password must be at least 8 characters"
    override val passwordMinEightPlaceholder = "Password (min. 8 characters)"
    override val invalidEmailOrPassword = "Invalid email or password"
    override val emailAlreadyRegistered = "Email already registered"
    override val usernameAlreadyTaken = "Username already taken"
    override val verifyEmailFirst = "Please verify your email before logging in"
    override val accountUsesGoogle = "This account uses Google Sign-In. Use the Google button to log in."
    override val passwordsDoNotMatch = "Passwords do not match"
    override val fillInAllFields = "Please fill in all fields"
    override val anErrorOccurred = "An error occurred"
    override val cookbooks = "Cookbooks"
    override val profile = "Profile"

    override fun stepOf(step: Int, total: Int) = "STEP $step OF $total"
    override val nextStep = "Next step"
    override val finish = "Finish"
    override fun nextIs(step: String) = "Next: $step"
    override val timerFromThisStep = "Timer from this step"
    override val start = "Start"
    override val pause = "Pause"
}
