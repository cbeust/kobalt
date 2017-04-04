package com.beust.kobalt.archive

import com.beust.kobalt.*
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive

open class Zip(open val project: Project, open var name: String = Archives.defaultArchiveName(project) + ".zip",
        open var fatJar: Boolean = false): AttributeHolder, IncludeFromTo()  {
    val excludes = arrayListOf<Glob>()

    @Directive
    fun exclude(vararg files: String) {
        files.forEach { excludes.add(Glob(it)) }
    }

    @Directive
    fun exclude(vararg specs: Glob) {
        specs.forEach { excludes.add(it) }
    }

    @Directive
    open val attributes = arrayListOf(Pair("Manifest-Version", "1.0"))

    override fun addAttribute(k: String, v: String) {
        attributes.add(Pair(k, v))
    }
}
