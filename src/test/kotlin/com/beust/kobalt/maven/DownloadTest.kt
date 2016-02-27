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
@Test
class DownloadTest @Inject constructor(
        val depFactory: DepFactory,
        val localRepo: LocalRepo,
        val mdFactory: MavenDependency.IFactory,
        val executors: KobaltExecutors) : KobaltTest() {
    private var executor: ExecutorService by Delegates.notNull()

    @BeforeClass
    fun bc() {
        executor = executors.newExecutor("DependentTest", 5)
    }

    private fun deleteDir(): Boolean {
        val dir = File(localRepo.toFullPath(groupId))
        val result = dir.deleteRecursively()
        return result
    }

    @Test
    fun shouldDownloadWithVersion() {
        val success = deleteDir()

        if (success) {
            arrayListOf("$groupId:$artifactId:$version", "$groupId:$artifactId:$previousVersion").forEach {
                val dep = depFactory.create(it, executor = executor)
                val future = dep.jarFile
                Assert.assertFalse(future is CompletedFuture)
                val file = future.get()
                Assert.assertTrue(file.exists(), "Couldn't find ${file.absolutePath}")
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
    fun shouldDownloadNoVersion() {
        val success = deleteDir()
        if (success) {
            val dep = depFactory.create(idNoVersion, executor = executor)

            val future = dep.jarFile
            val file = future.get()
            Assert.assertFalse(future is CompletedFuture)
            Assert.assertNotNull(file)
            Assert.assertTrue(file.exists(), "Couldn't find ${file.absolutePath}")
        } else {
            warn("Couldn't delete directory, not running test \"shouldDownloadNoVersion\"")
        }
    }

    @Test(groups = arrayOf("broken"), enabled = false)
    fun shouldDownloadRangedVersion() {
        File(localRepo.toFullPath("javax/servlet/servlet-api")).deleteRecursively()
        val range = "[2.5,)"
        val expected = "3.0-alpha-1"

        val dep = depFactory.create("javax.servlet:servlet-api:$range", executor = executor)
        val future = dep.jarFile
        val file = future.get()
        Assert.assertFalse(future is CompletedFuture)
        Assert.assertEquals(file.name, "servlet-api-$expected.jar")
        Assert.assertTrue(file.exists())
    }

    @Test
    fun shouldFindLocalJar() {
        MavenDependency.create("$idNoVersion$version")
        val dep = depFactory.create("$idNoVersion$version", executor = executor)
        val future = dep.jarFile
        //        Assert.assertTrue(future is CompletedFuture)
        val file = future.get()
        Assert.assertTrue(file.exists())
    }

    @Test
    fun shouldFindLocalJarNoVersion() {
        val dep = MavenDependency.create("$idNoVersion$version")
        val future = dep.jarFile
        future.get().delete()

        val dep2 = MavenDependency.create("$idNoVersion$version")
        val file = dep2.jarFile.get()
        Assert.assertNotNull(file)
        Assert.assertTrue(file.exists(), "Couldn't find ${file.absolutePath}")
    }

    @Test(groups = arrayOf("broken"), enabled = false)
    fun snapshotTest() {
        val id = "org.jetbrains.spek:spek:0.1-SNAPSHOT"
        val mavenId = MavenId.create(id)
        val dep = SimpleDep(mavenId)

        // TODO: allow tests to add their own repo. The following call requires
        // "http://repository.jetbrains.com/all" to work
        // For now, just hardcoding the result we should have received
//        val repoResult = repoFinder.findCorrectRepo(id)

        val hc = HostConfig("http://repository.jetbrains.com/all/")
        val repoResult = RepoFinder.RepoResult(hc,
                Version.of("0.1-SNAPSHOT"), hc.url, Version("0.1-SNAPSHOT", "20151011.112011-29"))

        val jarFile = dep.toJarFile(repoResult)
        val url = repoResult.hostConfig.url + jarFile

        val metadataXmlPath = dep.toMetadataXmlPath(false, false, "0.1-SNAPSHOT")
            .replace("\\", "/")

        Assert.assertEquals(metadataXmlPath, "org/jetbrains/spek/spek/0.1-SNAPSHOT/maven-metadata.xml")
        Assert.assertTrue(Kurl(HostConfig(url)).exists, "Should exist: $url")
    }

    @Test
    fun jitpackTest() {
        val id = "http://jitpack.io/com/github/JakeWharton/RxBinding/rxbinding-kotlin/542cd7e8a4/rxbinding-kotlin-542cd7e8a4.aar"
        Assert.assertTrue(Kurl(HostConfig(id)).exists)
    }

    @Test
    fun containerPomTest() {
        File(localRepo.toFullPath("nl/komponents/kovenant")).deleteRecursively()
        val dep = mdFactory.create(MavenId.create("nl.komponents.kovenant:kovenant:3.0.0"), executor = executor,
                downloadSources = false, downloadJavadocs = false)
        dep.directDependencies().forEach {
            Assert.assertTrue(it.jarFile.get().exists(), "Dependency was not downloaded: $it")
        }
    }

}

