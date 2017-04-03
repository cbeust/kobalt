package com.beust.kobalt.api

import com.beust.kobalt.TestConfig
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.aether.AetherDependency
import com.beust.kobalt.maven.aether.KobaltMavenResolver
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import org.apache.maven.model.Model
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.regex.Pattern

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
        @Directive open var testsDependOn: ArrayList<Project> = arrayListOf<Project>(),
        @Directive open var packageName: String? = group)
    : IBuildConfig, IDependencyHolder by DependencyHolder() {

    init {
        this.project = this
    }

    fun allProjectDependedOn() = project.dependsOn + project.testsDependOn

    class ProjectExtra(project: Project) {
        var isDirty = false

        /**
         * @return true if any of the projects we depend on is dirty.
         */
        fun dependsOnDirtyProjects(project: Project) = project.allProjectDependedOn().any { it.projectExtra.isDirty }
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

    fun testsDependOn(vararg projects: Project) = testsDependOn.addAll(projects)
    fun dependsOn(vararg projects: Project) = dependsOn.addAll(projects)

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

    /**
     * @return a list of the transitive dependencies (absolute paths to jar files) for the given dependencies.
     * Can be used for example as `collect(compileDependencies)`.
     */
    @Directive
    fun collect(dependencies: List<IClasspathDependency>) : List<String> {
        return Kobalt.context?.dependencyManager?.transitiveClosure(dependencies)?.map { it.jarFile.get() }
                ?.map { it.absolutePath}
            ?: emptyList()
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
            dep: Array<out String>, optional: Boolean = false, excludeConfig: ExcludeConfig? = null): List<Future<File>>
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
            if (excludeConfig != null) {
                this.forEach { it.excluded.add(excludeConfig) }
            }

            this.map { FutureTask { it.jarFile.get() } }
        }

    @Directive
    fun compile(vararg dep: String) = addToDependencies(project, dependencies, dep)

    class ExcludeConfig {
        val ids = arrayListOf<String>()

        @Directive
        fun exclude(vararg passedIds: String) = ids.addAll(passedIds)

        class ArtifactConfig(
            var groupId: String? = null,
            var artifactId: String? = null,
            var version: String? = null
        )

        val artifacts = arrayListOf<ArtifactConfig>()

        @Directive
        fun exclude(groupId: String? = null, artifactId: String? = null, version: String? = null)
            = artifacts.add(ArtifactConfig(groupId, artifactId, version))

        fun match(pattern: String?, id: String) : Boolean {
            return pattern == null || Pattern.compile(pattern).matcher(id).matches()
        }

        /**
         * @return true if the dependency is excluded with any of the exclude() directives. The matches
         * are performed by a regular expression match against the dependency.
         */
        fun isExcluded(dep: IClasspathDependency) : Boolean {
            // Straight id match
            var result = ids.any { match(it, dep.id) }

            // Match on any combination of (groupId, artifactId, version)
            if (! result && dep.isMaven) {
                val mavenDep = dep as AetherDependency
                val artifact = mavenDep.artifact
                result = artifacts.any {
                    val match1 = it.groupId == null || match(it.groupId, artifact.groupId)
                    val match2 = it.artifactId == null || match(it.artifactId, artifact.artifactId)
                    val match3 = it.version == null || match(it.version, artifact.version)
                    match1 && match2 && match3
                }
            }

            return result
        }
    }

    @Directive
    fun compile(dep: String, init: ExcludeConfig.() -> Unit) {
        val excludeConfig = ExcludeConfig().apply {
            init()
        }
        addToDependencies(project, dependencies, arrayOf(dep), excludeConfig = excludeConfig)
    }

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
