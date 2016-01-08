package com.beust.kobalt.plugin.publish

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.DocUrl
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.misc.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
@Singleton
public class PublishPlugin @Inject constructor(val files: KFiles, val factory: PomGenerator.IFactory,
            val bintrayFactory: BintrayApi.IFactory, val github: GithubApi, val localProperties: LocalProperties)
        : BasePlugin() {

    override val name = PLUGIN_NAME

    companion object {
        const val PLUGIN_NAME = "Publish"
        private const val TASK_UPLOAD_BINTRAY = "uploadBintray"
        private const val TASK_UPLOAD_GITHUB = "uploadGithub"
        private const val TASK_GENERATE_POM = "generatePom"

        private const val PROPERTY_BINTRAY_USER = "bintray.user"
        private const val PROPERTY_BINTRAY_PASSWORD = "bintray.apikey"
        private const val PROPERTY_BINTRAY_ORG = "bintray.organization"
    }

    @Suppress("UNUSED_FUNCTION_LITERAL")
    @Task(name = TASK_GENERATE_POM, description = "Generate the .pom file", runAfter = arrayOf("assemble"))
    fun taskGeneratePom(project: Project): TaskResult {
        factory.create(project).generate()
        return TaskResult()
    }

    private fun validateProject(project: Project) {
        requireNotNull(project.name, { "Project $project should have a name" })
        requireNotNull(project.version, { "Project $project should have a version" })
        requireNotNull(project.group, { "Project $project should have a group" })
        requireNotNull(project.artifactId, { "Project $project should have a artifactId" })
    }

    private val VALID = arrayListOf(".jar", ".pom")

    private fun findArtifactFiles(project: Project) : List<File> {
        val result = files.findRecursively(File(project.directory, project.buildDirectory)) { file ->
                VALID.any { file.endsWith(it)} and file.contains(project.version!!)
            }.map { it -> File(it) }
        return result
    }

    @Task(name = TASK_UPLOAD_BINTRAY, description = "Upload files to Bintray",
            runAfter = arrayOf(TASK_GENERATE_POM))
    fun taskUploadBintray(project: Project): TaskResult {
        validateProject(project)
        return uploadBintray(project)
    }

    private fun uploadBintray(project: Project) : TaskResult {
        val docUrl = DocUrl.PUBLISH_PLUGIN_URL
        val user = localProperties.get(PROPERTY_BINTRAY_USER, docUrl)
        val password = localProperties.get(PROPERTY_BINTRAY_PASSWORD, docUrl)
        val org = localProperties.getNoThrows(PROPERTY_BINTRAY_ORG, docUrl)

        val jcenter = bintrayFactory.create(user, password, org)
        var success = false
        val configuration = bintrayConfigurations[project.name]
        val messages = arrayListOf<String>()

        if (configuration != null) {
            //
            // Upload to Maven
            //
            val trMaven = jcenter.uploadMaven(project, findArtifactFiles(project), configuration)
            success = trMaven.success
            if (! success) messages.add(trMaven.errorMessage!!)

            //
            // Upload individual files, if applicable
            //
            configuration.files.forEach {
                val taskResult = jcenter.uploadFile(File(project.directory, it.first), it.second /* url */,
                        configuration)
                success = success and taskResult.success
                if (!taskResult.success) {
                    messages.add(taskResult.errorMessage!!)
                }
            }
        } else {
            log(2, "Couldn't find any jcenter{} configuration, not uploading anything")
            success = true
        }

        return TaskResult(success, messages.joinToString("\n  "))
    }

    @Task(name = TASK_UPLOAD_GITHUB, description = "Upload files to Github",
            runAfter = arrayOf(TASK_GENERATE_POM))
    fun taskUploadGithub(project: Project): TaskResult {
        validateProject(project)
        return uploadGithub(project)
    }

    private fun uploadGithub(project: Project) : TaskResult {
        val configuration = githubConfigurations[project.name]

        //
        // Upload individual files, if applicable
        //
        if (configuration != null) {
            configuration.files.forEach {
                log(2, "Uploading $it tag: ${project.version}")
                github.uploadRelease(project.name, project.version!!, it)
            }
        } else {
            warn("Couldn't find any github{} configuration, not uploading anything")
        }

        return TaskResult()
    }

    /**
     * Map of project name -> BintrayConfig
     */
    private val bintrayConfigurations = hashMapOf<String, BintrayConfig>()
    fun addBintrayConfiguration(projectName: String, config: BintrayConfig) {
        bintrayConfigurations.put(projectName, config)
    }

    /**
     * Map of project name -> GithubConfiguration
     */
    private val githubConfigurations = hashMapOf<String, GithubConfig>()
    fun addGithubConfiguration(projectName: String, config: GithubConfig) {
        githubConfigurations.put(projectName, config)
    }
}

data class GithubConfig(val project: Project) {
    val files = arrayListOf<File>()

    @Directive
    public fun file(filePath: String, url: String) {
        files.add(File(filePath))
    }
}

@Directive
public fun Project.github(init: GithubConfig.() -> Unit) {
    with(GithubConfig(this)) {
        init()
        (Kobalt.findPlugin(PublishPlugin.PLUGIN_NAME) as PublishPlugin).addGithubConfiguration(name, this)
    }
}

data class BintrayConfig(val project: Project) {
    /**
     * If true, the uploaded file will be published in your personal space (e.g. https://dl.bintray.com/cbeust/maven).
     * Once the file is uploaded there, it can be automatically synchronized to JCenter by linking your project to
     * Bintray. By default, files are not published.
     */
    @Directive
    var publish: Boolean = false

    /**
     * If true, sign the files with GPG. This is only required if you plan to later synchronize these files
     * from Bintray to Maven Central. Keep this to false if you are only interested in uploading to JCenter.
     */
    @Directive
    var sign: Boolean = false

    val files = arrayListOf<Pair<String, String>>()

    @Directive
    public fun file(filePath: String, url: String) {
        files.add(Pair(filePath, url))
    }
}

@Directive
public fun Project.bintray(init: BintrayConfig.() -> Unit) {
    with(BintrayConfig(this)) {
        init()
        (Kobalt.findPlugin(PublishPlugin.PLUGIN_NAME) as PublishPlugin).addBintrayConfiguration(name, this)
    }
}
