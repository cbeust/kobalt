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
import com.google.inject.assistedinject.Assisted
import com.squareup.okhttp.Response
import org.jetbrains.annotations.Nullable
import retrofit.mime.TypedFile
import java.io.File
import javax.inject.Inject

data class JCenterPackage(val jo: JsonObject) {
//    @Suppress("UNCHECKED_CAST")
//    val latestPublishedVersion = (jo.get("versions") as JsonArray).get(0) as JsonObject).
}

open public class UnauthenticatedJCenterApi @Inject constructor(open val http: Http){
    companion object {
        const val BINTRAY_URL_API = "https://api.bintray.com"
        const val BINTRAY_URL_API_CONTENT = BINTRAY_URL_API + "/content"
    }

    class JCenterResponse(val jo: JsonObject?, val errorMessage: String?)

    fun parseResponse(r: Response) : JCenterResponse {
        val networkResponse = r.networkResponse()
        if (networkResponse.code() != 200) {
            val message = networkResponse.message()
            val errorObject = JsonParser().parse(r.body().string()).asJsonObject
            return JCenterResponse(null, message + ": " + errorObject.get("message").asString)
        } else {
            return JCenterResponse(JsonParser().parse(r.body().string()).asJsonObject, null)
        }
    }

//    fun getPackage() : JCenterPackage {
//        val url = arrayListOf(BINTRAY_URL_API, "packages", "cbeust", "maven", "kobalt").joinToString("/")
//        val response = http.get(url).getAsString()
//        val result = parseResponse(response)
//        return JCenterPackage(result)
//    }
}

public class JCenterApi @Inject constructor (@Nullable @Assisted("username") val username: String?,
        @Nullable @Assisted("password") val password: String?, @Nullable @Assisted("org") val org: String?,
        override val http: Http, val gpg: Gpg, val executors: KobaltExecutors) : UnauthenticatedJCenterApi(http) {

    interface IFactory {
        fun create(@Nullable @Assisted("username") username: String?,
                   @Nullable @Assisted("password") password: String?,
                   @Nullable @Assisted("org") org: String?) : JCenterApi
    }

    fun packageExists(packageName: String) : Boolean {
        val url = arrayListOf(UnauthenticatedJCenterApi.BINTRAY_URL_API, "packages", org ?: username!!, "maven", packageName)
                .joinToString("/")
        val jcResponse = parseResponse(http.get(username, password, url))

        if (jcResponse.errorMessage != null) {
            throw KobaltException("Error from JCenter: ${jcResponse.errorMessage}")
        }

        return jcResponse.jo!!.get("name").asString == packageName
    }

//    class ForPost(val name: String, val license: Array<String>)
//
//    fun createPackage(packageName: String) : String {
//        val url = arrayListOf(UnauthenticatedJCenterApi.BINTRAY_URL_API, "packages", username!!, "maven").join("/")
//        val jsonString = Gson().toJson(ForPost(packageName, arrayOf("Apache 2.0")))
//        return http.post(username, password, url, jsonString)
//    }

    fun uploadMaven(project: Project, files: List<File>, config: JCenterConfig?) : TaskResult {
        if (! packageExists(project.name)) {
            throw KobaltException("Couldn't find a package called ${project.name} on bintray, please create one first" +
                    " as explained at https://bintray.com/docs/usermanual/uploads/uploads_creatinganewpackage.html")
        }

        val fileToPath: (File) -> String = { f: File ->
            arrayListOf(
                    UnauthenticatedJCenterApi.BINTRAY_URL_API_CONTENT,
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

    fun uploadFile(file: File, url: String, config: JCenterConfig, generateMd5: Boolean = false) =
        upload(arrayListOf(file), config, {
                f: File -> "${UnauthenticatedJCenterApi.BINTRAY_URL_API_CONTENT}/${org ?: username}/generic/$url"},
                generateMd5)

    private fun upload(files: List<File>, config: JCenterConfig?, fileToPath: (File) -> String,
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
        // Uploads can'be done in parallel or JCenter rejects them
        //
        val fileCount = filesToUpload.size
        if (fileCount > 0) {
            log(1, "  Found $fileCount artifacts to upload: " + filesToUpload.get(0)
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
                        TypedFile(MediaType.ANY_APPLICATION_TYPE.toString(), file),
                        post = false, // JCenter requires PUT
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
