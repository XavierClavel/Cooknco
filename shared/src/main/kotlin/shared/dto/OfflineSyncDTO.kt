package shared.dto

import kotlinx.serialization.Serializable

/**
 * What a phone already holds, sent to `POST /offline/sync` so that the answer carries only what
 * has changed since.
 *
 * @property held every recipe the phone has a copy of, with the
 *   [revision][shared.infodto.RecipeInfo.revision] that copy was read at. Nothing else about the
 *   phone's state is asked for: which recipes it *should* hold is the server's to work out.
 */
@Serializable
data class OfflineSyncDTO(
    val held: List<HeldRecipe> = emptyList(),
) {
    /**
     * @property revision null for a copy written before the field existed, which matches no
     *   revision at all — so that recipe comes back whole, once.
     */
    @Serializable
    data class HeldRecipe(
        val id: Long,
        val revision: Long? = null,
    )
}
