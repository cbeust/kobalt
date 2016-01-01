package com.beust.kobalt.api

import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.internal.IProjectInfo
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.KFiles
import java.util.*
import kotlin.collections.arrayListOf
import kotlin.collections.forEach
import kotlin.collections.hashMapOf
import kotlin.collections.hashSetOf

open class Project(
        @Directive open var name: String,
        @Directive open var version: String? = null,
        @Directive open var directory: String = ".",
        @Directive open var buildDirectory: String = KFiles.KOBALT_BUILD_DIR,
        @Directive open var group: String? = null,
        @Directive open var artifactId: String? = null,
        @Directive open var packaging: String? = null,
        @Directive open var dependencies: Dependencies? = null,
        @Directive open var sourceSuffix: String = "",
        @Directive open var description: String = "",
        @Directive open var scm: Scm? = null,
        @Directive open var url: String? = null,
        @Directive open var licenses: List<License> = arrayListOf<License>(),
        @Directive open var packageName: String? = group,
        val projectInfo: IProjectInfo) : IBuildConfig {

    override var buildConfig: BuildConfig? = null //BuildConfig()

    val testArgs = arrayListOf<String>()
    val testJvmArgs = arrayListOf<String>()
    val testIncludes = arrayListOf("**/*Test.class")
    val testExcludes = arrayListOf<String>()

    val projectProperties = ProjectProperties()

    override fun equals(other: Any?): Boolean {
        return name == (other as Project).name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    //
    // Directories
    //

    @Directive
    fun sourceDirectories(init: Sources.() -> Unit): Sources {
        val sources = Sources(this, sourceDirectories)
        sources.init()
        return sources
    }

    var sourceDirectories: HashSet<String> = hashSetOf()
        get() = if (field.isEmpty()) projectInfo.defaultSourceDirectories else field
        set(value) {
            field = value
        }

    @Directive
    fun sourceDirectoriesTest(init: Sources.() -> Unit): Sources {
        val sources = Sources(this, sourceDirectoriesTest)
        sources.init()
        return sources
    }

    var sourceDirectoriesTest: HashSet<String> = hashSetOf()
        get() = if (field.isEmpty()) projectInfo.defaultTestDirectories
        else field
        set(value) {
            field = value
        }

    //
    // Dependencies
    //

    @Directive
    fun dependencies(init: Dependencies.() -> Unit): Dependencies {
        dependencies = Dependencies(this, compileDependencies, compileProvidedDependencies, compileRuntimeDependencies)
        dependencies!!.init()
        return dependencies!!
    }

    val compileDependencies: ArrayList<IClasspathDependency> = arrayListOf()
    val compileProvidedDependencies: ArrayList<IClasspathDependency> = arrayListOf()
    val compileRuntimeDependencies: ArrayList<IClasspathDependency> = arrayListOf()

    @Directive
    fun dependenciesTest(init: Dependencies.() -> Unit): Dependencies {
        dependencies = Dependencies(this, testDependencies, testProvidedDependencies, compileRuntimeDependencies)
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
                   val runtimeDependencies: ArrayList<IClasspathDependency>) {
    @Directive
    fun compile(vararg dep: String) {
        dep.forEach { dependencies.add(MavenDependency.create(it)) }
    }

    @Directive
    fun provided(vararg dep: String) {
        dep.forEach { providedDependencies.add(MavenDependency.create(it)) }
    }

    @Directive
    fun runtime(vararg dep: String) {
        dep.forEach { runtimeDependencies.add(MavenDependency.create(it)) }
    }
}

class Scm(val connection: String, val developerConnection: String, val url: String)

class License(val name: String, val url: String) {
    fun toMavenLicense(): org.apache.maven.model.License {
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
    override var buildConfig: BuildConfig? = BuildConfig()
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

    override var buildConfig: BuildConfig? = BuildConfig()
}

@Directive
fun Project.buildType(name: String, init: BuildTypeConfig.() -> Unit) = BuildTypeConfig(this, name).apply {
    init()
    addBuildType(name, this)
}
