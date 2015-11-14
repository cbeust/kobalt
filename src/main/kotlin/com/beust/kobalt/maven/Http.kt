package com.beust.kobalt.maven

import com.beust.kobalt.KobaltException
import com.beust.kobalt.misc.CountingFileRequestBody
import com.beust.kobalt.misc.log
import com.squareup.okhttp.*
import retrofit.mime.TypedFile
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
            throw KobaltException("Could not load URL $url, error: " + ex.message, ex)
        }
    }

    private val MEDIA_TYPE_BINARY = MediaType.parse("application/octet-stream")

    public fun get(url: String) : Body {
        return get(null, null, url)
    }

    fun percentProgressCallback(totalSize: Long) : (Long) -> Unit {
        return { num: Long ->
            val progress = num * 100 / totalSize
            log(1, "\rUploaded: $progress%", newLine = false)
        }
    }

    val DEFAULT_ERROR_RESPONSE = { r: Response ->
        error("Couldn't upload file: " + r.message())
    }

    public fun uploadFile(user: String? = null, password: String? = null, url: String, file: TypedFile,
            post: Boolean,
            progressCallback: (Long) -> Unit = {},
            headers: Headers = Headers.of(),
            success: (Response) -> Unit = {},
            error: (Response) -> Unit = DEFAULT_ERROR_RESPONSE) {

        val fullHeaders = Headers.Builder()
        fullHeaders.set("Content-Type", file.mimeType())
        headers.names().forEach { fullHeaders.set(it, headers.get(it)) }

        user?.let {
            fullHeaders.set("Authorization", Credentials.basic(user, password))
        }

        val requestBuilder = Request.Builder()
                .headers(fullHeaders.build())
                .url(url)
        val request =
            (if (post)
                requestBuilder.post(CountingFileRequestBody(file.file(), file.mimeType(), progressCallback))
            else
                requestBuilder.put(CountingFileRequestBody(file.file(), file.mimeType(), progressCallback)))
            .build()

        log(2, "Uploading $file to $url")
        val response = OkHttpClient().newCall(request).execute()
        if (! response.isSuccessful) {
            error(response)
        } else {
            success(response)
        }
    }
}
