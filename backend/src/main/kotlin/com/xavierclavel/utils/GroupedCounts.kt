package com.xavierclavel.utils

import io.ebean.Query

/**
 * How many child rows each parent has, in one query — what a list DTO stating a collection's size
 * has to use instead of reading the collection.
 *
 * Reading `parent.children.size` while mapping a list costs one round trip per row. Ebean will not
 * fetch a `-to-many` out of that: every listing here is paged, and a `-to-many` fetch path is
 * dropped from the plan the moment `maxRows` is set — quietly, and whether it was asked for as a
 * join or as a secondary query (`fetchQuery` behaves exactly like `fetchLazy` in that case). So the
 * count is asked for from the child side, grouped by the parent id, and handed to the mapper.
 *
 * ```kotlin
 * fun countLikesByRecipe(recipeIds: Collection<Long>): Map<Long, Int> =
 *     QLike()
 *         .select("${QLike.Alias.recipe.id}, count(*)")
 *         .recipe.id.`in`(recipeIds)
 *         .query()
 *         .countByParent()
 * ```
 *
 * `select` has no typed overload for an aggregate, so the column list is a string — with every path
 * taken from `Alias`, as the aggregates in `RecipeService` are. Ebean adds the `group by` itself,
 * from the non-aggregate columns selected.
 *
 * `FetchPlanTest` is what holds the result: a listing whose cost grows with its rows fails there.
 */
class GroupedCount(
    var parentId: Long = 0,
    var total: Long = 0,
)

/**
 * Runs a `<parent id>, count(*)` query and maps it.
 *
 * @return count per parent id. A parent with no child row is **absent**, not zero: the query returns
 *   no row for it, and callers read the map with a `?: 0` for exactly that reason.
 */
fun <T : Any> Query<T>.countByParent(): Map<Long, Int> =
    asDto(GroupedCount::class.java)
        .findList()
        .associate { it.parentId to it.total.toInt() }
