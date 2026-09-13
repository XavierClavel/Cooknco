package com.xavierclavel.utils

import shared.dto.RecipeDTO

/**
 * Steps from plain text, for the tests that only care what a step *says*.
 *
 * A step carries an optional duration now — see [RecipeDTO.RecipeStepDTO] — and most of what
 * these tests assert about steps predates that and is about ordering, replacement and
 * deletion. These two keep that intent readable rather than dressing every fixture in a
 * duration it has no opinion about.
 */
fun stepsOf(vararg texts: String): MutableList<RecipeDTO.RecipeStepDTO> =
    texts.map { RecipeDTO.RecipeStepDTO(text = it) }.toMutableList()

fun stepsOf(texts: Collection<String>): MutableList<RecipeDTO.RecipeStepDTO> =
    stepsOf(*texts.toTypedArray())

/** What the steps say, in order. */
fun List<RecipeDTO.RecipeStepDTO>.texts(): List<String> = map { it.text }
