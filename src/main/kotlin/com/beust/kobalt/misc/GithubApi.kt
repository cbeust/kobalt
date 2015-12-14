package com.beust.kobalt.misc

import com.beust.kobalt.*
import com.beust.kobalt.internal.*
import com.beust.kobalt.maven.*
import com.google.gson.*
import com.google.gson.annotations.*
import com.squareup.okhttp.*
import com.squareup.okhttp.Headers
import retrofit.*
import retrofit.client.*
import retrofit.client.Response
import retrofit.http.*
import retrofit.http.Path
import retrofit.mime.*
import rx.Observable
import java.io.*
import java.net.*
import java.nio.file.*
import java.util.*
import java.util.concurrent.*
import javax.inject.*

/**
 * Retrieve Kobalt's latest release version from github.
 */
public class GithubApi @Inject constructor(val executors: KobaltExecutors,
        val localProperties: LocalProperties, val http: Http) {
    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/cbeust/kobalt/releases"
        const val PROPERTY_ACCESS_TOKEN = "github.accessToken"
        const val PROPERTY_USERNAME = "github.username"
    }

    class RetrofitErrorResponse(val code: String?, val field: String?)
    class RetrofitErrorsResponse(val message: String?, val errors: List<RetrofitErrorResponse>)

    private fun parseRetrofitError(e: Throwable) : RetrofitErrorsResponse {
        val re = e as RetrofitError
        val json = String((re.response.body as TypedByteArray).bytes)
        return Gson().fromJson(json, RetrofitErrorsResponse::class.java)
    }

    fun uploadRelease(packageName: String, tagName: String, zipFile: File) {
        log(1, "Uploading release ${zipFile.name}")

        val docUrl = DocUrl.PUBLISH_PLUGIN_URL
        val username = localProperties.get(PROPERTY_USERNAME, docUrl)
        val accessToken = localProperties.get(PROPERTY_ACCESS_TOKEN, docUrl)
        try {
            service.createRelease(username, accessToken, packageName, CreateRelease(tagName))
                    .flatMap { response ->
                        uploadAsset(accessToken, response.uploadUrl!!, TypedFile("application/zip", zipFile),
                                tagName)
                    }
                    .toBlocking(
                    .forEach { action ->
                        log(1, "\n${zipFile.name} successfully uploaded")
                    }
        } catch(e: RetrofitError) {
            val error = parseRetrofitError(e)
            throw KobaltException("Couldn't upload release, ${error.message}: "
                    + error.errors[0].code + " field: " + error.errors[0].field)
        }
    }

    private fun uploadAsset(token: String, uploadUrl: String, typedFile: TypedFile, tagName: String)
            : Observable<UploadAssetResponse> {
        val strippedUrl = uploadUrl.substring(0, uploadUrl.indexOf("{"))
        val fileName = typedFile.file().name
        val url = "$strippedUrl?name=$fileName&label=$fileName"
        val headers = Headers.of("Authorization", "token $token")
        val totalSize = typedFile.file().length()
        http.uploadFile(url = url, file = typedFile, headers = headers, post = true, // Github requires POST
                progressCallback = http.percentProgressCallback(totalSize))

        return Observable.just(UploadAssetResponse(tagName, tagName))
    }

    //
    // Read only Api
    //

    private val service = RestAdapter.Builder()
//            .setLogLevel(RestAdapter.LogLevel.FULL)
            .setClient(OkClient(OkHttpClient()))
            .setEndpoint("https://api.github.com")
            .build()
            .create(Api::class.java)

    //
    // JSON mapped classes that get sent up and down
    //
    class CreateRelease(@SerializedName("tag_name") var tagName: String? = null,
            var name: String? = tagName)
    class CreateReleaseResponse(var id: String? = null, @SerializedName("upload_url") var uploadUrl: String?)
    class UploadAssetResponse(var id: String? = null, val name: String? = null)

    interface Api {
        @POST("/repos/{owner}/{repo}/releases")
        fun createRelease(@Path("owner") owner: String,
                @Query("access_token") accessToken: String,
                @Path("repo") repo: String,
                @Body createRelease: CreateRelease): Observable<CreateReleaseResponse>
    }

    val latestKobaltVersion: Future<String>
        get() {
            val callable = Callable<String> {
                try {
                    URL(RELEASES_URL).openConnection().inputStream.bufferedReader().use { reader ->
                        val jo = JsonParser().parse(reader) as JsonArray

                        jo.filterIsInstance<JsonObject>()
                            .map { it.get("name")?.safeToString() ?: it.get("tag_name")?.safeToString() }
                            .filterNotNull()
                            .maxBy { Versions.toLongVersion(it) } ?: "0"
                    }
                } catch(ex: IOException) {
                    warn("Couldn't load the release URL: $RELEASES_URL")
                    "0"
                }
            }
            return executors.miscExecutor.submit(callable)
        }
}

fun Response.bodyContent() : String {
    val bodyBytes = (body as TypedByteArray).bytes
    val bodyMime = body.mimeType()
    val bodyCharset = MimeUtil.parseCharset(bodyMime, "utf-8")
    val result = String(bodyBytes, bodyCharset)
    return result
    //            return new Gson().fromJson(data, type);
}

class Prop {
    companion object {
        const val ACCESS_TOKEN_PROPERTY = "github.accessToken"
        const val USERNAME_PROPERTY = "github.username"

        val localProperties: Properties by lazy {
            val result = Properties()
            val filePath = Paths.get("local.properties")
            if (! Files.exists(filePath)) {
                throw KobaltException("Couldn't find a local.properties file")
            }

            filePath.let { path ->
                if (Files.exists(path)) {
                    Files.newInputStream(path).use {
                        result.load(it)
                    }
                }
            }

            result
        }

        private fun fromProperties(name: String) : String {
            val result = localProperties.getRaw(name)
                    ?: throw KobaltException("Couldn't find $name in local.properties")
            return result as String
        }

        val accessToken: String get() = fromProperties(ACCESS_TOKEN_PROPERTY)
        val username: String get() = fromProperties(USERNAME_PROPERTY)
    }
}

private fun JsonElement.safeToString() = when {
    isJsonNull -> null
    else -> asString
}
