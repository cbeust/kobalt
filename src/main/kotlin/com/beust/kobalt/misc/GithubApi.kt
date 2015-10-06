package com.beust.kobalt.misc

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.string
import com.beust.kobalt.maven.Http
import com.google.inject.Inject
import java.io.IOException
import java.net.URL

public class GithubApi @Inject constructor(val http: Http) : KobaltLogger {
    companion object {
        const val HOST = "https://api.github.com/"
    }

    val latestKobaltRelease: String
        get() {
            val url = HOST + "repos/cbeust/kobalt/releases"
            try {
                val ins = URL(url).openConnection().inputStream
                val jo = Parser().parse(ins) as JsonArray<JsonObject>
                if (jo.size() > 0) {
                    var result = jo.get(0).string("name")
                    if (result == null) {
                        result = jo.get(0).string("tag_name")
                    }
                    if (result != null) {
                        return result
                    }
                }
            } catch(ex: IOException) {
                warn("Couldn't load the release URL: ${url}")
            }
            return "0"
        }
}