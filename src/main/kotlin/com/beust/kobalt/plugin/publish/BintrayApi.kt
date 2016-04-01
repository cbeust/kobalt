package com.beust.kobalt.plugin.publish

import com.beust.kobalt.KobaltException
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.Gpg
import com.beust.kobalt.maven.Http
import com.beust.kobalt.maven.Md5
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.error
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.common.net.MediaType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.inject.assistedinject.Assisted
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.File
import javax.annotation.Nullable
import javax.inject.Inject

data class BintrayPackage(val jo: JsonObject) {
//    @Suppress("UNCHECKED_CAST")
//    val latestPublishedVersion = (jo.get("versions") as JsonArray).get(0) as JsonObject).
}

open public class UnauthenticatedBintrayApi @Inject constructor(open val http: Http) {
    companion object {
        const val BINTRAY_URL_API = "https://api.bintray.com"
        const val BINTRAY_URL_API_CONTENT = BINTRAY_URL_API + "/content"
    }

    class BintrayResponse(val jo: JsonObject?, val errorMessage: String?)

    fun parseResponse(r: Response): BintrayResponse {
        val networkResponse = r.networkResponse()
        if (networkResponse.code() != 200) {
            val message = networkResponse.message()
            try {
                val errorObject = JsonParser().parse(r.body().string()).asJsonObject
                return BintrayResponse(null, message + ": " + errorObject.get("message").asString)
            } catch(ex: Exception) {
                return BintrayResponse(null, message)
            }
        } else {
            return BintrayResponse(JsonParser().parse(r.body().string()).asJsonObject, null)
        }
    }
}

class BintrayApi @Inject constructor (
        @Nullable @Assisted("username") val username: String?,
        @Nullable @Assisted("password") val password: String?,
        @Nullable @Assisted("org") val org: String?,
        override val http: Http, val gpg: Gpg, val executors: KobaltExecutors) : UnauthenticatedBintrayApi(http) {

    interface IFactory {
        fun create(@Nullable @Assisted("username") username: String?,
                   @Nullable @Assisted("password") password: String?,
                   @Nullable @Assisted("org") org: String?) : BintrayApi
    }

    class ReleaseResponse(var id: String? = null, @SerializedName("upload_url") var uploadUrl: String?)

    interface Api {
        @GET("/packages/{owner}/maven/{package}")
        fun getPackage(@Path("owner") owner: String,
                       @Path("package") name: String): Call<BintrayResponse>

        @POST("/packages/{owner}/maven/{package}")
        fun createPackage(@Path("owner") owner: String,
                          @Path("package") name: String,
                          @Body content: String): Call<BintrayResponse>

/*
        @GET("/repos/{owner}/{repo}/releases")
        fun getReleases(@Path("owner") owner: String,
                @Path("repo") repo: String,
                @Query("access_token") accessToken: String): Call<List<ReleasesResponse>>

        @GET("/repos/{owner}/{repo}/releases")
        fun getReleasesNoAuth(@Path("owner") owner: String,
                @Path("repo") repo: String): Call<List<ReleasesResponse>>
*/
    }

    private val service: Api

    init {
        val builder = OkHttpClient.Builder()
        builder.interceptors().add(Interceptor { chain ->
            var original = chain.request();

            var requestBuilder = original.newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .header("Accept", "application/json")
                    .method(original.method(), original.body());

            chain.proceed(requestBuilder.build());
        })
        val okHttpClient = builder.build()

        service = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(UnauthenticatedBintrayApi.BINTRAY_URL_API)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Api::class.java)
    }

    fun packageExists(project: Project) : Boolean {
        val url = arrayListOf(UnauthenticatedBintrayApi.BINTRAY_URL_API, "packages", org ?: username!!,
                "maven", project.name)
            .joinToString("/")
        val jcResponse = parseResponse(http.get(username, password, url))
        val execute = service.getPackage(org ?: username!!, project.name).execute()

        if (execute.errorBody()?.string()?.contains("was not found") ?: false) {
            warn("Package does not exist on bintray.  Creating now.")
            val content = mapOf(
                    "desc" to project.description,
                    "vcs_url" to (project.scm?.url ?: ""),
                    "licences" to """[ "Apache-2.0" ]""",
                    "website_url" to (project.url ?: "")
            ).toString()
            val result = service.createPackage(org ?: username!!, project.name, content).execute()
            if (result.errorBody() != null) {
                error(" Errors while creating package:\n" + result.errorBody().string())
                return false
            }
        }

        return jcResponse.jo!!.get("name").asString == project.name
    }

    fun uploadMaven(project: Project, files: List<File>, config: BintrayConfig?) : TaskResult {
        if (! packageExists(project)) {
            throw KobaltException("Couldn't find a package called ${project.name} on bintray, please create one first" +
                    " as explained at https://bintray.com/docs/usermanual/uploads/uploads_creatinganewpackage.html")
        }

        val fileToPath: (File) -> String = { f: File ->
            arrayListOf(
                    UnauthenticatedBintrayApi.BINTRAY_URL_API_CONTENT,
                    org ?: username!!,
                    "maven",
                    project.name,
                    project.version!!,
                    project.group!!.replace(".", "/"),
                    project.artifactId!!,
                    project.version!!,
                    f.name)
                    .joinToString("/")
        }

        return upload(files, config, fileToPath, generateMd5 = true)
    }

    fun uploadFile(file: File, url: String, config: BintrayConfig?, generateMd5: Boolean = false) =
        upload(arrayListOf(file), config, {
                f: File -> "${UnauthenticatedBintrayApi.BINTRAY_URL_API_CONTENT}/${org ?: username}/generic/$url"},
                generateMd5)

    private fun upload(files: List<File>, config: BintrayConfig?, fileToPath: (File) -> String,
            generateMd5: Boolean = false) : TaskResult {
        val filesToUpload = arrayListOf<File>()

        if (config != null && config.sign) {
            // Create the .asc files
            filesToUpload.addAll(gpg.runGpg(files))
        }
        files.forEach {
            filesToUpload.add(it)
            if (generateMd5) {
                // Create and upload the md5 for this file
                with(File(it.absolutePath)) {
                    val md5: String = Md5.toMd5(this)
                    val md5File = File(absolutePath + ".md5")
                    md5File.writeText(md5)
                    filesToUpload.add(md5File)
                }
            }
        }

        //
        // If any configuration was given, apply them so the URL reflects them, e.g. ?publish=1
        //
        val options = arrayListOf<String>()
        if (config?.publish == true) options.add("publish=1")

        val optionPath = StringBuffer()
        if (options.size > 0) {
            optionPath.append("?" + options.joinToString("&"))
        }

        //
        // Uploads can'be done in parallel or Bintray rejects them
        //
        val fileCount = filesToUpload.size
        if (fileCount > 0) {
            log(1, "  Found $fileCount artifacts to upload: " + filesToUpload[0]
                    + if (fileCount > 1) "..." else "")
            var i = 1
            val errorMessages = arrayListOf<String>()


            fun dots(total: Int, list: List<Boolean>) : String {
                val spaces : String = Array(total - list.size, { " " }).joinToString("")
                return "|" + list.map { if (it) "." else "X" }.joinToString("") + spaces + "|"
            }

            val results = arrayListOf<Boolean>()
            filesToUpload.forEach { file ->
                http.uploadFile(username, password, fileToPath(file) + optionPath,
                        Http.TypedFile(MediaType.ANY_APPLICATION_TYPE.toString(), file),
                        post = false, // Bintray requires PUT
                        success = { r: Response -> results.add(true) },
                        error = { r: Response ->
                            results.add(false)
                            val jcResponse = parseResponse(r)
                            errorMessages.add(jcResponse.errorMessage!!)
                        })
                val end = if (i >= fileCount) "\n" else ""
                log(1, "    Uploading " + (i++) + " / $fileCount " + dots(fileCount, results) + end, false)
            }
            if (errorMessages.isEmpty()) {
                return TaskResult()
            } else {
                error(" Errors while uploading:\n" + errorMessages.map { "    $it" }.joinToString("\n"))
                return TaskResult(false, errorMessages.joinToString("\n"))
            }
        } else {
            warn("Found no artifacts to upload")
            return TaskResult()
        }
    }
}
