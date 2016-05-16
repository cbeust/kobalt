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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.assistedinject.Assisted
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import retrofit2.http.Headers
import java.io.File
import javax.annotation.Nullable
import javax.inject.Inject

class BintrayResponse()

class BintrayApi @Inject constructor(val http: Http,
                                     @Nullable @Assisted("username") val username: String?,
                                     @Nullable @Assisted("password") val password: String?,
                                     @Nullable @Assisted("org") val org: String?,
                                     val gpg: Gpg, val executors: KobaltExecutors) {

    companion object {
        const val BINTRAY_URL_API = "https://api.bintray.com"
        const val BINTRAY_URL_API_CONTENT = BINTRAY_URL_API + "/content"
    }

    interface IFactory {
        fun create(@Nullable @Assisted("username") username: String?,
                   @Nullable @Assisted("password") password: String?,
                   @Nullable @Assisted("org") org: String?): BintrayApi
    }

    interface Api {
        @GET("/packages/{owner}/maven/{package}")
        fun getPackage(@Path("owner") owner: String,
                       @Path("package") name: String): Call<BintrayResponse>

        @POST("/packages/{owner}/maven")
        fun createPackage(@Path("owner") owner: String,
                          @Body content: JsonObject): Call<BintrayResponse>

        @Multipart
        @Headers("Content-Type: application/xml")
        @PUT("/content/{owner}/maven/{artifact}/{version}/{group}/{artifact}/{version}/{name}")
        fun uploadPom(@Path("owner") owner: String,
                      @Path("group", encoded = true) group: String,
                      @Path("artifact") artifact: String,
                      @Path("version") version: String,
                      @Path("name") name: String,
                      @Part file: MultipartBody.Part): Call<BintrayResponse>

        @Multipart
        @PUT("/content/{owner}/maven/{artifact}/{version}/{group}/{artifact}/{version}/{name}")
        fun uploadArtifact(@Path("owner") owner: String,
                           @Path("group", encoded = true) group: String,
                           @Path("artifact") artifact: String,
                           @Path("version") version: String,
                           @Path("name") name: String,
                           @Part file: MultipartBody.Part): Call<BintrayResponse>


    }

    private val service: Api

    init {
        val builder = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
        builder.interceptors().add(Interceptor { chain ->
            var original = chain.request()

            chain.proceed(original.newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .method(original.method(), original.body())
                    .build());
        })
        val okHttpClient = builder.build()

        service = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(BintrayApi.BINTRAY_URL_API)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Api::class.java)
    }

    fun validatePackage(project: Project) {
        val execute = service.getPackage(org ?: username!!, project.name).execute()

        if (execute.errorBody()?.string()?.contains("'${project.name}' was not found") ?: false) {
            warn("Package does not exist on bintray.  Creating now.")
            val result = service.createPackage(org ?: username!!, buildPackageInfo(project))
                    .execute()
            if (result.errorBody() != null) {
                throw KobaltException("Error while creating package:\n" + result.errorBody().string())
            }
        }
    }

    private fun buildPackageInfo(project: Project): JsonObject {
        val jsonObject = JsonObject()
        jsonObject.addNonNull("name", project.name)
        jsonObject.addNonNull("desc", project.description)
        jsonObject.addNonNull("vcs_url", project.scm?.url)
        jsonObject.addNonNull("website_url", project.url)
        val licenses = JsonArray()
        project.licenses.forEach {
            licenses.add(it.name)
        }
        jsonObject.add("licenses", licenses)
        return jsonObject
    }

    fun uploadMaven(project: Project, files: List<File>, config: BintrayConfig?): TaskResult {
        validatePackage(project)
        return upload(project, files, config, generateMd5 = true)
    }

    fun uploadFile(project: Project, file: File, config: BintrayConfig?, generateMd5: Boolean = false) =
            upload(project, arrayListOf(file), config, generateMd5)

    private fun upload(project: Project, files: List<File>, config: BintrayConfig?, generateMd5: Boolean = false): TaskResult {
        val filesToUpload = arrayListOf<File>()

        if (config != null && config.sign) {
            // Create the .asc files
            filesToUpload.addAll(gpg.runGpg(files))
        }
        files.forEach {
            filesToUpload.add(it)
            if (generateMd5) {
                // Create and upload the md5 for this file
                with(it) {
                    val md5: String = Md5.toMd5(this)
                    val md5File = File(path + ".md5")
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

        val fileCount = filesToUpload.size
        if (fileCount > 0) {
            log(1, "  Found $fileCount artifacts to upload")
            val errorMessages = arrayListOf<String>()

            fun dots(total: Int, list: List<Boolean>, file: File? = null): String {
                val spaces: String = Array(total - list.size, { " " }).joinToString("")
                return "|" + list.map { if (it) "." else "X" }.joinToString("") + spaces +
                        (if (file != null) "| [ $file ]" else "|")
            }

            val results = arrayListOf<Boolean>()
            filesToUpload.forEachIndexed { i, file ->
                val type = MediaType.parse("multipart/form-data")

                val body = MultipartBody.Part.createFormData("artifact", file.name, RequestBody.create(type, file));

                if (file.extension != "pom") {
                    val upload = service.uploadArtifact(org ?: username!!, // project.name,
                            project.group!!.replace('.', '/'), project.artifactId!!, project.version!!, file.name, body)
                    val result = upload.execute()
                    val error = result.errorBody()?.string()
                    if (result.errorBody() != null) {
                        errorMessages.add(error!!)
                        results.add(false)
                    } else {
                        results.add(true)
                    }
                } else {
//                    http.uploadFile(username, password, fileToPath(project, file) + optionPath,
//                            Http.TypedFile(com.google.common.net.MediaType.ANY_APPLICATION_TYPE.toString(), file),
//                            post = false, // Bintray requires PUT
//                            success = { r: Response -> results.add(true) },
//                            error = { r: Response ->
//                                results.add(false)
//                                val jcResponse = parseResponse(r)
//                                errorMessages.add(jcResponse.errorMessage!!)
//                            })
                    val upload = service.uploadPom(org ?: username!!,// project.name,
                            project.group!!.replace('.', '/'),
                            project.artifactId!!, project.version!!, file.name, body)
                    val result = upload.execute()
                    val error = result.errorBody()?.string()
                    if (result.errorBody() != null) {
                        errorMessages.add(error!!)
                        results.add(false)
                    } else {
                        results.add(true)
                    }
                }

                log(1, "    Uploading ${i + 1} / $fileCount " + dots(fileCount, results, file), false)
            }
            val success = results
                    .filter { it }
                    .count()
            log(1, "    Uploaded $success / $fileCount " + dots(fileCount, results), false)
            log(1, "", true)
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

    fun fileToPath(project: Project, f: File) : String {
        return listOf(
                BINTRAY_URL_API_CONTENT,
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

    fun JsonObject.addNonNull(name: String, value: String?) {
        if (value != null) {
            addProperty(name, value);
        }
    }

}
