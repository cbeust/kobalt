package com.beust.kobalt.misc

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.Project
import com.google.inject.Inject
import java.io.File

class Git @Inject constructor() {
    fun maybeTagRelease(project: Project, uploadResult: TaskResult, auto: Boolean, tag: String, message: String) : TaskResult {
        val result =
                if (uploadResult.success && auto) {
                    val tagSuccess = tagRelease(project, auto, tag, message)
                    if (! tagSuccess) {
                        TaskResult(false, "Couldn't tag the project")
                    } else {
                        TaskResult()
                    }
                } else {
                    TaskResult()
                }
        return result
    }

    private fun tagRelease(project: Project, auto: Boolean, tag: String, message: String) : Boolean {
        val version = if (tag.isNullOrBlank()) project.version else tag
        val success = try {
            log(2, "Tagging this release as \"$version\"")
            val repo = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(File(KFiles.joinDir(project.directory, ".git")))
                    .readEnvironment()
                    .findGitDir()
                    .build()
            val git = org.eclipse.jgit.api.Git(repo)
            val ref = git.tag().setName(version).setMessage(message).call()
            git.push().setPushTags().call()
            true
        } catch(ex: Exception) {
            warn("Couldn't create tag ${version}: ${ex.message}", ex)
            false
        }

        return success
    }
}
