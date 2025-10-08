package com.sirim.scanner.ui.screens.records

import org.json.JSONArray
import org.json.JSONObject

/** Represents a user-defined field captured alongside the standard SIRIM data. */
data class CustomFieldEntry(
    val name: String,
    val value: String,
    val maxLength: Int
)

fun List<CustomFieldEntry>.toJson(): String {
    val array = JSONArray()
    for (entry in this) {
        val obj = JSONObject()
        obj.put("name", entry.name)
        obj.put("value", entry.value)
        obj.put("maxLength", entry.maxLength)
        array.put(obj)
    }
    return array.toString()
}

fun String?.toCustomFieldEntries(): List<CustomFieldEntry> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val name = obj.optString("name").trim()
                val value = obj.optString("value")
                val maxLength = obj.optInt("maxLength", 500).coerceAtLeast(1)
                if (name.isNotEmpty() || value.isNotEmpty()) {
                    add(CustomFieldEntry(name = name, value = value, maxLength = maxLength))
                }
            }
        }
    }.getOrElse { emptyList() }
}
