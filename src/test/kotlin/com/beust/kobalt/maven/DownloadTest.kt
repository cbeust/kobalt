package com.beust.kobalt.maven

import com.beust.kobalt.KobaltTest
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.io.File
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.properties.Delegates

/**
 * TODO: test snapshots  https://repository.jboss.org/nexus/content/repositories/root_repository//commons-lang/commons-lang/2.7-SNAPSHOT/commons-lang-2.7-SNAPSHOT.jar
 */
public class DownloadTest @Inject constructor(
        val depFactory: DepFactory,
        val localRepo: LocalRepo,
        val executors: KobaltExecutors) : KobaltTest() {
    var executor: ExecutorService by Delegates.notNull()

    @BeforeClass
    public fun bc() {
        executor = executors.newExecutor("DependentTest", 5)
    }

    private fun deleteDir() : Boolean {
        val dir = File(localRepo.toFullPath("$groupId"))
        val result = dir.deleteRecursively()
        return result
    }

    @Test
    public fun shouldDownloadWithVersion() {
        val success = deleteDir()

        if (success) {
            arrayListOf("$groupId:$artifactId:$version", "$groupId:$artifactId:$previousVersion").forEach {
                val dep = depFactory.create(it, executor)
                val future = dep.jarFile
                Assert.assertFalse(future is CompletedFuture)
                val file = future.get()
                Assert.assertTrue(file.exists())
            }
        } else {
            warn("Couldn't delete directory, not running test \"shouldDownloadNoVersion\"")
        }
    }

    val version = "2.9.1"
    val previousVersion = "2.9"
    val groupId = "joda-time"
   val artifactId = "joda-time"
    val jarFile = "$artifactId-$version.jar"
    val idNoVersion = "$groupId:$artifactId:"

    @Test(description = "Make sure that versionless id's, e.g. org.testng:testng:, get downloaded")
    public fun shouldDownloadNoVersion() {
        val success = deleteDir()
        if (success) {
            val dep = depFactory.create(idNoVersion, executor)

            val future = dep.jarFile
            val file = future.get()
            Assert.assertFalse(future is CompletedFuture)
            Assert.assertEquals(file.name, jarFile)
            Assert.assertTrue(file.exists())
        } else {
            warn("Couldn't delete directory, not running test \"shouldDownloadNoVersion\"")
        }
    }

    @Test(dependsOnMethods = arrayOf("shouldDownloadWithVersion"))
    public fun shouldFindLocalJar() {
        val dep = depFactory.create("$idNoVersion$version", executor)
        val future = dep.jarFile
//        Assert.assertTrue(future is CompletedFuture)
        val file = future.get()
        Assert.assertTrue(file.exists())
    }

    @Test(dependsOnMethods = arrayOf("shouldDownloadWithVersion"))
    public fun shouldFindLocalJarNoVersion() {
        val dep = depFactory.create(idNoVersion, executor, localFirst = false)
        val future = dep.jarFile
        val file = future.get()
        Assert.assertEquals(file.name, jarFile)
        Assert.assertTrue(file.exists())
    }
}

