package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.KobaltTest
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.Version
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

    private fun deleteDir(): Boolean {
        val dir = File(localRepo.toFullPath(groupId))
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
            warn("Couldn't delete directory, not running test \"shouldDownloadWithVersion\"")
        }
    }

    val version = "2.9.1"
    val previousVersion = "2.9"
    val groupId = "joda-time"
    val artifactId = "joda-time"
    val idNoVersion = "$groupId:$artifactId:"

    @Test(description = "Make sure that versionless id's, e.g. org.testng:testng:, get downloaded")
    public fun shouldDownloadNoVersion() {
        val success = deleteDir()
        if (success) {
            val dep = depFactory.create(idNoVersion, executor)

            val future = dep.jarFile
            val file = future.get()
            Assert.assertFalse(future is CompletedFuture)
            Assert.assertNotNull(file)
            Assert.assertTrue(file.exists())
        } else {
            warn("Couldn't delete directory, not running test \"shouldDownloadNoVersion\"")
        }
    }

    @Test
    public fun shouldDownloadRangedVersion() {
        File(localRepo.toFullPath("javax/servlet/servlet-api")).deleteRecursively()
        val range = "[2.5,)"
        val expected = "3.0-alpha-1"

        val dep = depFactory.create("javax.servlet:servlet-api:$range", executor)
        val future = dep.jarFile
        val file = future.get()
        Assert.assertFalse(future is CompletedFuture)
        Assert.assertEquals(file.name, "servlet-api-$expected.jar")
        Assert.assertTrue(file.exists())
    }

    @Test
    public fun shouldFindLocalJar() {
        MavenDependency.create("$idNoVersion$version")
        val dep = depFactory.create("$idNoVersion$version", executor)
        val future = dep.jarFile
        //        Assert.assertTrue(future is CompletedFuture)
        val file = future.get()
        Assert.assertTrue(file.exists())
    }

    @Test
    public fun shouldFindLocalJarNoVersion() {
        val dep = MavenDependency.create("$idNoVersion$version")
        val future = dep.jarFile
        future.get().delete()

        val dep2 = MavenDependency.create("$idNoVersion$version")
        val file = dep2.jarFile.get()
        Assert.assertNotNull(file)
        Assert.assertTrue(file.exists(), "Should find $file")
    }

    @Test
    fun snapshotTest() {
        val id = "org.jetbrains.spek:spek:0.1-SNAPSHOT"
        val mavenId = MavenId.create(id)
        val dep = SimpleDep(mavenId)

        // TODO: allow tests to add their own repo. The following call requires
        // "http://repository.jetbrains.com/all" to work
        // For now, just hardcoding the result we should have received
//        val repoResult = repoFinder.findCorrectRepo(id)

        val repoResult = RepoFinder.RepoResult(HostConfig("http://repository.jetbrains.com/all/"),
                true, Version.of("0.1-SNAPSHOT"), true, Version("0.1-SNAPSHOT", "20151011.112011-29"))

        val jarFile = dep.toJarFile(repoResult)
        val url = repoResult.hostConfig.url + jarFile

        val metadataXmlPath = dep.toMetadataXmlPath(false, false, "0.1-SNAPSHOT")
            .replace("\\", "/")

        Assert.assertEquals(metadataXmlPath, "org/jetbrains/spek/spek/0.1-SNAPSHOT/maven-metadata.xml")
        Assert.assertTrue(Kurl(HostConfig(url)).exists, "Should exist: $url")
    }

}

