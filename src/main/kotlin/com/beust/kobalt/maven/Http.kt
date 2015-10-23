package com.beust.kobalt.maven

import com.beust.kobalt.misc.log
import com.squareup.okhttp.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Singleton

@Singleton
public class Http {
    class Body(val body: ResponseBody, val code: Int) {
        public fun getAsString() : String {
            return body.string()
        }
        public fun getAsStream() : InputStream {
            return body.byteStream()
        }
    }

    public fun get(user: String?, password: String?, url: String) : Body {
        val client = OkHttpClient();
        val request = Request.Builder().url(url)
        if (user != null) {
            request.header("Authorization", Credentials.basic(user, password))
        }

        try {
            val response = client.newCall(request.build()).execute()
            return Body(response.body(), response.code())
        } catch(ex: IOException) {
            throw KobaltException("Could not load URL ${url}, error: " + ex.message, ex)
        }
    }

    private val MEDIA_TYPE_BINARY = MediaType.parse("application/octet-stream")

    public fun get(url: String) : Body {
        return get(null, null, url)
    }

    private fun builder(user: String?, password: String?) : Request.Builder {
        val result = Request.Builder()
        user?.let {
            result.header("Authorization", Credentials.basic(user, password))
        }
        return result
    }

    public fun uploadFile(user: String?, password: String?, url: String, file: File,
            success: (Response) -> Unit,
            error: (Response) -> Unit) {
        val request = builder(user, password)
            .url(url)
            .put(RequestBody.create(MEDIA_TYPE_BINARY, file))
            .build()

        log(2, "Uploading $file to $url")
        val response = OkHttpClient().newCall(request).execute()
        if (! response.isSuccessful) {
            error(response)
        } else {
            success(response)
        }
    }

    private val JSON = MediaType.parse("application/json; charset=utf-8")

    fun post(user: String?, password: String?, url: String, payload: String) : String {
        val request = builder(user, password)
            .url(url)
            .post(RequestBody.create(JSON, payload))
            .build()
        val response = OkHttpClient().newCall(request).execute()
        return response.body().string()
    }
}

class KobaltException(s: String? = null, ex: Throwable? = null) : RuntimeException(s, ex) {
    constructor(ex: Throwable?) : this(null, ex)
}
