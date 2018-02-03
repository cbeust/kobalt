package com.beust.kobalt.plugin.osgi

import aQute.bnd.osgi.Analyzer
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.archive.MetaArchive
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.plugin.packaging.PackagingPlugin
import com.google.common.reflect.ClassPath
import com.google.inject.Inject
import com.google.inject.Singleton
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLClassLoader
import java.nio.file.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.jar.JarFile

/**
 * Generate OSGi attributes in the MANIFEST.MF if an osgi{} directive was found in the project.
 */
@Singleton
class OsgiPlugin @Inject constructor(val configActor: ConfigActor<OsgiConfig>, val taskContributor: TaskContributor,
        val dependencyManager: DependencyManager)
        : BasePlugin(), ITaskContributor by taskContributor, IConfigActor<OsgiConfig> by configActor {
    companion object {
        const val PLUGIN_NAME = "Osgi"
    }
    override val name: String = PLUGIN_NAME

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)

        configurationFor(project)?.let { config ->
            taskContributor.addTask(this, project, "generateOsgiManifest",
                    description = "Generate the OSGi information in the manifest",
                    group = "build",
                    alwaysRunAfter = listOf(PackagingPlugin.TASK_ASSEMBLE),
                    runTask = { generateManifest(project, context) })
        }
    }

    private fun generateManifest(project: Project, context: KobaltContext): TaskResult {
        val jarName = project.projectProperties.get(Archives.JAR_NAME) as String
        val jarFile = File(KFiles.libsDir(project), jarName)
        val cp = ClassPath.from(URLClassLoader(arrayOf(jarFile.toURI().toURL()), null))

        val packages = cp.allClasses.map { it.packageName }.distinct()
        val exportPackageLine = packages.map {
            it + ";version=\"" + project.version + "\""
        }.joinToString(",")

        val toFile = Files.createTempFile(null, ".jar")
        val analyzer = Analyzer().apply {
            jar = aQute.bnd.osgi.Jar(jarName)
            val dependencies = project.compileDependencies + project.compileRuntimeDependencies
            dependencyManager.calculateDependencies(project, context, passedDependencies = dependencies).forEach {
                addClasspath(it.jarFile.get())
            }
            setProperty("Build-Date", LocalDate.now().format(DateTimeFormatter.ofPattern("y-MM-dd")))
            setProperty(Analyzer.BUNDLE_VERSION, project.version)
            setProperty(Analyzer.BUNDLE_NAME, project.group + "." + project.artifactId)
            setProperty(Analyzer.BUNDLE_DESCRIPTION, project.description)
            setProperty(Analyzer.IMPORT_PACKAGE, "*")
            setProperty(Analyzer.EXPORT_PACKAGE, exportPackageLine)
            project.pom?.let { pom ->
                if (pom.licenses.any()) {
                    setProperty(Analyzer.BUNDLE_LICENSE, pom.licenses[0].url)
                }
            }
        }

        analyzer.calcManifest().let { manifest ->
            val lines2 = ByteArrayOutputStream().use { baos ->
                manifest.write(baos)
                String(baos.toByteArray())
            }

            context.logger.log(project.name, 2, "  Generated manifest:\n$lines2")

            //
            // Update or create META-INF/MANIFEST.MF
            //
            KFiles.copy(Paths.get(jarFile.toURI()), Paths.get(toFile.toUri()))

            val fileSystem = FileSystems.newFileSystem(toFile, null)
            fileSystem.use { fs ->
                JarFile(jarFile).use { jf ->
                    val mf = jf.getEntry(MetaArchive.MANIFEST_MF)
                    if (mf == null) {
                        Files.createDirectories(fs.getPath("META-INF/"))
                    }
                    val jarManifest = fs.getPath(MetaArchive.MANIFEST_MF)
                    Files.write(jarManifest, listOf(lines2),
                            if (mf != null) StandardOpenOption.APPEND else StandardOpenOption.CREATE)
                }
            }
            Files.copy(Paths.get(toFile.toUri()), Paths.get(jarFile.toURI()), StandardCopyOption.REPLACE_EXISTING)
            return TaskResult()
        }
    }
}

class OsgiConfig

@Directive
fun Project.osgi(init: OsgiConfig.() -> Unit) {
    OsgiConfig().let {
        it.init()
        (Kobalt.findPlugin(OsgiPlugin.PLUGIN_NAME) as OsgiPlugin).addConfiguration(this, it)
    }
}


