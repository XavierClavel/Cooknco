package shared.infodto

import shared.enums.Visibility
import shared.overviewdto.UserOverview
import kotlinx.serialization.Serializable

@Serializable
data class CookbookInfo(
    val id: Long,
    val version: Long,
    val title: String,
    val visibility: Visibility,
    val description: String = "",
    val recipesCount: Int,
    val usersCount: Int,
    /** The first [MEMBERS_SHOWN] members by join date; [usersCount] says how many there are. */
    val members: List<UserOverview>,
) {
    companion object {
        /** How many members a cookbook names. Enough for a row of faces, not a membership list. */
        const val MEMBERS_SHOWN = 10
    }
}