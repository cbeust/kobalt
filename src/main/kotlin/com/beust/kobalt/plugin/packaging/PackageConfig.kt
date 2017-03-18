package com.beust.kobalt.plugin.packaging

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.archive.*
import com.beust.kobalt.glob
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.misc.KFiles
import java.io.File

class PackageConfig(val project: Project) : AttributeHolder {
    val jars = arrayListOf<Jar>()
    val wars = arrayListOf<War>()
    val zips = arrayListOf<Zip>()
    var generatePom: Boolean = false

    init {
        (Kobalt.findPlugin(PackagingPlugin.PLUGIN_NAME) as PackagingPlugin).addPackage(this)
    }

    @Directive
    fun jar(init: Jar.(p: Jar) -> Unit) = Jar(project).apply {
        init(this)
        jars.add(this)
    }

    @Directive
    fun zip(init: Zip.(p: Zip) -> Unit) = Zip(project).apply {
        init(this)
        zips.add(this)
    }

    @Directive
    fun war(init: War.(p: War) -> Unit) = War(project).apply {
        init(this)
        wars.add(this)
    }

    /**
     * Package all the jar files necessary for a maven repo: classes, sources, javadocs.
     */
    @Directive
    fun mavenJars(init: MavenJars.(p: MavenJars) -> Unit) : MavenJars {
        val m = MavenJars(this)
        m.init(m)

        val mainJar = jar {
            fatJar = m.fatJar
        }
        jar {
            name = "${project.name}-${project.version}-sources.jar"
            project.sourceDirectories.forEach {
                if (File(project.directory, it).exists()) {
                    include(from(it), to(""), glob("**"))
                }
            }
        }
        jar {
            name = "${project.name}-${project.version}-javadoc.jar"
            val fromDir = KFiles.joinDir(project.buildDirectory, JvmCompilerPlugin.DOCS_DIRECTORY)
            include(from(fromDir), to(""), glob("**"))
        }

        mainJarAttributes.forEach {
            mainJar.addAttribute(it.first, it.second)
        }

        generatePom = true

        return m
    }

    val mainJarAttributes = arrayListOf<Pair<String, String>>()

    override fun addAttribute(k: String, v: String) {
        mainJarAttributes.add(Pair(k, v))
    }

    class MavenJars(val ah: AttributeHolder, var fatJar: Boolean = false, var manifest: Manifest? = null) :
            AttributeHolder by ah {
        fun manifest(init: Manifest.(p: Manifest) -> Unit) : Manifest {
            val m = Manifest(this)
            m.init(m)
            return m
        }
    }
}