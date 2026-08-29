import axios from "axios";
import apiClient from '@/scripts/axios';
import {useAuthStore, declareLogin} from "@/stores/auth";

export {
  ICON_RECIPE,
  ICON_COOKBOOK,
  ICON_INGREDIENT,
  ICON_HOME,
  ICON_ADMIN,

  ICON_SAVE,
  ICON_DELETE,

  ICON_RECIPE_YIELD,

  ICON_COOKBOOK_RECIPES,
  ICON_COOKBOOK_USERS,

  ICON_USER_RECIPES,
  ICON_USER_LIKES,
  ICON_USER_FOLLOWS,
  ICON_USER_FOLLOWERS,

  ICON_RANDOM,
  ICON_DATE,
  ICON_LIKES,
  ICON_ALPHABETICAL,

  ICON_NOTIFICATION,
  ICON_NOTIFICATION_NEW,

  ICON_REMOVE,
  ICON_ACCEPT,
  ICON_DENY,

  ICON_LOCALIZATION,

  ICON_VISIBILITY_PRIVATE,
  ICON_VISIBILITY_PROTECTED,
  ICON_VISIBILITY_PUBLIC,

  ICON_WEIGHT,
  ICON_AMOUNT,
  ICON_VOLUME,
  ICON_NONE,

  getIngredientIcon,
}

const ICON_RECIPE = "mdi-notebook"
const ICON_COOKBOOK = "mdi-bookshelf"
const ICON_INGREDIENT = "mdi-food-apple"
const ICON_HOME = "mdi-home"
const ICON_ADMIN = "mdi-security"

const ICON_SAVE = "mdi-content-save"
const ICON_DELETE = "mdi-delete"

const ICON_RECIPE_YIELD = "mdi-silverware-fork-knife"

const ICON_COOKBOOK_RECIPES = "mdi-silverware-fork-knife"
const ICON_COOKBOOK_USERS = "mdi-account-multiple"

const ICON_USER_LIKES = "mdi-heart"
const ICON_USER_RECIPES = "mdi-notebook"
const ICON_USER_FOLLOWS = "mdi-account-heart"
const ICON_USER_FOLLOWERS = "mdi-account-multiple"

const ICON_RANDOM = "mdi-shuffle-variant"
const ICON_DATE = "mdi-clock"
const ICON_LIKES = "mdi-heart"
const ICON_ALPHABETICAL = "mdi-format-letter-case"

const ICON_INGREDIENT_CHEESE = "mdi-cheese"
const ICON_INGREDIENT_MEAT = "mdi-food-drumstick"
const ICON_INGREDIENT_VEGETABLE = "mdi-carrot"
const ICON_INGREDIENT_FRUIT = "mdi-food-apple"
const ICON_INGREDIENT_FISH = "mdi-fish"
const ICON_INGREDIENT_SPICE = "mdi-shaker-outline"

const ICON_NOTIFICATION = "mdi-bell"
const ICON_NOTIFICATION_NEW = "mdi-bell-badge"

const ICON_REMOVE = "mdi-delete"
const ICON_ACCEPT = "mdi-check"
const ICON_DENY = "mdi-close"

const ICON_LOCALIZATION = "mdi-translate"

const ICON_INTEGRATION = "mdi-xml"

const ICON_VISIBILITY_PRIVATE = "mdi-lock"
const ICON_VISIBILITY_PROTECTED = "mdi-account-group"
const ICON_VISIBILITY_PUBLIC = "mdi-eye"

const ICON_WEIGHT = "mdi-weight"
const ICON_AMOUNT = "mdi-tally-mark-5"
const ICON_VOLUME = "mdi-cup"
const ICON_NONE = "mdi-minus"

const getIngredientIcon = (ingredientType) => {
  switch (ingredientType) {
    case "VEGETABLE":
      return ICON_INGREDIENT_VEGETABLE
    case "FRUIT":
      return ICON_INGREDIENT_FRUIT
    case "MEAT":
      return ICON_INGREDIENT_MEAT
    case "FISH":
      return ICON_INGREDIENT_FISH
    case "CHEESE":
      return ICON_INGREDIENT_CHEESE
  }
}

