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
    override var buildConfig : BuildConfig? = null // BuildConfig()

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
    fun dependenciesTest(init: Dependencies.() -> Unit) : Dependencies {
        dependencies = Dependencies(this, testDependencies, arrayListOf(),
                testProvidedDependencies, compileRuntimeDependencies, excludedDependencies, nativeDependencies)
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

    override fun toString() = "[Project $name]"
}

class Sources(val project: Project, val sources: HashSet<String>) {
    @Directive
    fun path(vararg paths: String) {
        sources.addAll(paths)
    }
}

class Dependencies(val project: Project,
        val dependencies: ArrayList<IClasspathDependency>,
        val optionalDependencies: ArrayList<IClasspathDependency>,
        val providedDependencies: ArrayList<IClasspathDependency>,
        val runtimeDependencies: ArrayList<IClasspathDependency>,
        val excludedDependencies: ArrayList<IClasspathDependency>,
        val nativeDependencies: ArrayList<IClasspathDependency>) {

    /**
     * Add the dependencies to the given ArrayList and return a list of future jar files corresponding to
     * these dependencies. Futures are necessary here since this code is invoked from the build file and
     * we might not have set up the extra IRepositoryContributors just yet. By the time these
     * future tasks receive a get(), the repos will be correct.
     */
    private fun addToDependencies(project: Project, dependencies: ArrayList<IClasspathDependency>,
            dep: Array<out String>, optional: Boolean = false): List<Future<File>>
        = with(dep.map {
            val resolved =
                if (KobaltMavenResolver.isRangeVersion(it)) {
                    // Range id
                    val node = Kobalt.INJECTOR.getInstance(KobaltMavenResolver::class.java).resolveToArtifact(it)
                    val result = KobaltMavenResolver.artifactToId(node)
                    kobaltLog(2, "Resolved range id $it to $result")
                    result
                } else {
                    it
                }
            DependencyManager.create(resolved, optional, project.directory)
        }) {
            dependencies.addAll(this)
            this.map { FutureTask { it.jarFile.get() } }
        }

    @Directive
    fun compile(vararg dep: String) = addToDependencies(project, dependencies, dep)

    @Directive
    fun compileOptional(vararg dep: String) {
        addToDependencies(project, optionalDependencies, dep, optional = true)
        addToDependencies(project, dependencies, dep, optional = true)
    }

    @Directive
    fun provided(vararg dep: String) {
        addToDependencies(project, providedDependencies, dep)
        addToDependencies(project, dependencies, dep)
    }

    @Directive
    fun runtime(vararg dep: String) = addToDependencies(project, runtimeDependencies, dep)

    @Directive
    fun exclude(vararg dep: String) = addToDependencies(project, excludedDependencies, dep)

    @Directive
    fun native(vararg dep: String) = addToDependencies(project, nativeDependencies, dep)
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

fun Project.defaultConfig(init: BuildConfig.() -> Unit) = let { project ->
    BuildConfig().apply {
        init()
        project.defaultConfig = this
    }
}

@Directive
fun Project.buildType(name: String, init: BuildTypeConfig.() -> Unit) = BuildTypeConfig(name).apply {
    init()
    addBuildType(name, this)
}


@Directive
fun Project.productFlavor(name: String, init: ProductFlavorConfig.() -> Unit) = ProductFlavorConfig(name).apply {
    init()
    addProductFlavor(name, this)
}
