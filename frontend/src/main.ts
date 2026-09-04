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
app.mount('#app')
