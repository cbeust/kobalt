package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.internal.DocUrl
import com.beust.kobalt.internal.KobaltSettings
import com.beust.kobalt.internal.build.VersionCheckTimestampFile
import com.beust.kobalt.maven.Http
import com.beust.kobalt.maven.aether.Exceptions
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.inject.Inject
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import rx.Observable
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Future

class GithubApi2 @Inject constructor(
        val executors: KobaltExecutors, val localProperties: LocalProperties, val http: Http,
        val settings:KobaltSettings, val args: Args) {

    companion object {
        const val PROPERTY_ACCESS_TOKEN = "github.accessToken"
        const val PROPERTY_USERNAME = "github.username"
    }

    private val DOC_URL = DocUrl.PUBLISH_PLUGIN_URL

    //
    // JSON mapped classes that get sent up and down
    //
    class CreateRelease(@SerializedName("tag_name") var tagName: String? = null,
            var name: String? = tagName)
    class CreateReleaseResponse(var id: String? = null, @SerializedName("upload_url") var uploadUrl: String?)
    class UploadAssetResponse(var id: String? = null, val name: String? = null)
    class ReleasesResponse(@SerializedName("tag_name") var tagName: String? = null,
            var name: String? = tagName)

    interface Api {
        @POST("/repos/{owner}/{repo}/releases")
        fun createRelease(@Path("owner") owner: String,
                @Path("repo") repo: String,
                @Query("access_token") accessToken: String,
                @Body createRelease: CreateRelease): Call<CreateReleaseResponse>

        @GET("/repos/{owner}/{repo}/releases")
        fun getReleases(@Path("owner") owner: String,
                @Path("repo") repo: String,
                @Query("access_token") accessToken: String): Call<List<ReleasesResponse>>

        @GET("/repos/{owner}/{repo}/releases")
        fun getReleasesNoAuth(@Path("owner") owner: String,
                @Path("repo") repo: String): Call<List<ReleasesResponse>>
    }

    //
    // Read only Api
    //
    private val service = Retrofit.Builder()
            .client(OkHttpClient.Builder().proxy(settings.proxyConfigs?.firstOrNull()?.toProxy()).build())
            .baseUrl("https://api.github.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)

    // JSON Retrofit error
    class Error(val code: String)
    class RetrofitError(var message: String = "", var errors : List<Error> = arrayListOf())

    fun uploadRelease(packageName: String, tagName: String, zipFile: File) {
        kobaltLog(1, "Uploading release ${zipFile.name}")

        val username = localProperties.get(PROPERTY_USERNAME, DOC_URL)
        val accessToken = localProperties.get(PROPERTY_ACCESS_TOKEN, DOC_URL)
        val response = service.createRelease(username, packageName, accessToken, CreateRelease(tagName))
                .execute()
        val code = response.code()
        if (code != Http.CREATED) {
            val error = Gson().fromJson(response.errorBody().string(), RetrofitError::class.java)
            throw KobaltException("Couldn't upload release, ${error.message}: " + error.errors[0].code)
        } else {
            val body = response.body()

            uploadAsset(accessToken, body.uploadUrl!!, Http.TypedFile("application/zip", zipFile), tagName)
                    .toBlocking()
                .forEach { action ->
                    kobaltLog(1, "\n${zipFile.name} successfully uploaded")
                }
        }
    }

    private fun uploadAsset(token: String, uploadUrl: String, typedFile: Http.TypedFile, tagName: String)
            : Observable<UploadAssetResponse> {
        val strippedUrl = uploadUrl.substring(0, uploadUrl.indexOf("{"))
        val fileName = typedFile.file.name
        val url = "$strippedUrl?name=$fileName&label=$fileName"
        val headers = okhttp3.Headers.of("Authorization", "token $token")
        val totalSize = typedFile.file.length()
        http.uploadFile(url = url, file = typedFile, headers = headers, post = true, // Github requires POST
                progressCallback = http.percentProgressCallback(totalSize))

        return Observable.just(UploadAssetResponse(tagName, tagName))
    }

    val latestKobaltVersion: Future<String>
        get() {
            val callable = Callable<String> {
                var result = Kobalt.version
                if (! args.isDev && Duration.ofMinutes(10L) >
                        Duration.between(VersionCheckTimestampFile.timestamp, Instant.now())) {
                    kobaltLog(2, "Skipping GitHub latest release check, too soon.")
                } else {
                    val username = localProperties.getNoThrows(PROPERTY_USERNAME, DOC_URL)
                    val accessToken = localProperties.getNoThrows(PROPERTY_ACCESS_TOKEN, DOC_URL)
                    try {
                        val req =
                                if (username != null && accessToken != null) {
                                    service.getReleases(username, "kobalt", accessToken)
                                } else {
                                    service.getReleasesNoAuth("cbeust", "kobalt")
                                }
                        val ex = req.execute()
                        val errorBody = ex.errorBody()
                        if (errorBody != null) {
                            val jsonError = JsonParser().parse(errorBody.string())
                            warn("Couldn't call Github.getReleases(): $jsonError")
                        } else {
                            val releases = ex.body()
                            if (releases != null) {
                                releases.firstOrNull()?.let {
                                    try {
                                        result = listOf(it.name, it.tagName).filterNotNull().first { !it.isBlank() }
                                    } catch(ex: NoSuchElementException) {
                                        throw KobaltException("Couldn't find the latest release")
                                    }
                                }
                            } else {
                                warn("Didn't receive any body in the response to GitHub.getReleases()")
                            }
                        }
                    } catch(e: Exception) {
                        kobaltLog(1, "Couldn't retrieve releases from github: " + e.message)
                        Exceptions.printStackTrace(e)
//                    val error = parseRetrofitError(e)
//                    val details = if (error.errors != null) {
//                        error.errors[0]
//                    } else {
//                        null
//                    }
//                    // TODO: If the credentials didn't work ("bad credentials"), should start again
//                    // using cbeust/kobalt, like above. Right now, just bailing.
//                    kobaltLog(2, "Couldn't retrieve releases from github, ${error.message ?: e}: "
//                            + details?.code + " field: " + details?.field)
                    }
                }
                result
            }

            return executors.miscExecutor.submit(callable)
        }
}