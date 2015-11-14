package com.beust.kobalt.misc

import com.beust.kobalt.maven.Http
import com.beust.kobalt.maven.KobaltException
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.squareup.okhttp.Headers
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient
import retrofit.http.Body
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedFile
import rx.Observable
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Future
import javax.inject.Inject

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

        val username = localProperties.get(PROPERTY_USERNAME)
        val accessToken = localProperties.get(PROPERTY_ACCESS_TOKEN)
        try {
            service.createRelease(username, accessToken, packageName, CreateRelease(tagName))
                    .flatMap { response ->
                        uploadAsset(accessToken, response.uploadUrl!!, TypedFile("application/zip", zipFile),
                                tagName)
                    }
                    .toBlocking()
                    .forEach { action ->
                        log(1, "\nRelease successfully uploaded ${zipFile.name}")
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
        val url = "$strippedUrl?name=$tagName&label=$tagName"
        val headers = Headers.of("Authorization", "token $token")
        val totalSize = typedFile.file().length()
        http.uploadFile(url = url, file = typedFile, headers = headers,
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
                var result = "0"
                try {
                    val ins = URL(RELEASES_URL).openConnection().inputStream
                    @Suppress("UNCHECKED_CAST")
                    val reader = BufferedReader(InputStreamReader(ins))
                    val jo = JsonParser().parse(reader) as JsonArray
                    if (jo.size() > 0) {
                        var versionName = (jo.get(0) as JsonObject).get("name").asString
                        if (Strings.isEmpty(versionName)) {
                            versionName = (jo.get(0) as JsonObject).get("tag_name").asString
                        }
                        if (versionName != null) {
                            result = versionName
                        }
                    }
                } catch(ex: IOException) {
                    warn("Couldn't load the release URL: $RELEASES_URL")
                }
                result
            }
            return executors.miscExecutor.submit(callable)
        }
}
