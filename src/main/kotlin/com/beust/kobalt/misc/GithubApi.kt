package com.beust.kobalt.misc

import com.beust.kobalt.homeDir
import com.beust.kobalt.maven.KobaltException
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient
import retrofit.client.Response
import retrofit.http.*
import retrofit.mime.MimeUtil
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedFile
import rx.Observable
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Future
import javax.inject.Inject

/**
 * Retrieve Kobalt's latest release version from github.
 */
public class GithubApi @Inject constructor(val executors: KobaltExecutors) {
    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/cbeust/kobalt/releases"
    }

    fun uploadRelease(): Int {
        val releaseName = "0.100"
        val release = service.getReleaseByTagName(Prop.username, "kobalt", releaseName)

        uploadService.uploadRelease(Prop.username, Prop.accessToken, "kobalt", release.id!!, "zipFile", "label",
                TypedFile("text/plain", File(homeDir("kotlin", "kobalt", "kobalt", "src", "Build.kt"))))
                .subscribe(
                        { println("success") },
                        { e: Throwable ->
                            println("error" + (e as RetrofitError).response.bodyContent())
                            println("")
                        },
                        { println("complete") }
                )
        println("createRelease()")
        //                service.createRelease(username, accessToken, "kobalt",
        ////                        hashMapOf("tag_name" to "0.502tagName")
        //                        CreateRelease(releaseName)
        ////                        CreateRelease().apply { tag_name = "0.500tagName"}
        ////                        CreateRelease("0.500tagName",
        ////                        "master", "0.500name",
        ////                        "A test release",
        ////                        draft = false, prerelease = true)
        //                )
        //                    .map { response: CreateReleaseResponse? ->
        //                        uploadRelease(response?.id!!)
        //                        println("Received id " + response?.id)
        //                    }
        //                .subscribe(
        //                        { println("success") },
        //                        { e: Throwable ->
        //                            println("error" + (e as RetrofitError).response.bodyContent())
        //                        },
        //                        { println("complete")}
        //                )
        ////                    })
        Thread.sleep(10000)
        return 0
    }

    //
    // Read only Api
    //

    private val service = RestAdapter.Builder()
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .setClient(OkClient(OkHttpClient()))
            .setEndpoint("https://api.github.com")
            .build()
            .create(Api::class.java)

    class Release {
        var name: String? = null
        var prerelease: Boolean? = null
    }

    class CreateRelease(@SerializedName("tag_name") var tag_name: String? = null)
    class CreateReleaseResponse(var id: String? = null)
    class GetReleaseResponse(var id: String? = null,
            @SerializedName("upload_url") var uploadUrl: String? = null)

    interface Api {

        @GET("/repos/{owner}/{repo}/releases/tags/{tag}")
        fun getReleaseByTagName(@Path("owner") owner: String, @Path("repo") repo: String,
                @Path("tag") tagName: String): GetReleaseResponse

        @GET("/repos/{owner}/{repo}/releases")
        fun releases(@Path("owner") owner: String, @Path("repo") repo: String): List<Release>

        @POST("/repos/{owner}/{repo}/releases")
        fun createRelease(@Path("owner") owner: String,
                @Query("access_token") accessToken: String,
                @Path("repo") repo: String,
                @Body createRelease: CreateRelease
        ): Observable<CreateReleaseResponse>
    }

    //
    // Upload Api
    //

    val uploadService = RestAdapter.Builder()
            .setEndpoint("https://uploads.github.com/")
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .setClient(OkClient(OkHttpClient()))
            .build()
            .create(UploadApi::class.java)

    class UploadReleaseResponse(var id: String? = null)

    interface UploadApi {
        @POST("/repos/{owner}/{repo}/releases/{id}/assets")
        fun uploadRelease(@Path("owner") owner: String,
                @Query("access_token") accessToken: String,
                @Path("repo") repo: String,
                @Path("id") id: String,
                @Query("name") name: String,
                @Query("label") label: String,
                @Body file: TypedFile)
                //                        @Query("Content-Type") contentType: String = "text/plain")//"application/zip")
                : Observable<UploadReleaseResponse>
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
                    //                    val jo = Parser().parse(ins) as JsonArray<JsonObject>
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
            val result = localProperties.get(name)
                    ?: throw KobaltException("Couldn't find $name in local.properties")
            return result as String
        }

        val accessToken: String get() = fromProperties(ACCESS_TOKEN_PROPERTY)
        val username: String get() = fromProperties(USERNAME_PROPERTY)
    }
}
