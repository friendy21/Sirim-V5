package com.sirim.scanner.data.db

import org.json.JSONArray

fun List<String>.toGalleryJson(): String {
    val array = JSONArray()
    for (path in this) {
        if (path.isNotBlank()) {
            array.put(path)
        }
    }
    return array.toString()
}

fun String?.toGalleryList(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).orEmpty().trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }.getOrElse { emptyList() }
}
