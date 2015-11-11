package com.beust.kobalt.misc

import com.beust.kobalt.homeDir
import com.beust.kobalt.maven.KobaltException
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.http.*
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
        const val HOST =
                "https://api.github.com"
//                "https://developer.github.com/v3/"
        const val RELEASES_URL = "$HOST/repos/cbeust/kobalt/releases"
    }

    public class ServiceGenerator {
        companion object {
            val API_BASE_URL = HOST

            val httpClient = OkHttpClient()
            val builder = RestAdapter.Builder()
                    .setEndpoint(API_BASE_URL)
//                    .baseUrl(API_BASE_URL)
//                    .addConverterFactory(GsonConverterFactory.create())

            class Contributor {
                var login: String? = null
                var contributions: Int? = null
            }

            class Release {
                var name: String? = null
                var prerelease: Boolean? = null
            }

            class UploadReleaseResponse(var id: String? = null)

            class CreateRelease(@SerializedName("tag_name") var tag_name: String? = null)
//            class CreateRelease(
//                    @Query("tag_name") tag_name: String,
//                    @Query("target_commitish") target: String,
//                    @Query("name") name: String,
//                    @Query("body") body: String,
//                    @Query("draft") draft : Boolean,
//                    @Query("prerelease") prerelease: Boolean
//            )
            class CreateReleaseResponse(var id: String? = null)

            interface Api {
                @GET("/repos/{owner}/{repo}/contributors")
                fun contributors(@Path("owner") owner: String, @Path("repo") repo: String): List<Contributor>

                @GET("/repos/{owner}/{repo}/releases")
                fun releases(@Path("owner") owner: String, @Path("repo") repo: String): List<Release>

                @POST("/repos/{owner}/{repo}/releases")
                fun createRelease(@Path("owner") owner: String,
                        @Query("access_token") accessToken: String,
                        @Path("repo") repo: String,
                        @Body createRelease: CreateRelease
                ) : Observable<CreateReleaseResponse>

                @POST("/repos/{owner}/{repo}/releases/{id}/assets")
                fun uploadRelease(@Path("owner") owner: String,
                        @Query("access_token") accessToken: String,
                        @Path("repo") repo: String,
                        @Path("id") id: String,
                        @Query("name") name: String,
                        @Query("label") label: String,
                        @Body file: File,
                        @Query("Content-Type") contentType: String = "application/zip")
                    : Observable<UploadReleaseResponse>

            }

            val service : Api by lazy { ServiceGenerator.createService(Api::class.java) }

            fun <S> createService(serviceClass: Class<S>): S {
                val retrofit = builder
                        .setEndpoint(HOST)
                        .setLogLevel(RestAdapter.LogLevel.FULL)
                        .setClient(OkClient(OkHttpClient()))

                //                        .setClient(httpClient)
                        .build()
                return retrofit.create(serviceClass)
            }

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

            fun uploadRelease() : Int {
                println("createRelease()")
                service.createRelease(username, accessToken, "kobalt",
//                        hashMapOf("tag_name" to "0.502tagName")
                        CreateRelease("0.503tagName")
//                        CreateRelease().apply { tag_name = "0.500tagName"}
//                        CreateRelease("0.500tagName",
//                        "master", "0.500name",
//                        "A test release",
//                        draft = false, prerelease = true)
                )
                    .map { response: CreateReleaseResponse? ->
                        uploadRelease(response?.id!!)
                        println("Received id " + response?.id)
                    }
                .subscribe(
                        { println("success") },
                        { e: Throwable -> println("error" + e)},
                        { println("complete")}
                )
//                    })
                Thread.sleep(10000)
                return 0
            }

            fun uploadRelease(id: String) {
                service.uploadRelease(username, accessToken, "kobalt", id, "The zip file", "The label",
                        File(homeDir("kotlin", "kobalt", "src", "Build.kt")))
            }
        }
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
                        if (versionName == null) {
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