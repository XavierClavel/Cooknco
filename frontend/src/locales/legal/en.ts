import { CONTACT_EMAIL, type LegalDocuments } from '@/locales/legal/shared'

/**
 * English privacy policy and deletion notice. Mirrors `fr.ts` section for section — the two
 * say the same things about the service and are not translations of each other's sentences.
 *
 * Every claim here is one the code makes true. When what the service stores, sends or keeps
 * changes, both files change in the same commit: a policy accurate in one language is a
 * policy wrong in the other, and nobody notices the half that is wrong.
 */
const documents: LegalDocuments = {

  // ── Privacy policy ─────────────────────────────────────────────────────────
  privacy: {
    title: "Privacy policy",
    updated: "Last updated 18 September 2026",
    intro: [
      "Cook'n'Co is a recipe book: you write down the dishes you cook, you find them again, and you share them with the people you choose. This page says what the service holds about you, why it holds it, who else ever sees it, and how to get rid of all of it.",
      "The mobile app and cooknco.eu are one service and one account, so this policy covers both.",
    ],
    sections: [
      {
        heading: "Who is responsible",
        body: [
          `Cook'n'Co is run by Xavier Clavel, as an individual — there is no company behind it. For anything on this page, including a request to see or to delete what is held about you, the address is ${CONTACT_EMAIL}.`,
          "The service is operated from France, and this policy follows the GDPR.",
        ],
      },
      {
        heading: "What your account holds",
        bullets: [
          {
            term: "Your email address",
            text: "stored encrypted. What signing in matches against is a separate one-way fingerprint of it, so the address itself is never compared in the clear.",
          },
          {
            term: "Your password",
            text: "stored only as a bcrypt hash, which cannot be turned back into a password. Nobody, the developer included, can read it.",
          },
          {
            term: "Google sign-in",
            text: "if you signed up with Google, the service receives an account identifier and your email address from Google, and nothing else. Your Google password never reaches it.",
          },
          {
            term: "Your profile",
            text: "username, bio and profile picture, the date you joined, and the date you were last active.",
          },
          {
            term: "Your settings",
            text: "the language you are written to in, metric or imperial units, your dietary restrictions, whether your account is public, and whether follow requests are accepted automatically.",
          },
        ],
      },
      {
        heading: "What you write",
        body: [
          "Your recipes and their photos, their steps, times and ingredients; your cookbooks; what you liked and who you follow; and the private notes you keep on a recipe.",
          "Your account is public or on request, and that setting is what decides who reads your recipes. Your private notes are the exception with no setting at all: they are shown to you and to nobody else, ever.",
        ],
      },
      {
        heading: "Your devices, and push notifications",
        body: [
          "A phone with push notifications switched on is registered with the notification token Google issues for that install, the platform, the app version, the language the phone runs in, and when it was first and last seen. That row is what a notification is delivered to.",
          "It goes when you sign out, when you switch push off, and when Google reports the token as dead. App versions are also counted together — never per person — to know how many installs a minimum supported version would stop.",
        ],
      },
      {
        heading: "The emails the service sends",
        body: [
          "Three kinds: confirming your address when you sign up, resetting your password when you ask, and — only if you switch them on, because they are off by default — telling you what the people you follow have cooked.",
          "They go out through Gmail's mail servers, so Google carries them the way any mail provider carries mail.",
        ],
      },
      {
        heading: "What the service does not do",
        bullets: [
          { text: "No advertising, no sponsored content, no advertising identifier." },
          { text: "No analytics, no measurement or tracking toolkit of any kind, no profiling." },
          { text: "Nothing about you is sold, rented, or handed to anyone for marketing." },
          {
            text: "Scanning a printed recipe happens entirely on your phone. Neither the photograph nor the text read off it leaves the device.",
          },
          {
            text: "Exporting a recipe to PDF is done by a component inside the service's own infrastructure, which has no access to the internet at all.",
          },
        ],
      },
      {
        heading: "Who else can see it",
        bullets: [
          {
            term: "Other people using Cook'n'Co",
            text: "what your account's visibility allows, and nothing more.",
          },
          {
            term: "Google",
            text: "delivers the push notifications, carries the emails, and identifies you if you chose to sign in with Google.",
          },
          {
            term: "The hosting provider",
            text: "the servers and the database run on machines rented from a provider inside the European Union.",
          },
          {
            term: "The AI clients you connected yourself",
            text: "one linked through the MCP settings reads and writes your recipes on your behalf. You can withdraw any of them at any moment, from those same settings.",
          },
          {
            term: "A court or an authority",
            text: "where the law actually requires it.",
          },
        ],
        after: [
          "The service itself transfers nothing outside the European Union. Google operates worldwide, and its own terms govern what it does with what it carries.",
        ],
      },
      {
        heading: "Where it lives, and for how long",
        body: [
          "On servers in the European Union. The database is copied every night; those copies are kept for fourteen days and then destroyed. They exist to bring the service back after an incident, and are read for nothing else.",
          "What your account holds is kept for as long as the account exists. When you delete it, it goes immediately, and the nightly copies stop carrying it within fourteen days. The deletion page has the details.",
        ],
      },
      {
        heading: "Cookies, and what is stored on your device",
        body: [
          "A cookie identifies your signed-in session on the website, and the app keeps the equivalent token on the phone. Beside them sit small preferences — the language you are reading in, the last search you ran — so that the page opens the way you left it.",
          "There is no advertising cookie and no measurement cookie, which is why the site asks you to accept nothing.",
          "The web server keeps ordinary access logs for a short time, as every web server does. They record requests, not people, and are read when something breaks.",
        ],
      },
      {
        heading: "Your rights",
        bullets: [
          { term: "See what is held", text: "ask, and you get it." },
          { term: "Correct it", text: "your profile and your settings are yours to edit at any time." },
          {
            term: "Take it with you",
            text: "every recipe exports to a PDF, from the app and from the website.",
          },
          {
            term: "Erase it",
            text: "delete your account yourself, in two taps, from the app or the website.",
          },
          {
            term: "Object, or ask for processing to be restricted",
            text: "write, and say what you object to.",
          },
        ],
        after: [
          `All of it goes to ${CONTACT_EMAIL}, and is answered within a month.`,
          "The service processes what it does in order to run the account you asked for; on your consent for push and email notifications; and on a legitimate interest in keeping the place moderated and secure. If you think that is not being respected, you can complain to the CNIL, the French data protection authority.",
        ],
      },
      {
        heading: "Children",
        body: [
          "Cook'n'Co is not aimed at children. An account belonging to someone under 15 is deleted as soon as it is reported, and nothing beyond the username is asked for.",
        ],
      },
      {
        heading: "Changes to this policy",
        body: [
          "The date at the top says when this text last changed. Anything that changes what is collected, or who sees it, is announced in the app and by email before it takes effect — not slipped into this page.",
        ],
      },
    ],
  },

  // ── Account deletion ───────────────────────────────────────────────────────
  deletion: {
    title: "Deleting your account",
    updated: "Last updated 18 September 2026",
    intro: [
      "This page is about the Cook'n'Co account — the app (com.xavierclavel.cooknco) and cooknco.eu share one, so deleting it deletes both.",
      "You do it yourself, it takes effect the moment you confirm, and nobody has to approve it.",
    ],
    sections: [
      {
        heading: "In the app",
        bullets: [
          { text: "Open your profile, then the settings button." },
          { text: "Scroll to ACCOUNT and tap “Delete my account”." },
          { text: "Confirm. You are signed out, and the account is gone." },
        ],
      },
      {
        heading: "On the website",
        bullets: [
          { text: "Sign in at cooknco.eu." },
          { text: "Open Settings from the profile menu." },
          { text: "Press “Delete my account” at the bottom of the page, and confirm." },
        ],
      },
      {
        heading: "If you cannot sign in any more",
        body: [
          `Write to ${CONTACT_EMAIL} from the address the account uses, asking for it to be deleted. It is done within 30 days, and you get a reply saying so. Sending it from that address is what shows the account is yours.`,
        ],
      },
      {
        heading: "What is deleted, straight away",
        bullets: [
          { text: "The account itself: username, email address, password, profile picture, bio." },
          { text: "Your recipes, their photos, their steps and their ingredients." },
          { text: "Your private notes on recipes." },
          { text: "What you liked, who you followed, and who followed you." },
          { text: "Your membership of every cookbook." },
          { text: "Your devices, and the push tokens they were reachable at." },
          { text: "The access you granted to any AI client, through MCP." },
          { text: "Your settings, your dietary restrictions, and every open session." },
        ],
      },
      {
        heading: "What outlives the account, and why",
        bullets: [
          {
            term: "Moderation reports",
            text: "a report you filed, or one filed about something you posted, is kept without your account attached to it. Moderation has to keep its history, and deleting an account cannot be a way of erasing it.",
          },
          {
            term: "Notifications other people already received",
            text: "they keep saying what happened, with your account no longer named as what caused it.",
          },
          {
            term: "A cookbook shared with other people",
            text: "stays with its remaining members, because it is theirs too. Your recipes leave with you, including the ones you had put in it.",
          },
          {
            term: "The nightly backups",
            text: "copies made before the deletion still hold the account for up to fourteen days, after which they are destroyed in turn. They are only ever read to bring the service back after an incident.",
          },
        ],
      },
      {
        heading: "Before you go",
        body: [
          "It cannot be undone. There is no waiting period during which the account could be brought back, and the username is free for somebody else immediately.",
          "Every recipe exports to a PDF, one at a time, from the recipe's own page in the app and on the website. That is the copy to make first.",
          "Cook'n'Co bills nothing, so there is no subscription to cancel alongside.",
        ],
      },
      {
        heading: "A question about any of this",
        body: [
          `${CONTACT_EMAIL}. The privacy policy says what is held about you, and why.`,
        ],
      },
    ],
  },
}

export default documents
