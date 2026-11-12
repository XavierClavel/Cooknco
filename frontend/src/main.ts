/**
 * main.ts
 *
 * Bootstraps Vuetify and other plugins then mounts the App`
 */

// Plugins
import { registerPlugins } from '@/plugins'

// Components
import App from './App.vue'

// Composables
import { createApp } from 'vue'
import { createPinia } from 'pinia';
import router from "@/router";
import VueCookies from 'vue-cookies'
import i18n from "@/plugins/i18n";
import './styles/global.scss'


const pinia = createPinia();

const app = createApp(App)
app.use(pinia);
app.use(i18n)
app.use(router);
app.use(VueCookies)

app.config.globalProperties.$cookies.config('1y')

registerPlugins(app)

/*
  A picture that fails to load is hidden rather than left as the browser's broken-image icon
  and alt text: every picture in the sticker theme sits on a striped placeholder, which is the
  better thing to show. Image errors do not bubble, hence the capture phase.
*/
window.addEventListener('error', (event) => {
  if (event.target instanceof HTMLImageElement) event.target.style.visibility = 'hidden'
}, true)
app.mount('#app')
