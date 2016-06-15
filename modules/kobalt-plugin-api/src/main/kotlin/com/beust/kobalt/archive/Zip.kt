package com.beust.kobalt.archive

import com.beust.kobalt.Glob
import com.beust.kobalt.IFileSpec
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.misc.From
import com.beust.kobalt.misc.IncludedFile
import com.beust.kobalt.misc.To

open class Zip(open val project: Project, open var name: String = Archives.defaultArchiveName(project) + ".zip") {
    val excludes = arrayListOf<Glob>()

    @Directive
    fun from(s: String) = From(s)

    @Directive
    fun to(s: String) = To(s)

    @Directive
    fun exclude(vararg files: String) {
        files.forEach { excludes.add(Glob(it)) }
    }

    @Directive
    fun exclude(vararg specs: Glob) {
        specs.forEach { excludes.add(it) }
    }

    @Directive
    fun include(vararg files: String) {
        includedFiles.add(IncludedFile(files.map { IFileSpec.FileSpec(it) }))
    }

    @Directive
    fun include(from: From, to: To, vararg specs: String) {
        includedFiles.add(IncludedFile(from, to, specs.map { IFileSpec.FileSpec(it) }))
    }

    @Directive
    fun include(from: From, to: To, vararg specs: IFileSpec.GlobSpec) {
        includedFiles.add(IncludedFile(from, to, listOf(*specs)))
    }

    /**
     * Prefix path to be removed from the zip file. For example, if you add "build/lib/a.jar" to the zip
     * file and the excludePrefix is "build/lib", then "a.jar" will be added at the root of the zip file.
     */
    val includedFiles = arrayListOf<IncludedFile>()

}


