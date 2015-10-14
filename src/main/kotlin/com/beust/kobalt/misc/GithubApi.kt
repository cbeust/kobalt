package com.beust.kobalt.misc

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.string
import com.google.inject.Inject
import java.io.IOException
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
                    val jo = Parser().parse(ins) as JsonArray<JsonObject>
                    if (jo.size() > 0) {
                        var versionName = jo.get(0).string("name")
                        if (versionName == null) {
                            versionName = jo.get(0).string("tag_name")
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