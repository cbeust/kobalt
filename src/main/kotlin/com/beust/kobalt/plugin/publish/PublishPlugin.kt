package com.beust.kobalt.plugin.publish

import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.internal.TaskResult
import com.beust.kobalt.maven.PomGenerator
import com.beust.kobalt.misc.GithubApi
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.google.common.base.Preconditions
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class PublishPlugin @Inject constructor(val files: KFiles, val factory: PomGenerator.IFactory,
            val jcenterFactory: JCenterApi.IFactory, val github: GithubApi)
        : BasePlugin() {

    override val name = "publish"

    companion object {
        private const val TASK_UPLOAD_JCENTER = "uploadJcenter"
        private const val TASK_UPLOAD_GITHUB = "uploadGithub"
        private const val TASK_GENERATE_POM = "generatePom"

        private const val PROPERTY_BINTRAY_USER = "bintray.user"
        private const val PROPERTY_BINTRAY_PASSWORD = "bintray.apikey"
    }

    @Suppress("UNUSED_FUNCTION_LITERAL")
    @Task(name = TASK_GENERATE_POM, description = "Generate the .pom file", runAfter = arrayOf("assemble"))
    fun taskGeneratePom(project: Project): TaskResult {
        factory.create(project).generate()
        return TaskResult()
    }

    private fun validateProject(project: Project) {
        Preconditions.checkNotNull(project.name, "Project $project should have a name")
        Preconditions.checkNotNull(project.version, "Project $project should have a version")
        Preconditions.checkNotNull(project.group, "Project $project should have a group")
        Preconditions.checkNotNull(project.artifactId, "Project $project should have a artifactId")
    }

    private val VALID = arrayListOf(".jar", ".pom")

    private fun findArtifactFiles(project: Project) : List<File> {
        val result = files.findRecursively(File(project.directory, project.buildDirectory)) { file ->
                VALID.any { file.endsWith(it)} and file.contains(project.version!!)
            }.map { it -> File(it) }
        return result
    }

    private fun checkAuthentication(value: String, key: String) {
        Preconditions.checkNotNull(value, "Couldn't find user in property $key, make sure you specified" +
                "your credentials in local.properties")
    }

    data class UserData(val user: String, val password: String)

    private fun checkCredentials(project: Project) : UserData {
        val user = System.getProperty(PROPERTY_BINTRAY_USER)
        val password = System.getProperty(PROPERTY_BINTRAY_PASSWORD)
        checkAuthentication(user, PROPERTY_BINTRAY_USER)
        checkAuthentication(password, PROPERTY_BINTRAY_PASSWORD)

        validateProject(project)
        return UserData(user, password)
    }

    @Task(name = TASK_UPLOAD_JCENTER, description = "Upload the artifacts to JCenter",
            runAfter = arrayOf(TASK_GENERATE_POM))
    fun taskUploadJcenter(project: Project): TaskResult {
        checkCredentials(project).let {
            return uploadJcenter(project, it.user, it.password)
        }
    }

    @Task(name = TASK_UPLOAD_GITHUB, description = "Upload the release to Github",
            runAfter = arrayOf(TASK_GENERATE_POM))
    fun taskUploadGithub(project: Project): TaskResult {
        checkCredentials(project).let {
            return uploadGithub(project)
        }
    }

    private fun uploadGithub(project: Project) : TaskResult {
        val configuration = githubConfigurations.getRaw(project.name)

        //
        // Upload individual files, if applicable
        //
        configuration?.let { conf : GithubConfig ->
            conf.files.forEach {
                log(2, "Uploading $it tag: ${project.version}")
                github.uploadRelease(project.name, project.version!!, it)
            }
        }
        return TaskResult()
    }

    private fun uploadJcenter(project: Project, user: String?, password: String?) : TaskResult {
        val jcenter = jcenterFactory.create(user, password)

        val configuration = jcenterConfigurations.getRaw(project.name)

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
        configuration?.let { conf : JCenterConfig ->
            conf.files.forEach {
                val taskResult = jcenter.uploadFile(File(project.directory, it.first), it.second /* url */, conf)
                success = success and taskResult.success
                if (!taskResult.success) {
                    messages.add(taskResult.errorMessage!!)
                }
            }
        }
        return TaskResult(success, messages.joinToString("\n  "))
    }

    /**
     * Map of project name -> JCenterConfiguration
     */
    private val jcenterConfigurations = hashMapOf<String, JCenterConfig>()
    fun addJCenterConfiguration(projectName: String, config: JCenterConfig) {
        jcenterConfigurations.put(projectName, config)
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
    var publish: Boolean = false
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
        (Kobalt.findPlugin("publish") as PublishPlugin).addGithubConfiguration(name, this)
    }
}

data class JCenterConfig(val project: Project) {
    var publish: Boolean = false
    val files = arrayListOf<Pair<String, String>>()

    @Directive
    public fun file(filePath: String, url: String) {
        files.add(Pair(filePath, url))
    }
}

@Directive
public fun Project.jcenter(init: JCenterConfig.() -> Unit) {
    with(JCenterConfig(this)) {
        init()
        (Kobalt.findPlugin("publish") as PublishPlugin).addJCenterConfiguration(name, this)
    }
}
