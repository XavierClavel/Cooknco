/**
 * plugins/vuetify.ts
 *
 * Framework documentation: https://vuetifyjs.com`
 */

// Styles
import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/styles'

// Composables
import {createVuetify, ThemeDefinition} from 'vuetify'
import { VNumberInput } from 'vuetify/labs/VNumberInput'

const customTheme: ThemeDefinition = {
  dark: false,
  colors: {
    // The sticker theme: the page is green and every card on it is pale paper
    background: '#629677',
    surface: '#eef3ee',
    'on-background': '#0d1821',
    primary: '#ff6f59',
    secondary: '#f4b634',
    error: '#000000',
    info: '#2196F3',
    success: '#4CAF50',
    warning: '#FB8C00',
    'on-surface': "#000000", // Default text color
    'menu': "#eef3ee",
    'paper': "#eef3ee",
    'ink': "#0d1821",
    'mustard': "#f4b634",
    'plum': "#4f4193",
    'cream': "#fff0c2",
    'border': "#0d1821",
  },
  variables: {
    fontFamily: 'Geologica, sans-serif',
  }
}

// https://vuetifyjs.com/en/introduction/why-vuetify/#feature-guides
export default createVuetify({
  theme: {
    defaultTheme: 'customTheme',
    themes: {
      customTheme,
    }
  },
  defaults: {
    VCardTitle: {
      class: "text-black text-pre-wrap font-weight-bold text-h5 text-sm-h2",
    },
    VCard: {
      rounded: 'lg',
      color: 'surface',
    },
    VBtn: {
      rounded: 'pill',
    },
    VNavigationDrawer: {
      rounded: 'lg',
    },
    VNumberInput: {
      variant:"solo",
      flat: true,
      hideDetails: 'auto',
      bgColor: "white",
      color: "primary",
      VBtn: {
        rounded: 0,
      },
    },
    VTextField: {
      variant:"solo",
      flat: true,
      hideDetails: 'auto',
      bgColor: "white",
      color: "primary",
      class: "my-2"
    },
    VTextarea: {
      variant:"solo",
      flat: true,
      hideDetails: 'auto',
      bgColor: "white",
      color: "primary",
    },
    VSelect: {
      variant:"solo",
      flat: true,
      hideDetails: true,
      bgColor: "white",
      color: "primary",
      menuProps: {
        contentClass: 'custom-select-menu'
      },
      class: "custom-list",
    },
    VCombobox: {
      variant:"solo",
      flat: true,
      hideDetails: true,
      bgColor: "white",
      color: "primary",
    },
    VAutocomplete: {
      variant:"solo",
      flat: true,
      hideDetails: true,
      bgColor: "white",
      color: "primary",
    },
    VListItem: {
      slim: true,
    },
    VList: {
      slim: true,
    },
    VChip: {
      variant: "elevated",
      color: "primary"
    },
    VSnackbar: {
      color: "ink",
    }
  },
  components: {
    VNumberInput,
  },
  styles: {
    configFile: 'src/styles/settings.scss'
  }
})
