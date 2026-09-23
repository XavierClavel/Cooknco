package com.xavierclavel.models

/**
 * The five figures a profile states about an account.
 *
 * Each one is the size of a collection on [User], and reading them off the entity costs five
 * queries per account — which a listing pays per row. [com.xavierclavel.services.UserService.countsOf]
 * answers for a whole page in five queries altogether, and this is what it hands the mappers.
 *
 * A follow that is still pending counts for neither side: a request is not a follower.
 */
data class UserCounts(
    val recipes: Int = 0,
    val likes: Int = 0,
    val cookbooks: Int = 0,
    val followers: Int = 0,
    val follows: Int = 0,
) {
    companion object {
        /** What an account nothing has been counted for reads as — all zeroes, never nulls. */
        val NONE = UserCounts()
    }
}
