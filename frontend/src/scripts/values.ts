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
    image: "vegetable.png",
  },
  {
    value: "FRUIT",
    image: "vegetable.png",
  },
  {
    value: "GRAIN",
    image: "vegetable.png",
  },
  {
    value: "NUT",
    image: "vegetable.png",
  },
  {
    value: "DAIRY",
    image: "vegetable.png",
  },
  {
    value: "FISH",
    image: "vegetable.png",
  },
  {
    value: "MEAT",
    image: "vegetable.png",
  },
  {
    value: "CONDIMENT",
    image: "vegetable.png",
  },
  {
    value: "OIL",
    image: "vegetable.png",
  },
  {
    value: "BAKERY",
    image: "vegetable.png",
  },
  {
    value: "BEVERAGE_INGREDIENT",
    image: "vegetable.png",
  },
  {
    value: "ALCOHOL",
    image: "vegetable.png",
  },
  {
    value: "MISCELLANEOUS",
    image: "vegetable.png",
  },
])
