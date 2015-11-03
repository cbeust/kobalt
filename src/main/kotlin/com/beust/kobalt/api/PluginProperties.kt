package com.beust.kobalt.api

import com.google.inject.Inject
import java.util.*

/**
 * Plugins can publish to and read from this object in order to exchange information. Keys stored in
 * these maps should be annotated with @ExportedProperty.
 */
class PluginProperties @Inject constructor() {
    private val pluginProperties = hashMapOf<String, HashMap<String, Any>>()

    fun put(pluginName: String, key: String, value: Any) {
        var map = pluginProperties[pluginName]
        if (map == null) {
            map = hashMapOf<String, Any>()
            pluginProperties.put(pluginName, map)
        }
        map.put(key, value)
    }

    fun get(pluginName: String, key: String) = pluginProperties[pluginName]?.get(key)
}
