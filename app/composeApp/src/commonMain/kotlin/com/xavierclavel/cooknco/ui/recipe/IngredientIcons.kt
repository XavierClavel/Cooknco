package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.resources.Res
import com.xavierclavel.cooknco.resources.ingredient_alcohol
import com.xavierclavel.cooknco.resources.ingredient_bakery
import com.xavierclavel.cooknco.resources.ingredient_beverage_ingredient
import com.xavierclavel.cooknco.resources.ingredient_condiment
import com.xavierclavel.cooknco.resources.ingredient_dairy
import com.xavierclavel.cooknco.resources.ingredient_fish
import com.xavierclavel.cooknco.resources.ingredient_fruit
import com.xavierclavel.cooknco.resources.ingredient_grain
import com.xavierclavel.cooknco.resources.ingredient_meat
import com.xavierclavel.cooknco.resources.ingredient_miscellaneous
import com.xavierclavel.cooknco.resources.ingredient_nut
import com.xavierclavel.cooknco.resources.ingredient_oil
import com.xavierclavel.cooknco.resources.ingredient_vegetable
import org.jetbrains.compose.resources.DrawableResource

/**
 * The picture for an ingredient's kind.
 *
 * Bundled rather than fetched. These used to be loaded from
 * `https://cooknco.eu/image/ingredients/<TYPE>.webp`, which has never held a single one of
 * them — every ingredient row in the app asked the network for a picture, got a 404 and drew
 * an empty box. Fourteen fixed glyphs that change only when the app does have no business
 * being a per-row network request in the first place, so they ship with it: no round trip, no
 * flash of nothing, and they work with no signal in a kitchen.
 *
 * Anything this does not recognise — a kind added to the backend's enum after this build, or
 * a custom ingredient with no kind at all — gets the bowl, which is the one glyph in the set
 * that deliberately looks like no particular kind of food.
 */
fun ingredientIcon(type: String?): DrawableResource = when (type?.uppercase()) {
    "VEGETABLE" -> Res.drawable.ingredient_vegetable
    "FRUIT" -> Res.drawable.ingredient_fruit
    "GRAIN" -> Res.drawable.ingredient_grain
    "NUT" -> Res.drawable.ingredient_nut
    "DAIRY" -> Res.drawable.ingredient_dairy
    "FISH" -> Res.drawable.ingredient_fish
    "MEAT" -> Res.drawable.ingredient_meat
    "CONDIMENT" -> Res.drawable.ingredient_condiment
    "OIL" -> Res.drawable.ingredient_oil
    "BAKERY" -> Res.drawable.ingredient_bakery
    "BEVERAGE_INGREDIENT" -> Res.drawable.ingredient_beverage_ingredient
    "ALCOHOL" -> Res.drawable.ingredient_alcohol
    else -> Res.drawable.ingredient_miscellaneous
}
