package com.beust.kobalt.api

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.util.*

open class Project(
        @Directive open var name: String,
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
        val suffixesFound : Set<String> by lazy {
            val sf = hashSetOf<String>()
            Kobalt.context?.let {
                project.sourceDirectories.forEach { source ->
                    val sourceDir = File(KFiles.joinDir(project.directory, source))
                    if (sourceDir.exists()) {
                        KFiles.findRecursively(sourceDir, { file ->
                            val ind = file.lastIndexOf(".")
                            if (ind >= 0) {
                                sf.add(file.substring(ind + 1))
                            }
                            false
                        })
                    } else {
                        log(2, "Skipping nonexistent directory $sourceDir")
                    }
                }
            }
            sf
        }

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

    val testConfigs = arrayListOf(TestConfig(this))

    override var buildConfig : BuildConfig? = null //BuildConfig()

    val projectProperties = ProjectProperties()

    override fun equals(other: Any?): Boolean {
        return name == (other as Project).name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        val DEFAULT_SOURCE_DIRECTORIES = hashSetOf("src/main/java", "src/main/kotlin", "src/main/resources")
        val DEFAULT_SOURCE_DIRECTORIES_TEST = hashSetOf("src/test/java", "src/test/kotlin")
    }

    //
    // Directories
    //

    @Directive
    fun sourceDirectories(init: Sources.() -> Unit) : Sources {
        return Sources(this, sourceDirectories).apply { init() }
    }

    private fun existing(dirs: Set<String>) = dirs.filter { File(directory, it).exists() }.toHashSet()

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
        dependencies = Dependencies(this, compileDependencies, compileProvidedDependencies, compileRuntimeDependencies,
                excludedDependencies)
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

class Dependencies(val project: Project, val dependencies: ArrayList<IClasspathDependency>,
        val providedDependencies: ArrayList<IClasspathDependency>,
        val runtimeDependencies: ArrayList<IClasspathDependency>,
        val excludedDependencies: ArrayList<IClasspathDependency>) {

    /**
     * Add the dependencies to the given ArrayList and return a list of jar files corresponding to
     * these dependencies.
     */
    private fun addToDependencies(dependencies: ArrayList<IClasspathDependency>, dep: Array<out String>)
            : List<File>
        = with(dep.map { MavenDependency.create(it)}) {
            dependencies.addAll(this)
            this.map { it.jarFile.get() }
    }

    @Directive
    fun compile(vararg dep: String) = addToDependencies(dependencies, dep)

    @Directive
    fun provided(vararg dep: String) = addToDependencies(providedDependencies, dep)

    @Directive
    fun runtime(vararg dep: String) = addToDependencies(runtimeDependencies, dep)

    @Directive
    fun exclude(vararg dep: String) = addToDependencies(excludedDependencies, dep)
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
    class Field(val name: String, val type: String, val value: Any)

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
