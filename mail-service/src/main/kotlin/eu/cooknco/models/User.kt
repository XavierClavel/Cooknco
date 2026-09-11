package eu.cooknco.models

import io.ebean.Model
import jakarta.persistence.*
import shared.enums.Locale

@Entity
@Table(name = "users")
class User(
    @Id
    val id: Long = 0L,
    val username: String = "",
    val encryptedMail: String = "",
    /**
     * The account's language, or null while nothing has ever reported one.
     *
     * Mirrors the backend's `User.locale`, nullable for the same reason: the column held FR
     * for everybody for as long as nothing wrote it, which is why every mail sent from here
     * went out in French. Readers fall back rather than believe a default — see
     * `getMailAndLocale`.
     */
    var locale: Locale? = null,
    @Column(name = "notifications_enabled")
    var notificationsEnabled: Boolean = false,
): Model()