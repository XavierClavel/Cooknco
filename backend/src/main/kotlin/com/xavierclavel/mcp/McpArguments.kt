package com.xavierclavel.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Reading and describing tool arguments.
 *
 * A tool's input schema is advertised to the client, not enforced by the protocol: arguments
 * arrive as whatever JSON the caller sent, so every read here validates instead of trusting.
 * A bad argument raises [InvalidToolArgument], which comes back as a failed tool *result*
 * rather than a transport error — the model that called the tool reads the reason and can fix
 * its call. See `CookncoMcpServer.tool`.
 *
 * Numbers are also accepted as strings ("2" as well as 2). Models routinely quote them, and
 * refusing would only cost a round trip to learn nothing.
 */
class InvalidToolArgument(message: String) : Exception(message)

private fun JsonObject.present(name: String): JsonElement? = this[name]?.takeUnless { it is JsonNull }

private fun JsonObject.primitive(name: String): JsonPrimitive? =
    present(name)?.let {
        it as? JsonPrimitive ?: throw InvalidToolArgument("'$name' must be a single value, not ${it::class.simpleName}")
    }

fun JsonObject.optionalString(name: String): String? =
    primitive(name)?.let {
        if (!it.isString) throw InvalidToolArgument("'$name' must be a string")
        it.content.takeIf { content -> content.isNotBlank() }
            ?: throw InvalidToolArgument("'$name' must not be empty")
    }

fun JsonObject.requiredString(name: String): String =
    optionalString(name) ?: throw InvalidToolArgument("'$name' is required")

fun JsonObject.optionalLong(name: String): Long? =
    primitive(name)?.let {
        it.content.trim().toLongOrNull() ?: throw InvalidToolArgument("'$name' must be a whole number")
    }

fun JsonObject.requiredLong(name: String): Long =
    optionalLong(name) ?: throw InvalidToolArgument("'$name' is required")

fun JsonObject.optionalInt(name: String): Int? =
    primitive(name)?.let {
        it.content.trim().toIntOrNull() ?: throw InvalidToolArgument("'$name' must be a whole number")
    }

fun JsonObject.optionalFloat(name: String): Float? =
    primitive(name)?.let {
        it.content.trim().toFloatOrNull() ?: throw InvalidToolArgument("'$name' must be a number")
    }

fun JsonObject.optionalBoolean(name: String): Boolean? =
    primitive(name)?.let {
        when (it.content.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw InvalidToolArgument("'$name' must be true or false")
        }
    }

/** Case-insensitive, and names the accepted values on failure so the caller can retry. */
inline fun <reified T : Enum<T>> JsonObject.optionalEnum(name: String): T? =
    optionalString(name)?.let { value ->
        enumValues<T>().find { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw InvalidToolArgument(
                "'$name' must be one of ${enumValues<T>().joinToString(", ") { it.name }}, got '$value'"
            )
    }

/** Accepts a JSON array as well as the comma-separated string models often send instead. */
fun JsonObject.optionalLongList(name: String): List<Long> {
    val element = present(name) ?: return emptyList()
    val values = when (element) {
        is JsonArray -> element.map {
            (it as? JsonPrimitive)?.content ?: throw InvalidToolArgument("'$name' must be a list of whole numbers")
        }
        is JsonPrimitive -> element.content.split(",")
        else -> throw InvalidToolArgument("'$name' must be a list of whole numbers")
    }
    return values.filter { it.isNotBlank() }.map {
        it.trim().toLongOrNull() ?: throw InvalidToolArgument("'$name' must be a list of whole numbers, got '$it'")
    }
}

fun JsonObject.optionalStringList(name: String): List<String>? {
    val element = present(name) ?: return null
    val values = when (element) {
        is JsonArray -> element.map {
            (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
                ?: throw InvalidToolArgument("'$name' must be a list of strings")
        }
        is JsonPrimitive -> listOf(element.content)
        else -> throw InvalidToolArgument("'$name' must be a list of strings")
    }
    return values.map { it.trim() }.filter { it.isNotEmpty() }
}

fun JsonObject.optionalObjectList(name: String): List<JsonObject>? {
    val element = present(name) ?: return null
    val array = element as? JsonArray ?: throw InvalidToolArgument("'$name' must be a list of objects")
    return array.map { it as? JsonObject ?: throw InvalidToolArgument("every entry of '$name' must be an object") }
}

/** Rejects arguments the tool does not declare, so a typo is reported instead of ignored. */
fun JsonObject.rejectUnknown(schema: ToolSchema) {
    val declared = schema.properties?.keys ?: emptySet()
    val unknown = keys - declared
    if (unknown.isNotEmpty()) {
        throw InvalidToolArgument(
            "unknown argument${if (unknown.size > 1) "s" else ""} ${unknown.joinToString(", ")}; " +
                "this tool accepts ${declared.joinToString(", ").ifEmpty { "no arguments" }}"
        )
    }
}

fun toolSchema(vararg properties: Pair<String, JsonObject>, required: List<String> = emptyList()): ToolSchema =
    ToolSchema(
        properties = JsonObject(properties.toMap()),
        required = required.takeIf { it.isNotEmpty() },
    )

private fun property(type: String, description: String, extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
    put("type", type)
    put("description", description)
    extra()
}

fun stringArg(description: String): JsonObject = property("string", description)

fun intArg(description: String): JsonObject = property("integer", description)

fun numberArg(description: String): JsonObject = property("number", description)

fun boolArg(description: String): JsonObject = property("boolean", description)

fun enumArg(description: String, values: Array<out Enum<*>>): JsonObject =
    property("string", description) {
        putJsonArray("enum") { values.forEach { add(it.name) } }
    }

fun arrayArg(description: String, items: JsonObject): JsonObject =
    property("array", description) { put("items", items) }

fun objectArg(description: String, vararg properties: Pair<String, JsonObject>): JsonObject =
    property("object", description) {
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
    }
