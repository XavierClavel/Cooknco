package shared.enums

/**
 * A picture the app shows in place of one an entity does not have.
 *
 * One entry per kind of content rather than one per [ImageBucket]: a recipe is displayed
 * at two sizes but has a single default, and an operator replacing it means both.
 */
enum class DefaultImage(val buckets: List<ImageBucket>) {
    USER(listOf(ImageBucket.USER)),
    RECIPE(listOf(ImageBucket.RECIPE, ImageBucket.RECIPE_THUMBNAIL)),
    COOKBOOK(listOf(ImageBucket.COOKBOOK)),
    ;

    companion object {
        /** The default a bucket serves. Every bucket belongs to exactly one. */
        fun of(bucket: ImageBucket): DefaultImage = entries.first { bucket in it.buckets }
    }
}
