package com.beust.kobalt.misc

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Inject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Future

/**
 * Retrieve Kobalt's latest release version from github.
 */
public class GithubApi @Inject constructor(val executors: KobaltExecutors) {
    companion object {
        const val HOST = "https://api.github.com/"
    }

    val latestKobaltVersion: Future<String>
        get() {
            val callable = Callable<String> {
                var result = "0"
                val url = HOST + "repos/cbeust/kobalt/releases"
                try {
                    val ins = URL(url).openConnection().inputStream
                    @Suppress("UNCHECKED_CAST")
                    val reader = BufferedReader(InputStreamReader(ins))
                    val jo = JsonParser().parse(reader) as JsonArray
//                    val jo = Parser().parse(ins) as JsonArray<JsonObject>
                    if (jo.size() > 0) {
                        var versionName = (jo.get(0) as JsonObject).get("name").asString
                        if (versionName == null) {
                            versionName = (jo.get(0) as JsonObject).get("tag_name").asString
                        }
                        if (versionName != null) {
                            result = versionName
                        }
                    }
                } catch(ex: IOException) {
                    warn("Couldn't load the release URL: $url")
                }
                result
            }
            return executors.miscExecutor.submit(callable)
        }
}