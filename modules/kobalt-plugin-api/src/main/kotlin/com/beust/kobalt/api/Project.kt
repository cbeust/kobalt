package com.beust.kobalt.api

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.JvmCompilerPlugin
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import java.io.File
import java.util.*

open class Project(
        @Directive open var name: String = "",
        @Directive open var version: String? = null,
        @Directive open var directory: String = ".",
        @Directive open var buildDirectory: String = KFiles.KOBALT_BUILD_DIR,
        @Directive open var group: String? = null,
        @Directive open var artifactId: String? = null,
        @Directive open var packaging: String? = null,
        @Directive open var dependencies: Dependencies? = null,
        @Directive open var description : String = "",
        @Directive open var scm : Scm? = null,
        @Directive open var url: String? = null,
        @Directive open var licenses: List<License> = arrayListOf<License>(),
        @Directive open var packageName: String? = group) : IBuildConfig {

    class ProjectExtra(project: Project) {
        val dependsOn = arrayListOf<Project>()

        var isDirty = false

        /**
         * @return true if any of the projects we depend on is dirty.
         */
        fun dependsOnDirtyProjects(project: Project) = project.projectExtra.dependsOn.any { it.projectExtra.isDirty }
    }

    /**
     * This field caches a bunch of things we don't want to recalculate all the time, such as the list of suffixes
     * found in this project.
     */
    val projectExtra = ProjectExtra(this)

    val testConfigs = arrayListOf<TestConfig>()

    // If one is specified by default, we only generate a BuildConfig, find a way to fix that
    override var buildConfig : BuildConfig? = null // BuildConfig()

    val projectProperties = ProjectProperties()

    override fun equals(other: Any?) = name == (other as Project).name
    override fun hashCode() = name.hashCode()

    /** Can be used by plug-ins */
    val dependentProjects : List<ProjectDescription>
            get() = projectProperties.get(JvmCompilerPlugin.DEPENDENT_PROJECTS) as List<ProjectDescription>

    companion object {
        val DEFAULT_SOURCE_DIRECTORIES = setOf("src/main/java", "src/main/kotlin", "src/main/resources")
        val DEFAULT_SOURCE_DIRECTORIES_TEST = setOf("src/test/java", "src/test/kotlin", "src/test/resources")
    }

    //
    // Directories
    //

    @Directive
    fun sourceDirectories(init: Sources.() -> Unit) : Sources {
        return Sources(this, sourceDirectories).apply { init() }
    }

    var sourceDirectories = hashSetOf<String>().apply { addAll(DEFAULT_SOURCE_DIRECTORIES)}

    @Directive
    fun sourceDirectoriesTest(init: Sources.() -> Unit) : Sources {
        return Sources(this, sourceDirectoriesTest).apply { init() }
    }

    var sourceDirectoriesTest = hashSetOf<String>().apply { addAll(DEFAULT_SOURCE_DIRECTORIES_TEST)}

    //
    // Dependencies
    //

    @Directive
    fun dependencies(init: Dependencies.() -> Unit) : Dependencies {
        dependencies = Dependencies(this, compileDependencies, compileProvidedDependencies,
                compileRuntimeDependencies, excludedDependencies)
        dependencies!!.init()
        return dependencies!!
    }

    val compileDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val compileProvidedDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val compileRuntimeDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val excludedDependencies : ArrayList<IClasspathDependency> = arrayListOf()

    @Directive
    fun dependenciesTest(init: Dependencies.() -> Unit) : Dependencies {
        dependencies = Dependencies(this, testDependencies, testProvidedDependencies, compileRuntimeDependencies,
                excludedDependencies)
        dependencies!!.init()
        return dependencies!!
    }

    val testDependencies : ArrayList<IClasspathDependency> = arrayListOf()
    val testProvidedDependencies : ArrayList<IClasspathDependency> = arrayListOf()

    /** Used to disambiguate various name properties */
    @Directive
    val projectName: String get() = name

    val productFlavors = hashMapOf<String, ProductFlavorConfig>()

    fun addProductFlavor(name: String, pf: ProductFlavorConfig) {
        productFlavors.put(name, pf)
    }

    var defaultConfig : BuildConfig? = null

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
}

class Sources(val project: Project, val sources: HashSet<String>) {
    @Directive
    fun path(vararg paths: String) {
        sources.addAll(paths)
    }
}

class Dependencies(val project: Project,
        val dependencies: ArrayList<IClasspathDependency>,
        val providedDependencies: ArrayList<IClasspathDependency>,
        val runtimeDependencies: ArrayList<IClasspathDependency>,
        val excludedDependencies: ArrayList<IClasspathDependency>) {

    /**
     * Add the dependencies to the given ArrayList and return a list of jar files corresponding to
     * these dependencies.
     */
    private fun addToDependencies(project: Project, dependencies: ArrayList<IClasspathDependency>,
            dep: Array<out String>): List<File>
        = with(dep.map { DependencyManager.create(it, project)}) {
            dependencies.addAll(this)
            this.map { it.jarFile.get() }
    }

    @Directive
    fun compile(vararg dep: String) = addToDependencies(project, dependencies, dep)

    @Directive
    fun provided(vararg dep: String) = addToDependencies(project, providedDependencies, dep)

    @Directive
    fun runtime(vararg dep: String) = addToDependencies(project, runtimeDependencies, dep)

    @Directive
    fun exclude(vararg dep: String) = addToDependencies(project, excludedDependencies, dep)
}

class Scm(val connection: String, val developerConnection: String, val url: String)

class License(val name: String, val url: String) {
    fun toMavenLicense() : org.apache.maven.model.License {
        val result = org.apache.maven.model.License()
        result.name = name
        result.url = url
        return result
    }

}

class BuildConfig {
    class Field(val name: String, val type: String, val value: Any) {
        override fun hashCode() = name.hashCode()
        override fun equals(other: Any?) = (other as Field).name == name
    }

    val fields = arrayListOf<Field>()

    fun field(type: String, name: String, value: Any) {
        fields.add(Field(name, type, value))
    }
}

interface IBuildConfig {
    var buildConfig: BuildConfig?

    fun buildConfig(init: BuildConfig.() -> Unit) {
        buildConfig = BuildConfig().apply {
            init()
        }
    }
}

class ProductFlavorConfig(val name: String) : IBuildConfig {
    var applicationId: String? = null
    override var buildConfig : BuildConfig? = BuildConfig()
}

@Directive
fun Project.productFlavor(name: String, init: ProductFlavorConfig.() -> Unit) = ProductFlavorConfig(name).apply {
        init()
        addProductFlavor(name, this)
    }

class BuildTypeConfig(val project: Project?, val name: String) : IBuildConfig {
    var minifyEnabled = false
    var applicationIdSuffix: String? = null
    var proguardFile: String? = null

//    fun getDefaultProguardFile(name: String) : String {
//        val androidPlugin = Plugins.findPlugin(AndroidPlugin.PLUGIN_NAME) as AndroidPlugin
//        return Proguard(androidPlugin.androidHome(project)).getDefaultProguardFile(name)
//    }

    override var buildConfig : BuildConfig? = BuildConfig()
}

@Directive
fun Project.buildType(name: String, init: BuildTypeConfig.() -> Unit) = BuildTypeConfig(this, name).apply {
        init()
        addBuildType(name, this)
    }

fun Project.defaultConfig(init: BuildConfig.() -> Unit) = let { project ->
    BuildConfig().apply {
        init()
        project.defaultConfig = this
    }
}
