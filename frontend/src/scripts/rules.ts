import {
  ICON_ALPHABETICAL,
  ICON_AMOUNT,
  ICON_DATE,
  ICON_LIKES, ICON_NONE,
  ICON_RANDOM, ICON_VISIBILITY_PRIVATE, ICON_VISIBILITY_PROTECTED, ICON_VISIBILITY_PUBLIC,
  ICON_VOLUME,
  ICON_WEIGHT
} from "@/scripts/icons";
import {t} from "@/scripts/localization";

export {
  requiredRule,
  min8Rule,
  passwordRule,
  max511,
  max255,
  max100,
  max50,
}

const requiredRule = value => !!value || t('required')
const min8Rule = v => v.length >= 8 || t('min_8_characters')
const max511 = v => !v || v.length <= 511 || t('max_500_characters')
const max255 = v => !v || v.length <= 255 || t('max_200_characters')
const max100 = v => !v || v.length <= 100 || t('max_100_characters')
const max50 = v => !v || v.length <= 50 || t('max_50_characters')
const passwordRule = (password1, password2) => password1 == password2 || t('passwords_must_match')
