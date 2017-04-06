package com.beust.kobalt

import com.beust.kobalt.api.annotation.Directive
import java.io.File

/**
 * Base classes for directives that support install(from,to) (e.g. install{} or jar{}).
 */
open class IncludeFromTo {
    /**
     * Prefix path to be removed from the zip file. For example, if you add "build/lib/a.jar" to the zip
     * file and the excludePrefix is "build/lib", then "a.jar" will be added at the root of the zip file.
     */
    val includedFiles = arrayListOf<IncludedFile>()

    @Directive
    fun from(s: String) = From(s)

    @Directive
    fun to(s: String) = To(s)

    @Directive
    fun copy(from: From, to: To) {
        val dir = File(from.path).absoluteFile.parentFile
        includedFiles.add(IncludedFile(from(dir.absolutePath), to, listOf(IFileSpec.FileSpec(from.path))))
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
}

