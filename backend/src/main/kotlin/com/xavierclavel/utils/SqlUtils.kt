package com.xavierclavel.utils

/**
 * Renders a string as an injection-proof PostgreSQL literal for places where Ebean
 * cannot bind parameters (e.g. orderBy clauses, which are copied into SQL verbatim).
 * The value is hex-encoded, so the emitted SQL only contains [0-9a-f] characters.
 */
fun sqlStringLiteral(value: String): String =
    "convert_from(decode('" +
        value.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) } +
        "', 'hex'), 'UTF8')"
