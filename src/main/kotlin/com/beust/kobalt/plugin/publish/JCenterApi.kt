package com.beust.kobalt.plugin.publish

import com.beust.klaxon.*
import com.beust.kobalt.api.Project
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.Gpg
import com.beust.kobalt.maven.Http
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.maven.Md5
import com.beust.kobalt.misc.*
import com.google.inject.assistedinject.Assisted
import com.squareup.okhttp.Response
import org.jetbrains.annotations.Nullable
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject

data class JCenterPackage(val jo: JsonObject) {
    @Suppress("UNCHECKED_CAST")
    val latestPublishedVersion = (jo.get("versions") as JsonArray<String>).get(0)
}

open public class UnauthenticatedJCenterApi @Inject constructor(open val http: Http){
    companion object {
        const val BINTRAY_URL_API = "https://api.bintray.com"
        const val BINTRAY_URL_API_CONTENT = BINTRAY_URL_API + "/content"
    }

    fun parseResponse(response: String) : JsonObject {
        return Parser().parse(ByteArrayInputStream(response.toByteArray(Charset.defaultCharset()))) as JsonObject
    }

    fun getPackage(name: String) : JCenterPackage {
        val url = arrayListOf(BINTRAY_URL_API, "packages", "cbeust", "maven", "kobalt").join("/")
        val response = http.get(url).getAsString()
        val result = parseResponse(response)
        return JCenterPackage(result)
    }

    val kobaltPackage : JCenterPackage
        get() = getPackage("kobalt")
}

public class JCenterApi @Inject constructor (@Nullable @Assisted("username") val username: String?,
        @Nullable @Assisted("password") val password: String?,
        override val http: Http, val gpg: Gpg) : UnauthenticatedJCenterApi(http) {

    interface IFactory {
        fun create(@Nullable @Assisted("username") username: String?,
                @Nullable @Assisted("password") password: String?) : JCenterApi
    }

    fun packageExists(packageName: String) : Boolean {
        val url = arrayListOf(UnauthenticatedJCenterApi.BINTRAY_URL_API, "packages", username!!, "maven", packageName)
                .join("/")
        val response = http.get(username, password, url).getAsString()
        val jo = parseResponse(response)

        return jo.string("name") == packageName
    }

    fun createPackage(packageName: String) : String {
        val url = arrayListOf(UnauthenticatedJCenterApi.BINTRAY_URL_API, "packages", username!!, "maven").join("/")
        val jo = json {
            obj("name" to packageName)
            obj("license" to array("Apache 2.0"))
        }
        return http.post(username, password, url, jo.toJsonString())
    }

    fun uploadMaven(project: Project, files: List<File>, configuration : JCenterConfiguration?) : TaskResult {
        if (! packageExists(project.name!!)) {
            throw KobaltException("Couldn't find a package called ${project.name} on bintray, please create one first" +
                    " as explained at https://bintray.com/docs/usermanual/uploads/uploads_creatinganewpackage.html")
        }

        val fileToPath: (File) -> String = { f: File ->
            arrayListOf(
                    UnauthenticatedJCenterApi.BINTRAY_URL_API_CONTENT,
                    username!!,
                    "maven",
                    project.name!!,
                    project.version!!,
                    project.group!!.replace(".", "/"),
                    project.artifactId!!,
                    project.version!!,
                    f.name)
                .join("/")
        }

        return upload(files, configuration, fileToPath, generateMd5 = true, generateAsc = true)
    }

    fun uploadFile(file: File, url: String, configuration: JCenterConfiguration, generateMd5: Boolean = false,
            generateAsc: Boolean = false) =
        upload(arrayListOf(file), configuration, {
                f: File -> "${UnauthenticatedJCenterApi.BINTRAY_URL_API_CONTENT}/$username/generic/$url"},
                generateMd5, generateAsc)

    private fun upload(files: List<File>, configuration : JCenterConfiguration?, fileToPath: (File) -> String,
            generateMd5: Boolean = false, generateAsc: Boolean) : TaskResult {
        val successes = arrayListOf<File>()
        val failures = hashMapOf<File, String>()
        val filesToUpload = arrayListOf<File>()

        if (generateAsc) {
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
        if (configuration?.publish == true) options.add("publish=1")

        //
        // TODO: These files should be uploaded from a thread pool instead of serially
        //
        log(1, "Found ${filesToUpload.size()} artifacts to upload")
        filesToUpload.forEach {
            var path = fileToPath(it)
            if (options.size() > 0) {
                path += "?" + options.join("&")
            }

            log(1, "  Uploading $it to $path")
            http.uploadFile(username, password, path, it,
                    { r: Response -> successes.add(it) },
                    { r: Response ->
                        val jo = parseResponse(r.body().string())
                        failures.put(it, jo.string("message") ?: "No message found")
                    })
        }

        val result: TaskResult
        if (successes.size() == filesToUpload.size()) {
            log(1, "All artifacts successfully uploaded")
            result = TaskResult(true)
        } else {
            result = TaskResult(false, failures.values().join(" "))
            error("Failed to upload ${failures.size()} files:")
            failures.forEach{ entry ->
                error(" - ${entry.key} : ${entry.value}")
            }
        }

        return result
    }
}
