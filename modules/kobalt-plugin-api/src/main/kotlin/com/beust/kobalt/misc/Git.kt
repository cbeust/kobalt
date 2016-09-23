package com.beust.kobalt.misc

import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.Project
import com.google.inject.Inject
import java.io.File

class Git @Inject constructor() {
    fun maybeTagRelease(project: Project, uploadResult: TaskResult, autoGitTag: Boolean) : TaskResult {
        val result =
                if (uploadResult.success && autoGitTag) {
                    val tagSuccess = tagRelease(project)
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

    private fun tagRelease(project: Project) : Boolean {
        val success = try {
            log(2, "Tagging this release as \"${project.version}\"")
            val repo = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(File(KFiles.joinDir(project.directory, ".git")))
                    .readEnvironment()
                    .findGitDir()
                    .build()
            val git = org.eclipse.jgit.api.Git(repo)
            val ref = git.tag().setName(project.version).call()
            git.push().setPushTags().call()
            true
        } catch(ex: Exception) {
            warn("Couldn't create tag ${project.version}: ${ex.message}", ex)
            false
        }

        return success
    }
}
