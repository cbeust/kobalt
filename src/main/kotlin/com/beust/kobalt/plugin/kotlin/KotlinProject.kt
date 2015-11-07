package com.beust.kobalt.plugin.kotlin

import com.beust.kobalt.api.Dependencies
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.misc.toString

public class KotlinProject(
        @Directive
        override var name: String = "",
        @Directive
        override var version: String? = null,
        /** The absolute directory location of this project */
        @Directive
        override var directory: String = ".",
        /** The build directory, relative to the project directory */
        @Directive
        override var buildDirectory: String? = "kobaltBuild",
        @Directive
        override var group: String? = null,
        @Directive
        override var artifactId: String? = name,
        @Directive
        override var dependencies: Dependencies? = null,
        @Directive
        override var packaging: String? = null)
        : Project(name, version, directory, buildDirectory, group, artifactId, packaging, dependencies, ".kt",
                projectInfo = KotlinProjectInfo()) {

    override public fun toString() = toString("KotlinProject", "name", name)
}
