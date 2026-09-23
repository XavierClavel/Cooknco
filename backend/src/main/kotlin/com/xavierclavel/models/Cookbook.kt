package com.xavierclavel.models

import com.xavierclavel.models.jointables.CookbookRecipe
import com.xavierclavel.services.CookbookService
import com.xavierclavel.models.jointables.CookbookUser
import shared.dto.CookbookDTO
import shared.enums.Visibility
import shared.infodto.CookbookInfo
import shared.overviewdto.CookbookOverview
import shared.overviewdto.CookbookRecipeOverview
import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "cookbooks")
class Cookbook (

    @Id
    @GeneratedValue
    var id: Long = 0,

    @DbDefault("0")
    var imageVersion: Long = 0,

    var title: String = "",

    var description: String = "",

    var creationDate: LocalDateTime = LocalDateTime.now(),

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var recipes: MutableSet<CookbookRecipe> = mutableSetOf(),

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var users: MutableSet<CookbookUser> = mutableSetOf(),

    @DbDefault("1")
    var visibility: Visibility = Visibility.PUBLIC,

): Model() {
    companion object {
        fun from(cookbookDTO: CookbookDTO)= Cookbook(
            title = cookbookDTO.title,
            description = cookbookDTO.description,
            visibility = cookbookDTO.visibility,
        )
    }

    fun merge(cookbookDTO: CookbookDTO) = this.apply {
        this.title = cookbookDTO.title
        this.description = cookbookDTO.description
        this.visibility = cookbookDTO.visibility
    }

    /**
     * @param recipesCount and the membership counted by the caller, not read off `recipes` and
     *   `users`. Both are collections, so a listing reading them costs two round trips per
     *   cookbook — see `CookbookService.countRecipesByCookbook` and `membershipOf`, and
     *   `CookbookFetchPlanTest`, which is what keeps them in use.
     */
    fun toInfo(recipesCount: Int, membership: CookbookService.Membership) = CookbookInfo(
        id = this.id,
        version = this.imageVersion,
        title = this.title,
        visibility = this.visibility,
        description = this.description,
        recipesCount = recipesCount,
        usersCount = membership.count,
        members = membership.shown,
    )
    fun toOverview() = CookbookOverview(
        id = this.id,
        version = this.imageVersion,
        title = this.title,
    )

    fun toRecipeOverview(present: Boolean) = CookbookRecipeOverview(
        id = this.id,
        title = this.title,
        hasRecipe = present,
    )

    fun increaseVersion() = apply {
        this.imageVersion++;
    }.update()

    fun resetVersion() = apply {
        this.imageVersion = 0
    }.update()

}