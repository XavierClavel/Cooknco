package com.xavierclavel.cooknco.ui.i18n

import com.xavierclavel.cooknco.ui.home.DateGroupKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** The app in French. See [Strings]. */
object FrStrings : Strings {

    override val cancel = "Annuler"
    override val save = "Enregistrer"
    override val saveCaps = "ENREGISTRER"
    override val delete = "Supprimer"
    override val edit = "Modifier"
    override val close = "Fermer"
    override val back = "Retour"
    override val next = "Suivant"
    override val retry = "Réessayer"
    override val loading = "Chargement"
    override val search = "Rechercher"
    override val show = "Afficher"
    override val hide = "Masquer"
    override val somethingWentWrong = "Une erreur est survenue"

    override val logIn = "Connexion"
    override val logInCaps = "SE CONNECTER"
    override val logOut = "Se déconnecter"
    override val continueWithGoogle = "Continuer avec Google"
    override val or = "ou"
    override val emailAddress = "Adresse e-mail"
    override val password = "Mot de passe"
    override val forgottenPassword = "Mot de passe oublié ?"
    override val newHere = "Nouveau ici ?"
    override val createAnAccount = "Créer un compte"
    override val username = "Nom d'utilisateur"
    override val signUp = "S'inscrire"
    override val alreadyHaveAnAccount = "Vous avez déjà un compte ?"

    override val navFeed = "Fil"
    override val navSearch = "Recherche"
    override val navBooks = "Carnets"
    override val navMe = "Moi"
    override val newRecipe = "Nouvelle recette"

    override val today = "Aujourd'hui"
    override val yesterday = "Hier"
    override fun daysAgo(days: Long) = "Il y a $days jours"
    override val feedEmpty = "Rien pour l'instant. Suivez quelques cuisiniers et leurs recettes arriveront ici."

    override val ingredients = "Ingrédients"
    override val steps = "Étapes"
    override val notes = "Notes"
    override val startCooking = "Commencer"
    override val addToCookbook = "Ajouter à un carnet"
    override val shareRecipe = "Partager la recette"
    override val editRecipe = "Modifier la recette"
    override val deleteRecipe = "Supprimer la recette"
    override fun deleteRecipeQuestion(title: String) = "Supprimer « $title » ?"
    override val deleteRecipeWarning = "C'est définitif. La recette quitte tous les carnets où elle se trouve."
    override val deleteThisRecipe = "Supprimer cette recette ?"
    override fun deleteRecipeMessage(title: String) = "« $title » disparaîtra pour tout le monde. C'est définitif."
    override val keepIt = "La garder"
    override val like = "J'aime"
    override val more = "Plus"
    override val share = "Partager"
    override val shareLink = "Partager le lien"
    override val scaledFor = "Ajusté pour"
    override val decrease = "Diminuer"
    override val increase = "Augmenter"
    override fun stepNumber(index: Int) = "ÉTAPE $index"
    override val noStepsYet = "Aucune étape pour l'instant"
    override val myNotesOnlyYouSeeThese = "MES NOTES · VOUS SEUL LES VOYEZ"
    override fun fromTheAuthor(name: String) = "DE ${name.uppercase()}, L'AUTEUR"
    override val writeNotesHere = "Écrivez vos notes ici !"
    override val writeANoteHere = "Écrivez une note ici — elle reste sur cette recette, sur tous vos appareils."
    override fun stepsCount(count: Int) = if (count == 1) "1 étape" else "$count étapes"
    override fun aboutMinutesInTotal(minutes: Int) = "environ $minutes min au total"
    override fun ovenAt(temperature: Int) = "four à $temperature °C"
    override val serves = "Pour"

    override val whatsCooking = "On cuisine quoi ?"
    override val yourProfile = "Votre profil"
    override fun newRecipesCount(count: Int) = if (count == 1) "1 nouvelle recette" else "$count nouvelles recettes"
    override fun weekday(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "Lundi"
        DayOfWeek.TUESDAY -> "Mardi"
        DayOfWeek.WEDNESDAY -> "Mercredi"
        DayOfWeek.THURSDAY -> "Jeudi"
        DayOfWeek.FRIDAY -> "Vendredi"
        DayOfWeek.SATURDAY -> "Samedi"
        else -> "Dimanche"
    }

    override fun onDate(date: LocalDate) = "${date.day} ${monthName(date.month)} ${date.year}"

    private fun monthName(month: Month) = when (month) {
        Month.JANUARY -> "janvier"
        Month.FEBRUARY -> "février"
        Month.MARCH -> "mars"
        Month.APRIL -> "avril"
        Month.MAY -> "mai"
        Month.JUNE -> "juin"
        Month.JULY -> "juillet"
        Month.AUGUST -> "août"
        Month.SEPTEMBER -> "septembre"
        Month.OCTOBER -> "octobre"
        Month.NOVEMBER -> "novembre"
        else -> "décembre"
    }

    override fun dateGroup(key: DateGroupKey) = when (key) {
        DateGroupKey.Today -> today
        DateGroupKey.Yesterday -> yesterday
        is DateGroupKey.Weekday -> weekday(key.dayOfWeek)
        is DateGroupKey.On -> onDate(key.date)
    }

    override val confirmPassword = "Confirmer le mot de passe"
    override val passwordMinEight = "Le mot de passe doit faire au moins 8 caractères"
    override val passwordMinEightPlaceholder = "Mot de passe (8 caractères min.)"
    override val invalidEmailOrPassword = "E-mail ou mot de passe incorrect"
    override val emailAlreadyRegistered = "Cet e-mail est déjà utilisé"
    override val usernameAlreadyTaken = "Ce nom d'utilisateur est déjà pris"
    override val verifyEmailFirst = "Vérifiez votre e-mail avant de vous connecter"
    override val accountUsesGoogle = "Ce compte utilise la connexion Google. Utilisez le bouton Google."
    override val passwordsDoNotMatch = "Les mots de passe ne correspondent pas"
    override val fillInAllFields = "Remplissez tous les champs"
    override val anErrorOccurred = "Une erreur est survenue"
    override val cookbooks = "Carnets"
    override val profile = "Profil"

    override fun stepOf(step: Int, total: Int) = "ÉTAPE $step SUR $total"
    override val nextStep = "Étape suivante"
    override val finish = "Terminer"
    override fun nextIs(step: String) = "Ensuite : $step"
    override val timerFromThisStep = "Minuteur pour cette étape"
    override val start = "Démarrer"
    override val pause = "Pause"
}
