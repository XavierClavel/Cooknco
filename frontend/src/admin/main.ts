import {createApp} from 'vue'
import {createI18n} from 'vue-i18n'
import en from '@/locales/en'
import fr from '@/locales/fr'
import {getBrowserLocale} from '@/scripts/localization'
import App from './App.vue'
import router from './router'
import './styles/base.css'

// Copy shares the public app's locale files; the design does not.
const i18n = createI18n({
  legacy: false,
  locale: getBrowserLocale(),
  fallbackLocale: 'en',
  messages: {en, fr},
})

createApp(App).use(i18n).use(router).mount('#admin')
