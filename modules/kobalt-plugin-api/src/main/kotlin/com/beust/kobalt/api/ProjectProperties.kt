package com.beust.kobalt.api

/**
 * Plugins can add and read properties from the project by using this class, found on the Project class.
 * Keys stored in this map by plug-ins should be annotated with @ExportedProjectProperty.
 */
class ProjectProperties {
    private val properties = hashMapOf<String, Any>()

    fun put(key: String, value: Any) = properties.put(key, value)

    fun get(key: String) = properties[key]

    fun getString(key: String): String? {
        val result = get(key)
        return if (result != null) result as String
        else null
    }
}

