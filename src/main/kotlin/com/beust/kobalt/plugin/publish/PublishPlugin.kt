package com.beust.kobalt.plugin.publish

import com.beust.klaxon.string
import com.beust.kobalt.Plugins
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.Http
import com.beust.kobalt.maven.KobaltException
import com.beust.kobalt.misc.KobaltLogger
import com.google.common.base.Preconditions
import org.jetbrains.kotlin.utils.sure
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class PublishPlugin @Inject constructor(val http: Http, val files: com.beust.kobalt.misc.KFiles,
            val factory: com.beust.kobalt.maven.PomGenerator.IFactory,
            val jcenterFactory: JCenterApi.IFactory)
        : BasePlugin(), KobaltLogger {

    override val name = "publish"

    companion object {
        private const val TASK_UPLOAD_JCENTER = "uploadJcenter"
        private const val TASK_GENERATE_POM = "generatePom"

        private const val PROPERTY_BINTRAY_USER = "bintray.user"
        private const val PROPERTY_BINTRAY_PASSWORD = "bintray.apikey"
    }

    @Task(name = TASK_GENERATE_POM, description = "Generate the .pom file", runAfter = arrayOf("assemble"))
    fun taskGeneratePom(project: Project): TaskResult {
        factory.create(project).generate()
        return TaskResult()
    }

    private fun validateProject(project: Project) {
        Preconditions.checkNotNull(project.name, "Project ${project} should have a name")
        Preconditions.checkNotNull(project.version, "Project ${project} should have a version")
        Preconditions.checkNotNull(project.group, "Project ${project} should have a group")
        Preconditions.checkNotNull(project.artifactId, "Project ${project} should have a artifactId")
    }

    private val VALID = arrayListOf(".jar", ".pom")

    private fun findArtifactFiles(project: Project) : List<File> {
        val result = files.findRecursively(File(project.directory, project.buildDirectory)) { file ->
                VALID.any { file.endsWith(it)} and file.contains(project.version!!)
            }.map { it -> File(it) }
        log(1, "${project.name}: Found ${result.size()} artifacts to upload")
        return result
    }

    private fun checkAuthentication(value: String, key: String) {
        Preconditions.checkNotNull(value, "Couldn't find user in property ${key}, make sure you specified" +
                "your credentials in local.properties")
    }

    @Task(name = TASK_UPLOAD_JCENTER, description = "Upload the artifacts to JCenter",
            runAfter = arrayOf(TASK_GENERATE_POM))
    fun taskUploadJcenter(project: Project): TaskResult {
        val user = System.getProperty(PROPERTY_BINTRAY_USER)
        val password = System.getProperty(PROPERTY_BINTRAY_PASSWORD)
        checkAuthentication(user, PROPERTY_BINTRAY_USER)
        checkAuthentication(password, PROPERTY_BINTRAY_PASSWORD)

        validateProject(project)

        val jcenter = jcenterFactory.create(user, password)

        val configuration = configurations.get(project.name)

        //
        // Upload to Maven
        //
        val trMaven = jcenter.uploadMaven(project, findArtifactFiles(project), configuration)
        var success = trMaven.success
        val messages = arrayListOf<String>()
        if (! success) messages.add(trMaven.errorMessage!!)

        //
        // Upload individual files, if applicable
        //
        configuration?.let { conf : JCenterConfiguration ->
            conf.files.forEach {
                val taskResult = jcenter.uploadFile(File(project.directory, it.first), it.second /* url */,
                        conf)
                success = success and taskResult.success
                if (!taskResult.success) {
                    messages.add(taskResult.errorMessage!!)
                }
            }
        }
        return TaskResult(success, messages.join("\n  "))
    }

    /**
     * Map of project name -> JCenterConfiguration
     */
    private val configurations = hashMapOf<String, JCenterConfiguration>()
    fun addConfiguration(projectName: String, config: JCenterConfiguration) {
        configurations.put(projectName, config)
    }

}

data class JCenterConfiguration(val project: Project) {
    var publish: Boolean = false
    val files = arrayListOf<Pair<String, String>>()

    @Directive
    public fun file(filePath: String, url: String) {
        files.add(Pair(filePath, url))
    }
}

@Directive
public fun jcenter(project: Project, ini: JCenterConfiguration.() -> Unit)
        : JCenterConfiguration {
    val pd = JCenterConfiguration(project)
    pd.ini()
    (Plugins.getPlugin("publish") as PublishPlugin).addConfiguration(project.name!!, pd)
    return pd
}
