package com.beust.kobalt.maven

import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.maven.LocalRepo
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.MainModule
import com.beust.kobalt.TestModule
import com.beust.kobalt.api.Kobalt
import com.google.inject.Module
import com.google.inject.util.Modules
import org.testng.Assert
import org.testng.IModuleFactory
import org.testng.ITestContext
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.io.File
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.properties.Delegates

/**
 * TODO: test snapshots  https://repository.jboss.org/nexus/content/repositories/root_repository//commons-lang/commons-lang/2.7-SNAPSHOT/commons-lang-2.7-SNAPSHOT.jar
 */
@Guice(modules = arrayOf(TestModule::class))
public class DownloadTest @Inject constructor(
        val depFactory: DepFactory,
        val localRepo: LocalRepo,
        val executors: KobaltExecutors) {
    var executor: ExecutorService by Delegates.notNull()

    @BeforeSuite
    public fun bs() {
        Kobalt.INJECTOR = com.google.inject.Guice.createInjector(TestModule())
    }

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
