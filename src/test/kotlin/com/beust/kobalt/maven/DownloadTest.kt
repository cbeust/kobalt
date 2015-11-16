package com.beust.kobalt.maven

import com.beust.kobalt.KobaltTest
import com.beust.kobalt.misc.KobaltExecutors
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

    @Test
    public fun shouldDownloadWithVersion() {
        File(localRepo.toFullPath("org/testng/testng")).deleteRecursively()

        arrayListOf("org.testng:testng:6.9.4", "org.testng:testng:6.9.5").forEach {
            val dep = depFactory.create(it, executor)
            val future = dep.jarFile
            Assert.assertFalse(future is CompletedFuture)
            val file = future.get()
            Assert.assertTrue(file.exists())
        }
    }

    val latestTestNg = "6.9.9"

    @Test
    public fun shouldDownloadNoVersion() {
        File(localRepo.toFullPath("org/testng/testng")).deleteRecursively()

        val dep = depFactory.create("org.testng:testng:", executor)

        val future = dep.jarFile
        val file = future.get()
        Assert.assertFalse(future is CompletedFuture)
        Assert.assertEquals(file.name, "testng-$latestTestNg.jar")
        Assert.assertTrue(file.exists())
    }

    @Test(dependsOnMethods = arrayOf("shouldDownloadWithVersion"))
    public fun shouldFindLocalJar() {
        val dep = depFactory.create("org.testng:testng:$latestTestNg", executor)
        val future = dep.jarFile
        Assert.assertTrue(future is CompletedFuture)
        val file = future.get()
        Assert.assertTrue(file.exists())
    }

    @Test(dependsOnMethods = arrayOf("shouldDownloadWithVersion"))
    public fun shouldFindLocalJarNoVersion() {
        val dep = depFactory.create("org.testng:testng:", executor)
        val future = dep.jarFile
        val file = future.get()
        Assert.assertEquals(file.name, "testng-$latestTestNg.jar")
        Assert.assertTrue(file.exists())
    }
}

