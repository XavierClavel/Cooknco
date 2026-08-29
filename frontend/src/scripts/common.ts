import axios from "axios";
import apiClient, {imageClient} from '@/scripts/axios';
import {useAuthStore, declareLogin} from "@/stores/auth";
import {deleteCookie, getCookie} from "@/scripts/cookies";
import {getLocale, t} from "@/scripts/localization";
import {ingredientTypes} from "@/scripts/values";

export {
  login,
  loginOauthGoogle,
  logout,
  signup,
  verifyEmail,
  requestPasswordReset,
  resetPassword,
  toResetPasswordEmailSent,

  toCreateRecipe,
  toCreateCookbookAddRecipe,
  toEditRecipe,
  toViewRecipe,
  toListRecipe,

  toViewUser,
  toEditUser,

  toViewIngredient,
  toListIngredient,

  toCreateCookbook,
  toEditCookbook,
  toListCookbooks,
  toMyCookbooks,
  toViewCookbook,

  toResetPassword,
  toResetPasswordSuccess,
  toUpdatePassword,
  toUpdatePasswordSuccess,

  toHome,
  toUsers,
  toLogin,
  toSignup,
  toMaintenance,

  toMyProfile,
  toSettings,

  uploadImage,
  doDeleteImage,

  noLoginRedirect,
  noLoginRedirectStartsWith,
  allowNoLoginStartsWith,
  adminOnly,
  isPublicPath,
  isChromelessPath,

  unitToReadable,

  getUserIconUrl,
  getCookbookIconUrl,
  getRecipeImageUrl,
  getRecipeThumbnailUrl,
  getIngredientImageUrl,
  getImageUrl,
  getDefaultImageUrl,

  getHealth,

  defaultImageRecipe,
}

const toCreateRecipe = () => navigateTo(`/recipe/edit`)
const toEditRecipe = (id) => navigateTo(`/recipe/edit?id=${id}`)
const toViewRecipe = (id) => navigateTo(`/recipe/view?id=${id}`)
const toListRecipe = (search) => navigateTo(`/recipe/list${search}`)

const toViewUser = (id) => navigateTo(`/user/view/?user=${id}`)
const toMyProfile = () => {
  const authStore = useAuthStore();
  navigateTo(`/user/view?user=${authStore.id}`)
}
const toEditUser = (id) => navigateTo(`/user/edit?user=${id}`)
const toSettings = () => navigateTo(`/user/settings`)

const toListIngredient = () => navigateTo(`/ingredient/list`)
const toViewIngredient = (id) => navigateTo(`/ingredient/view?ingredient=${id}`)

const toCreateCookbook = () => navigateTo(`/cookbook/edit`)
const toEditCookbook = (id) => navigateTo(`/cookbook/edit?cookbook=${id}`)
const toCreateCookbookAddRecipe = (id) => navigateTo(`/cookbook/edit?addRecipe=${id}`)
const toViewCookbook = (id) => navigateTo(`/cookbook/view?cookbook=${id}`)
const toListCookbooks = (id) => navigateTo(`/cookbook/list?user=${id}`)

const toMyCookbooks = () => {
  const authStore = useAuthStore();
  toListCookbooks(authStore.id)
}

const toHome = () => navigateTo('/home')
const toUsers = () => navigateTo('/user/list')
const toLogin = () => navigateTo('/login')
const toSignup = () => navigateTo('/signup')
const toMaintenance = () => navigateTo('/maintenance')
const toResetPassword = () => navigateTo('/password/reset')
const toUpdatePassword = () => navigateTo('/password/update')
const toUpdatePasswordSuccess = () => navigateTo('/password/update/success')
const toResetPasswordEmailSent = () => navigateTo('/password/reset/email')
const toResetPasswordSuccess = () => navigateTo('/password/reset/success')

// NB: `navigateTo` used to be defined here as a nextTick + router.push wrapper.
// Under Nuxt that local definition would shadow the framework's own
// auto-imported navigateTo, so every helper above would keep pushing onto a
// router that no longer exists. It is deliberately gone.

// These used to be matched against vue-router route *names*, which under
// unplugin-vue-router happened to be identical to the paths. Nuxt names routes
// differently ('password-reset-email', not '/password/reset/email'), so they
// are matched against `route.path` now — same strings, stable meaning.

/** Routes reachable logged out, and which render without the app chrome. */
const noLoginRedirect = [
  '/logout',
  '/signup',
  '/user/verify',
  '/maintenance',
  '/verification-email-sent',
  '/password/update/success',
]
const noLoginRedirectStartsWith = [
  '/password/reset',
  '/login',
]

/** Routes reachable logged out that keep the normal chrome (shareable pages). */
const allowNoLoginStartsWith = [
  '/recipe/view',
  '/user/view',
]

const adminOnly = [
  '/user/list',
]

/** True for a path that does not require an authenticated session. */
const isPublicPath = (path: string) =>
  noLoginRedirect.includes(path) ||
  noLoginRedirectStartsWith.some((it) => path.startsWith(it)) ||
  allowNoLoginStartsWith.some((it) => path.startsWith(it))

/** True for a path that renders bare, without drawer and app bar. */
const isChromelessPath = (path: string) =>
  noLoginRedirect.includes(path) ||
  noLoginRedirectStartsWith.some((it) => path.startsWith(it))

const defaultImageUser = '/default_user.jpg'
const defaultImageRecipe = '/default_recipe.png'
const defaultImageCookbook = '/default_cookbook.png'

// Resolved per call, not at module scope: import.meta.env.VITE_* does not exist
// under Nitro, and runtimeConfig lets the same build serve any environment.
const imgBase = () => useRuntimeConfig().public.imgUrl

const getUserIconUrl = (id, version) => id && version ? `${imgBase()}/users/${id}-v${version}.webp` : defaultImageUser
const getCookbookIconUrl = (id, version) => id && version ? `${imgBase()}/cookbooks/${id}-v${version}.webp` : defaultImageCookbook
const getRecipeImageUrl = (id, version) => id && version ? `${imgBase()}/recipes/${id}-v${version}.webp` : defaultImageRecipe
const getRecipeThumbnailUrl = (id, version) => id && version ? `${imgBase()}/recipes-thumbnails/${id}-v${version}.webp` : defaultImageRecipe
const getIngredientImageUrl = (type) => {
  try {
    const t = ingredientTypes.value.find((item) => item.value == type)
    return `/ingredients/${t.image}`
  } catch (e) {
    return ''
  }

}

const getImageUrl = (path, id, version) => {
  switch (path) {
    case "users":
      return getUserIconUrl(id, version)

    case "recipes":
      return getRecipeImageUrl(id, version)

    case "cookbooks":
      return getCookbookIconUrl(id, version)
  }
}

const getDefaultImageUrl = (path) => {
  switch (path) {
    case "users":
      return getUserIconUrl(0, 0)

    case "recipes":
      return getRecipeImageUrl(0, 0)

    case "cookbooks":
      return getCookbookIconUrl(0, 0)
  }
}

async function login(user) {
  const result = await apiClient.post(`/auth/login`, {}, {
    auth: {
      username: user.mail,
      password: user.password,
    },
    withCredentials: true
  })
  if (result.status == 200) {
    const redirectPath = getCookie("redirectedFrom")
    if (redirectPath) {
      navigateTo(redirectPath)
      deleteCookie("redirectedFrom")
    } else {
      toHome()
    }
    const authStore = useAuthStore();
    authStore.login()
  }
  return result
}

async function loginOauthGoogle() {
  if (!import.meta.client) return
  window.location.href = `${useRuntimeConfig().public.apiUrl}/auth/login-oauth-google`
}

async function signup(user) {
  const result = await apiClient.post(`/auth/signup?locale=${getLocale()}`, user)
  if (result.status == 201) {
    navigateTo(`/verification-email-sent`)
  }
  return result
}

async function verifyEmail(token) {
  return await apiClient.post(`/auth/verify?token=${token}`)
}

async function requestPasswordReset(mail) {
  return await apiClient.delete(`/auth/password/reset/${mail}?locale=${getLocale()}`)
}

async function resetPassword(token: string, password: string) {
  return await apiClient.put(`/auth/password/reset/${token}?password=${password}`)
}

async function logout() {
  const result = await apiClient.post("/auth/logout", {}, {
    withCredentials: true
  })
  if (result.status == 200) {
    navigateTo(`/logout`)
    const authStore = useAuthStore();
    authStore.logout()
  }
  return result
}

async function doDeleteImage(path,id) {
  return await imageClient.delete( `/${path}/${id}`).then(function(response){
    console.log('SUCCESS!!');
    console.log(response)
  })
    .catch(function(error){
      console.log('FAILURE!!');
      console.log(error)
    });
}

async function uploadImage(id, file, path) {
  let formData = new FormData()
  formData.append('file', file)
  return await imageClient.post( `/${path}/${id}`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }
  ).then(function(response){
    console.log('SUCCESS!!');
    console.log(response)
  })
    .catch(function(error){
      console.log('FAILURE!!');
      console.log(error)
    });
}


async function getHealth() {
  return await apiClient.get(`/health`)
}


const unitToReadable = (unit) => {
  switch (unit) {
    case "UNIT":
      return ""
    case "GRAM":
      return "g"
    case "MILLILITERS":
      return "mL"
    case "POUND":
      return "lb"
    case "TEASPOON":
      return ` ${t("unit_teaspoon")}`
    case "TABLESPOON":
      return ` ${t("unit_tablespoon")}`
    case "CUP":
      return " cups"
    case "NONE":
      return ""
    case null:
      return ""
  }
}


