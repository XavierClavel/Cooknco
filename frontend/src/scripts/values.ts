import {
  ICON_ALPHABETICAL,
  ICON_DATE,
  ICON_LIKES,
  ICON_RANDOM, ICON_VISIBILITY_PRIVATE, ICON_VISIBILITY_PROTECTED, ICON_VISIBILITY_PUBLIC,
} from "@/scripts/icons";

export {
  dishOptions,
  sourceOptions,
  sortOptions,
  visibilityOptions,
  ingredientTypes,
}


const dishOptions = [
  {
    value: "ENTREE",
    label: "entree",
  },
  {
    value: "MAIN_DISH",
    label: "plat",
  },
  {
    value: "DESERT",
    label: "desert",
  },
  {
    value: "SALTY_SNACK",
    label: "salty_snacks",
  },
  {
    value: "SUGARY_SNACK",
    label: "sugary_snacks",
  },
  {
    value: "DRINK",
    label: "drinks",
  },
  {
    value: "OTHER",
    label: "others",
  },
]


const sourceOptions = ref([
  {label: 'my_recipes', value: "user"},
  {label: 'likes', value: "likedBy"},
  {label: 'cookbooks', value: "userCookbooks"},
  {label: 'follows', value: "followedBy"},
])

const sortOptions = ref([
  { label: 'alphabetical', value: "NAME", icon:ICON_ALPHABETICAL, ordered: true },
  { label: 'random', value: "RANDOM", icon:ICON_RANDOM, ordered: false },
  { label: 'likes', value: "LIKES", icon:ICON_LIKES, ordered: true },
  { label: 'date', value: "DATE", icon:ICON_DATE, ordered: true },
]);

const visibilityOptions = ref([
  {
    label: 'Private',
    value: 'PRIVATE',
    icon: ICON_VISIBILITY_PRIVATE,
  },
  {
    label: 'Protected',
    value: 'PROTECTED',
    icon: ICON_VISIBILITY_PROTECTED,
  },
  {
    label: 'Public',
    value: 'PUBLIC',
    icon: ICON_VISIBILITY_PUBLIC,
  },
])

const ingredientTypes = ref([
  {
    value: "VEGETABLE",
    image: "vegetable.svg",
  },
  {
    value: "FRUIT",
    image: "fruit.svg",
  },
  {
    value: "GRAIN",
    image: "grain.svg",
  },
  {
    value: "NUT",
    image: "nut.svg",
  },
  {
    value: "DAIRY",
    image: "dairy.svg",
  },
  {
    value: "FISH",
    image: "fish.svg",
  },
  {
    value: "MEAT",
    image: "meat.svg",
  },
  {
    value: "CONDIMENT",
    image: "condiment.svg",
  },
  {
    value: "OIL",
    image: "oil.svg",
  },
  {
    value: "BAKERY",
    image: "bakery.svg",
  },
  {
    value: "BEVERAGE_INGREDIENT",
    image: "beverage_ingredient.svg",
  },
  {
    value: "ALCOHOL",
    image: "alcohol.svg",
  },
  {
    value: "MISCELLANEOUS",
    image: "miscellaneous.svg",
  },
])
