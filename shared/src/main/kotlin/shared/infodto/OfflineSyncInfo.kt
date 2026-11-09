package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.OfflinePinSet
import shared.overviewdto.RecipeOverview

/**
 * Everything a phone needs to bring its offline copy up to date, in one answer to
 * `POST /offline/sync`.
 *
 * The server decides what the phone should hold, from the same listings and the same
 * visibility rules every other screen reads: a recipe the phone holds and [pinned] does not
 * name is one it should drop, whatever the reason — unliked, taken out of a cookbook, deleted,
 * hidden, or out of reach since the author went private. Nothing has to record those as
 * events, which is what lets this be a comparison rather than a log.
 *
 * @property pinned every recipe the phone should hold, own recipes first, then cookbook ones,
 *   then likes, each list most recent first and bounded.
 * @property recipes the pinned recipes whose copy on the phone is missing or out of date, in
 *   [pinned]'s order, at most a page of them. Each carries the revision it was read at, which
 *   is the one the phone should record — [pinned]'s can be a moment older.
 * @property hasMore whether more of them are waiting: ask again straight away, with the
 *   recipes just received counted as held.
 * @property notes the account's notes on any pinned recipe, every time — they are its own and
 *   few, and editing one changes nothing about the recipe a revision could follow.
 * @property own and [liked] the two lists the profile draws, as the listings serve them.
 */
@Serializable
data class OfflineSyncInfo(
    val pinned: List<OfflinePin> = emptyList(),
    val recipes: List<RecipeInfo> = emptyList(),
    val hasMore: Boolean = false,
    val notes: List<OfflineNotes> = emptyList(),
    val own: List<RecipeOverview> = emptyList(),
    val liked: List<RecipeOverview> = emptyList(),
    val cookbooks: List<OfflineCookbookInfo> = emptyList(),
)

/**
 * @property isLiked read for every pinned recipe, rather than inferred from [sets] holding
 *   `LIKED`: a recipe liked long ago can be past the likes' bound and pinned for another
 *   reason, and its heart is still filled in.
 */
@Serializable
data class OfflinePin(
    val id: Long,
    val revision: Long,
    val sets: Set<OfflinePinSet>,
    val isLiked: Boolean = false,
)

@Serializable
data class OfflineNotes(
    val recipeId: Long,
    val notes: String,
)

/** A cookbook the account belongs to, with every row its screen lists — unpaged. */
@Serializable
data class OfflineCookbookInfo(
    val cookbook: CookbookInfo,
    val recipes: List<CookbookRecipeInfo> = emptyList(),
)
