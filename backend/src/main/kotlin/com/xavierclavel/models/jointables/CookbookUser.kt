package com.xavierclavel.models.jointables

import com.xavierclavel.models.Cookbook
import com.xavierclavel.models.User
import shared.infodto.CookbookUserInfo
import io.ebean.Model
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(name = "cookbook_users")
class CookbookUser (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    var user: User,

    @ManyToOne
    var cookbook: Cookbook,

    var isAdmin: Boolean = false,

    var joinDate: LocalDateTime = LocalDateTime.now(),

): Model() {

    fun toInfo() = CookbookUserInfo(
        id = user.id,
        version = user.imageVersion,
        username = user.username,
        isAdmin = isAdmin,
        joinDate = joinDate.toEpochSecond(ZoneOffset.UTC),
    )
}