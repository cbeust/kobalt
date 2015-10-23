package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.MavenDependency
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.misc.KFiles
import java.util.ArrayList

open public class Project(
        @Directive open var name: String? = null,
        @Directive open var version: String? = null,
        @Directive open var directory: String = ".",
        @Directive open var buildDirectory: String? = KFiles.KOBALT_BUILD_DIR,
        @Directive open var group: String? = null,
        @Directive open var artifactId: String? = null,
        @Directive open var dependencies: Dependencies? = null,
        @Directive open var sourceSuffix : String = "",
        @Directive open var compilerInfo : ICompilerInfo,
        @Directive open var description : String = "",
        @Directive open var scm : Scm? = null,
        @Directive open var url: String? = null,
        @Directive open var licenses: List<License> = arrayListOf<License>()) {

    var testArgs: ArrayList<String> = arrayListOf()

    override fun equals(other: Any?): Boolean {
        return name == (other as Project).name
    }

    override fun hashCode(): Int {
        return name!!.hashCode()
    }

    //
    // Directories
    //
    @Directive
    public fun sourceDirectories(init: Sources.() -> Unit) : Sources {
        val sources = Sources(this, sourceDirectories)
        sources.init()
        return sources
    }

    var sourceDirectories : ArrayList<String> = arrayListOf()
        get() = if (field.isEmpty()) compilerInfo.defaultSourceDirectories else field
        set(value) {
            field = value
        }

    @Directive
    public fun sourceDirectoriesTest(init: Sources.() -> Unit) : Sources {
        val sources = Sources(this, sourceDirectoriesTest)
        sources.init()
        return sources
    }

    var sourceDirectoriesTest : ArrayList<String> = arrayListOf()
        get() = if (field.isEmpty()) compilerInfo.defaultTestDirectories
                else field
        set(value) {
            field = value
        }

    //
    // Dependencies
    //

    @Directive
    public fun dependencies(init: Dependencies.() -> Unit) : Dependencies {
        dependencies = Dependencies(this, compileDependencies, compileProvidedDependencies, compileRuntimeDependencies)
        dependencies!!.init()
        return dependencies!!
    }

    val compileDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val compileProvidedDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val compileRuntimeDependencies : ArrayList<IClasspathDependency> = arrayListOf()

    @Directive
    public fun dependenciesTest(init: Dependencies.() -> Unit) : Dependencies {
        dependencies = Dependencies(this, testDependencies, testProvidedDependencies, compileRuntimeDependencies)
        dependencies!!.init()
        return dependencies!!
    }

    val testDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val testProvidedDependencies : ArrayList<IClasspathDependency> = arrayListOf()
}

public class Sources(val project: Project, val sources: ArrayList<String>) {
    @Directive
    public fun path(vararg paths: String) {
        sources.addAll(paths)
    }
}

public class Dependencies(val project: Project, val dependencies: ArrayList<IClasspathDependency>,
        val providedDependencies: ArrayList<IClasspathDependency>,
        val runtimeDependencies: ArrayList<IClasspathDependency>) {
    @Directive
    fun compile(vararg dep: String) {
        dep.forEach { dependencies.add(MavenDependency.create(it)) }
    }

    @Directive
    fun provided(vararg dep: String) {
        dep.forEach { providedDependencies.add(MavenDependency.create(it))}
    }

    @Directive
    fun runtime(vararg dep: String) {
        dep.forEach { runtimeDependencies.add(MavenDependency.create(it))}
    }
}

public class Scm(val connection: String, val developerConnection: String, val url: String)

public class License(val name: String, val url: String) {
    fun toMavenLicense() : org.apache.maven.model.License {
        val result = org.apache.maven.model.License()
        result.name = name
        result.url = url
        return result
    }

}