package shared.enums

/**
 * A database the nightly job is expected to dump.
 *
 * Enumerated rather than discovered from the volume on purpose: the whole point of the
 * backups tab is to notice a dump that stopped being written, and a database only known
 * from the files it left behind disappears from the page on the very night it stops being
 * backed up — exactly when it should turn red.
 *
 * [filePrefix] is the name the job passes to its `dump` function (`k8s/base/backup.yaml`),
 * which writes `{filePrefix}-{yyyyMMdd-HHmmss}.dump`.
 */
enum class BackupDatabase(val filePrefix: String) {
    /** The application database, behind the `cooknco-database` service. */
    COOKNCO("cooknco"),

    /** `mail-service`'s own database, which holds the outbox and what it has sent. */
    MAIL_SERVICE("mail-service"),
    ;

    companion object {
        fun fromFilePrefix(prefix: String): BackupDatabase? = entries.find { it.filePrefix == prefix }
    }
}
