package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.archive.Zip
import com.beust.kobalt.misc.IncludedFile

interface ArchiveFileFinder {
    fun findIncludedFiles(project: Project, context: KobaltContext, zip: Zip) : List<IncludedFile>
}

