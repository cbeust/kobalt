package com.beust.kobalt.api

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import org.apache.maven.model.Model
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

open class Project(
        @Directive open var name: String = "",
        @Directive open var version: String? = null,
        @Directive open var directory: String = ".",
        @Directive open var buildDirectory: String = KFiles.KOBALT_BUILD_DIR,
        @Directive open var group: String? = null,
        @Directive open var artifactId: String? = null,
        @Directive open var packaging: String? = null,
        @Directive open var description : String = "",
        @Directive open var url: String? = null,
        @Directive open var pom: Model? = null,
        @Directive open var dependsOn: ArrayList<Project> = arrayListOf<Project>(),
        @Directive open var packageName: String? = group)
    : IBuildConfig, IDependencyHolder by DependencyHolder() {

    init {
        this.project = this
    }

    class ProjectExtra(project: Project) {
        var isDirty = false

        /**
         * @return true if any of the projects we depend on is dirty.
         */
        fun dependsOnDirtyProjects(project: Project) = project.dependsOn.any { it.projectExtra.isDirty }
    }

    /**
     * This field caches a bunch of things we don't want to recalculate all the time, such as the list of suffixes
     * found in this project.
     */
    val projectExtra = ProjectExtra(this)

    val testConfigs = arrayListOf<TestConfig>()

    // If one is specified by default, we only generateAndSave a BuildConfig, find a way to fix that
    override var buildConfig: BuildConfig? = null // BuildConfig()

    val projectProperties = ProjectProperties()

    override fun equals(other: Any?) = name == (other as Project).name
    override fun hashCode() = name.hashCode()

    companion object {
        val DEFAULT_SOURCE_DIRECTORIES = setOf("src/main/java", "src/main/kotlin", "src/main/resources")
        val DEFAULT_SOURCE_DIRECTORIES_TEST = setOf("src/test/java", "src/test/kotlin", "src/test/resources")
    }

    //
    // Directories
    //

    @Directive
    fun sourceDirectories(init: Sources.() -> Unit): Sources {
        return Sources(this, sourceDirectories).apply { init() }
    }

    var sourceDirectories = hashSetOf<String>().apply { addAll(DEFAULT_SOURCE_DIRECTORIES) }

    @Directive
    fun sourceDirectoriesTest(init: Sources.() -> Unit): Sources {
        return Sources(this, sourceDirectoriesTest).apply { init() }
    }

    var sourceDirectoriesTest = hashSetOf<String>().apply { addAll(DEFAULT_SOURCE_DIRECTORIES_TEST) }

    //
    // Dependencies
    //

    @Directive
    fun dependenciesTest(init: Dependencies.() -> Unit): Dependencies {
        dependencies = Dependencies(this, testDependencies, arrayListOf(),
                testProvidedDependencies, compileRuntimeDependencies, excludedDependencies, nativeDependencies)
        dependencies!!.init()
        return dependencies!!
    }

    val testDependencies: ArrayList<IClasspathDependency> = arrayListOf()
    val testProvidedDependencies: ArrayList<IClasspathDependency> = arrayListOf()

    /** Used to disambiguate various name properties */
    @Directive
    val projectName: String get() = name

    val productFlavors = hashMapOf<String, ProductFlavorConfig>()

    fun addProductFlavor(name: String, pf: ProductFlavorConfig) {
        productFlavors.put(name, pf)
    }

    var defaultConfig: BuildConfig? = null

    val buildTypes = hashMapOf<String, BuildTypeConfig>()

    fun addBuildType(name: String, bt: BuildTypeConfig) {
        buildTypes.put(name, bt)
    }

    fun classesDir(context: KobaltContext): String {
        val initial = KFiles.joinDir(buildDirectory, "classes")
        val result = context.pluginInfo.buildDirectoryInterceptors.fold(initial, { dir, intercept ->
            intercept.intercept(this, context, dir)
        })
        return result
    }

    override fun toString() = "[Project $name]"

    fun defaultConfig(init: BuildConfig.() -> Unit) = let { project ->
        BuildConfig().apply {
            init()
            project.defaultConfig = this
        }
    }

    @Directive
    fun buildType(name: String, init: BuildTypeConfig.() -> Unit) = BuildTypeConfig(name).apply {
        init()
        addBuildType(name, this)
    }


    @Directive
    fun productFlavor(name: String, init: ProductFlavorConfig.() -> Unit) = ProductFlavorConfig(name).apply {
        init()
        addProductFlavor(name, this)
    }
}
