import { CONTACT_EMAIL, type LegalDocuments } from '@/locales/legal/shared'

/**
 * Politique de confidentialité et notice de suppression, en français. Suit `en.ts` section
 * par section : les deux disent la même chose du service, sans être la traduction des
 * phrases de l'autre.
 *
 * Tout ce qui est écrit ici, le code le rend vrai. Quand ce que le service stocke, envoie ou
 * conserve change, les deux fichiers changent dans le même commit : une politique juste dans
 * une langue est une politique fausse dans l'autre, et c'est la moitié fausse que personne
 * ne relit.
 */
const documents: LegalDocuments = {

  // ── Politique de confidentialité ───────────────────────────────────────────
  privacy: {
    title: "Politique de confidentialité",
    updated: "Dernière mise à jour le 18 septembre 2026",
    intro: [
      "Cook'n'Co est un carnet de recettes : vous y notez les plats que vous cuisinez, vous les retrouvez au moment de les refaire, et vous les partagez avec qui vous voulez. Cette page dit ce que le service conserve sur vous, pourquoi, qui d'autre le voit, et comment tout faire disparaître.",
      "L'application et cooknco.eu sont un seul service et un seul compte : cette politique couvre les deux.",
    ],
    sections: [
      {
        heading: "Qui est responsable",
        body: [
          `Cook'n'Co est édité par Xavier Clavel, en tant que personne physique — il n'y a pas de société derrière. Pour tout ce qui figure sur cette page, y compris pour demander ce qui est conservé sur vous ou sa suppression, l'adresse est ${CONTACT_EMAIL}.`,
          "Le service est exploité depuis la France et cette politique applique le RGPD.",
        ],
      },
      {
        heading: "Ce que contient votre compte",
        bullets: [
          {
            term: "Votre adresse e-mail",
            text: "stockée chiffrée. Ce que la connexion compare est une empreinte à sens unique, calculée à part : l'adresse elle-même n'est jamais comparée en clair.",
          },
          {
            term: "Votre mot de passe",
            text: "conservé uniquement sous forme d'empreinte bcrypt, qu'on ne peut pas retransformer en mot de passe. Personne ne peut le lire, l'éditeur compris.",
          },
          {
            term: "La connexion Google",
            text: "si vous vous êtes inscrit avec Google, le service reçoit de Google un identifiant de compte et votre adresse e-mail, rien d'autre. Votre mot de passe Google ne lui parvient jamais.",
          },
          {
            term: "Votre profil",
            text: "pseudonyme, biographie et photo, date d'inscription et date de dernière activité.",
          },
          {
            term: "Vos réglages",
            text: "la langue dans laquelle on vous écrit, les unités métriques ou impériales, vos restrictions alimentaires, le caractère public ou non de votre compte, et l'acceptation automatique des demandes d'abonnement.",
          },
        ],
      },
      {
        heading: "Ce que vous écrivez",
        body: [
          "Vos recettes et leurs photos, leurs étapes, leurs durées et leurs ingrédients ; vos livres de recettes ; ce que vous avez aimé et les personnes que vous suivez ; et les notes privées que vous gardez sur une recette.",
          "Votre compte est public ou sur demande, et c'est ce réglage qui décide qui lit vos recettes. Vos notes privées sont la seule chose qui n'a aucun réglage : elles ne sont montrées qu'à vous, jamais à personne d'autre.",
        ],
      },
      {
        heading: "Vos appareils et les notifications",
        body: [
          "Un téléphone dont les notifications sont activées est enregistré avec le jeton que Google délivre à cette installation, la plateforme, la version de l'application, la langue de l'appareil, et les dates de premier et de dernier contact. C'est cette ligne qui reçoit une notification.",
          "Elle disparaît à la déconnexion, quand vous coupez les notifications, et quand Google signale le jeton comme mort. Les versions de l'application sont par ailleurs comptées ensemble — jamais par personne — pour savoir combien d'installations une version minimale bloquerait.",
        ],
      },
      {
        heading: "Les e-mails que le service envoie",
        body: [
          "Trois sortes : la confirmation de votre adresse à l'inscription, la réinitialisation du mot de passe quand vous la demandez, et — uniquement si vous les activez, car elles sont désactivées par défaut — les nouvelles des personnes que vous suivez.",
          "Ils partent par les serveurs de messagerie de Gmail : Google les achemine comme n'importe quel fournisseur achemine du courrier.",
        ],
      },
      {
        heading: "Ce que le service ne fait pas",
        bullets: [
          { text: "Aucune publicité, aucun contenu sponsorisé, aucun identifiant publicitaire." },
          { text: "Aucune mesure d'audience, aucun outil de suivi d'aucune sorte, aucun profilage." },
          { text: "Rien de ce qui vous concerne n'est vendu, loué ni transmis à des fins de prospection." },
          {
            text: "La lecture d'une recette imprimée se fait entièrement sur votre téléphone : ni la photo ni le texte qui en est tiré ne quittent l'appareil.",
          },
          {
            text: "L'export d'une recette en PDF est réalisé par un composant interne à l'infrastructure du service, qui n'a aucun accès à internet.",
          },
        ],
      },
      {
        heading: "Qui d'autre y a accès",
        bullets: [
          {
            term: "Les autres personnes sur Cook'n'Co",
            text: "ce que la visibilité de votre compte autorise, et rien de plus.",
          },
          {
            term: "Google",
            text: "achemine les notifications et les e-mails, et vous identifie si vous avez choisi la connexion Google.",
          },
          {
            term: "L'hébergeur",
            text: "les serveurs et la base de données tournent sur des machines louées chez un hébergeur situé dans l'Union européenne.",
          },
          {
            term: "Les clients IA que vous avez connectés",
            text: "celui que vous reliez par les réglages MCP lit et écrit vos recettes en votre nom. Vous pouvez retirer cet accès à tout moment, depuis ces mêmes réglages.",
          },
          {
            term: "Une autorité judiciaire ou administrative",
            text: "lorsque la loi l'exige réellement.",
          },
        ],
        after: [
          "Le service ne transfère par lui-même aucune donnée hors de l'Union européenne. Google opère dans le monde entier et ses propres conditions régissent ce qu'il fait de ce qu'il achemine.",
        ],
      },
      {
        heading: "Où tout cela se trouve, et pour combien de temps",
        body: [
          "Sur des serveurs situés dans l'Union européenne. La base de données est copiée chaque nuit ; ces copies sont conservées quatorze jours puis détruites. Elles existent pour remettre le service en route après un incident, et ne sont lues pour rien d'autre.",
          "Ce que contient votre compte est conservé tant que le compte existe. Quand vous le supprimez, tout part immédiatement, et les copies de nuit cessent de le contenir sous quatorze jours. Le détail est sur la page de suppression.",
        ],
      },
      {
        heading: "Cookies et données stockées sur votre appareil",
        body: [
          "Un cookie identifie votre session sur le site, et l'application garde le jeton équivalent sur le téléphone. À côté d'eux vivent de petites préférences — la langue dans laquelle vous lisez, la dernière recherche lancée — pour que la page s'ouvre comme vous l'avez laissée.",
          "Il n'y a ni cookie publicitaire ni cookie de mesure d'audience : c'est pour cela que le site ne vous demande d'accepter quoi que ce soit.",
          "Le serveur web conserve quelque temps des journaux d'accès ordinaires, comme tout serveur web. Ils enregistrent des requêtes, pas des personnes, et ne sont lus que lorsque quelque chose casse.",
        ],
      },
      {
        heading: "Vos droits",
        bullets: [
          { term: "Savoir ce qui est conservé", text: "demandez, et vous l'obtenez." },
          { term: "Le corriger", text: "votre profil et vos réglages sont modifiables à tout moment." },
          {
            term: "L'emporter",
            text: "chaque recette s'exporte en PDF, depuis l'application comme depuis le site.",
          },
          {
            term: "L'effacer",
            text: "supprimez votre compte vous-même, en deux gestes, depuis l'application ou le site.",
          },
          {
            term: "Vous opposer, ou demander la limitation du traitement",
            text: "écrivez, en disant ce à quoi vous vous opposez.",
          },
        ],
        after: [
          `Tout cela passe par ${CONTACT_EMAIL}, et reçoit une réponse sous un mois.`,
          "Le service traite ces données pour faire fonctionner le compte que vous avez demandé ; sur votre consentement pour les notifications et les e-mails d'information ; et sur un intérêt légitime à garder le lieu modéré et sûr. Si vous estimez que ce n'est pas respecté, vous pouvez saisir la CNIL.",
        ],
      },
      {
        heading: "Les enfants",
        body: [
          "Cook'n'Co ne s'adresse pas aux enfants. Un compte appartenant à une personne de moins de 15 ans est supprimé dès qu'il est signalé, sans qu'il soit demandé autre chose que le pseudonyme.",
        ],
      },
      {
        heading: "Modifications de cette politique",
        body: [
          "La date en haut de page dit quand ce texte a changé pour la dernière fois. Tout ce qui modifie ce qui est collecté, ou qui y a accès, est annoncé dans l'application et par e-mail avant d'entrer en vigueur — et non glissé dans cette page.",
        ],
      },
    ],
  },

  // ── Suppression du compte ──────────────────────────────────────────────────
  deletion: {
    title: "Supprimer votre compte",
    updated: "Dernière mise à jour le 18 septembre 2026",
    intro: [
      "Cette page concerne le compte Cook'n'Co — l'application (com.xavierclavel.cooknco) et cooknco.eu en partagent un seul, donc le supprimer supprime les deux.",
      "Vous le faites vous-même, cela prend effet au moment où vous confirmez, et personne n'a à l'approuver.",
    ],
    sections: [
      {
        heading: "Dans l'application",
        bullets: [
          { text: "Ouvrez votre profil, puis le bouton des réglages." },
          { text: "Descendez jusqu'à COMPTE et touchez « Supprimer mon compte »." },
          { text: "Confirmez. Vous êtes déconnecté et le compte n'existe plus." },
        ],
      },
      {
        heading: "Sur le site",
        bullets: [
          { text: "Connectez-vous sur cooknco.eu." },
          { text: "Ouvrez les réglages depuis le menu du profil." },
          { text: "Appuyez sur « Supprimer mon compte » en bas de la page, puis confirmez." },
        ],
      },
      {
        heading: "Si vous ne pouvez plus vous connecter",
        body: [
          `Écrivez à ${CONTACT_EMAIL} depuis l'adresse du compte, en demandant sa suppression. Elle est faite sous 30 jours et vous recevez une réponse le confirmant. Écrire depuis cette adresse est ce qui montre que le compte est le vôtre.`,
        ],
      },
      {
        heading: "Ce qui est supprimé, immédiatement",
        bullets: [
          { text: "Le compte lui-même : pseudonyme, adresse e-mail, mot de passe, photo, biographie." },
          { text: "Vos recettes, leurs photos, leurs étapes et leurs ingrédients." },
          { text: "Vos notes privées sur les recettes." },
          { text: "Ce que vous avez aimé, les personnes que vous suiviez et celles qui vous suivaient." },
          { text: "Votre appartenance à tous les livres de recettes." },
          { text: "Vos appareils et les jetons par lesquels ils recevaient les notifications." },
          { text: "Les accès que vous avez accordés à un client IA, par MCP." },
          { text: "Vos réglages, vos restrictions alimentaires et toutes vos sessions ouvertes." },
        ],
      },
      {
        heading: "Ce qui survit au compte, et pourquoi",
        bullets: [
          {
            term: "Les signalements de modération",
            text: "un signalement que vous avez déposé, ou déposé au sujet de ce que vous avez publié, est conservé sans votre compte attaché. La modération doit garder son historique, et supprimer un compte ne peut pas être une façon de l'effacer.",
          },
          {
            term: "Les notifications déjà reçues par d'autres",
            text: "elles continuent de dire ce qui s'est passé, sans que votre compte en soit encore nommé comme l'auteur.",
          },
          {
            term: "Un livre de recettes partagé",
            text: "reste à ses autres membres, parce qu'il est aussi le leur. Vos recettes partent avec vous, y compris celles que vous y aviez mises.",
          },
          {
            term: "Les sauvegardes de nuit",
            text: "les copies faites avant la suppression contiennent encore le compte pendant quatorze jours au plus, après quoi elles sont détruites à leur tour. Elles ne sont lues que pour remettre le service en route après un incident.",
          },
        ],
      },
      {
        heading: "Avant de partir",
        body: [
          "C'est irréversible : il n'y a pas de délai pendant lequel le compte pourrait être rétabli, et le pseudonyme est libre pour quelqu'un d'autre immédiatement.",
          "Chaque recette s'exporte en PDF, une par une, depuis sa propre page dans l'application et sur le site. C'est la copie à faire d'abord.",
          "Cook'n'Co ne facture rien : il n'y a aucun abonnement à résilier à côté.",
        ],
      },
      {
        heading: "Une question sur tout cela",
        body: [
          `${CONTACT_EMAIL}. La politique de confidentialité dit ce qui est conservé sur vous, et pourquoi.`,
        ],
      },
    ],
  },
}

export default documents
