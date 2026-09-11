import i18n from '@/plugins/i18n'
import {getCookie, setCookie} from "@/scripts/cookies";
import {getSettings} from "@/scripts/settings";

export {
  overrideLocaleFromCookie,
  applyAccountLocale,
  forceLocale,
  getLocale,
  getBrowserLocale,
  toApiLocale,
  fromApiLocale,
}


function overrideLocaleFromCookie() {
  const cookieLocale = getCookie('locale')
  console.log(cookieLocale)
  if (cookieLocale) {
    setLocale(cookieLocale)
  }
  console.log("Current locale: ", getLocale())
}

/**
 * Switches to the language saved on the account, when it has one.
 *
 * This is what makes the choice follow the user rather than the browser: signing in
 * somewhere new reads the account's language back instead of leaving the new machine on
 * whatever its own settings say. The cookie is written along the way, so the next load of
 * this browser applies it without waiting for the request.
 *
 * Silent on failure - being signed in is enough to render the app, and the browser's own
 * language is a perfectly good answer if the account's cannot be read.
 */
async function applyAccountLocale() {
  try {
    const response = await getSettings()
    const locale = fromApiLocale(response.data?.locale)
    if (locale) forceLocale(locale)
  } catch {
    // keep whatever the cookie or the browser already gave us
  }
}

/** `fr` as the UI spells it, `FR` as the API's `Locale` does. */
function toApiLocale(locale: string): string {
  return locale.toUpperCase()
}

/** Null-tolerant: the account may simply have no language saved yet. */
function fromApiLocale(locale: string | null | undefined): string | null {
  return locale ? locale.toLowerCase() : null
}

function forceLocale(locale) {
  if (!Object.keys(i18n.global.messages).includes(locale)) {
    return
  }
  if (locale == getLocale()) {
    return
  }
  setCookie('locale', locale)
  setLocale(locale)
}

function setLocale(locale) {
  i18n.global.locale = locale
}

function getLocale(): string {
  let locale = i18n.global.locale
  if (!Object.keys(i18n.global.messages).includes(locale)) {
    locale = i18n.global.fallbackLocale
  }
  return locale
}

function getBrowserLocale() {
  const locale = navigator.language || navigator.languages[0] || 'en';
  return locale.split('-')[0]; //Handles regional variants ex en-US, fr-FR
}
