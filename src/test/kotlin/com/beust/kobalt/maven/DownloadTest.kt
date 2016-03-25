package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.KobaltTest
import com.beust.kobalt.maven.aether.KobaltAether
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.warn
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.io.File
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.properties.Delegates

@Test
class DownloadTest @Inject constructor(
        val localRepo: LocalRepo,
        val pomFactory: Pom.IFactory,
        val dependencyManager: DependencyManager,
        val depFactory: DependencyFactory,
        val aether: KobaltAether,
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
                val dep = depFactory.create(it)
                val future = dep.jarFile
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
            val dep = depFactory.create(idNoVersion)

            val future = dep.jarFile
            val file = future.get()
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

        val dep = depFactory.create("javax.servlet:servlet-api:$range")
        val future = dep.jarFile
        val file = future.get()
        Assert.assertEquals(file.name, "servlet-api-$expected.jar")
        Assert.assertTrue(file.exists())
    }

    @Test
    fun shouldFindLocalJar() {
        depFactory.create("$idNoVersion$version")
        val dep = depFactory.create("$idNoVersion$version")
        val future = dep.jarFile
        //        Assert.assertTrue(future is CompletedFuture)
        val file = future.get()
        Assert.assertTrue(file.exists())
    }

    @Test
    fun shouldFindLocalJarNoVersion() {
        val dep = depFactory.create("$idNoVersion$version")
        val future = dep.jarFile
        future.get().delete()

        val dep2 = depFactory.create("$idNoVersion$version")
        val file = dep2.jarFile.get()
        Assert.assertNotNull(file)
        Assert.assertTrue(file.exists(), "Couldn't find ${file.absolutePath}")
    }

//    @Test(groups = arrayOf("broken"), enabled = false)
//    fun snapshotTest() {
//        val id = "org.jetbrains.spek:spek:0.1-SNAPSHOT"
//        val mavenId = MavenId.create(id)
//        val dep = SimpleDep(mavenId)
//
//        // TODO: allow tests to add their own repo. The following call requires
//        // "http://repository.jetbrains.com/all" to work
//        // For now, just hardcoding the result we should have received
////        val repoResult = repoFinder.findCorrectRepo(id)
//
//        val hc = HostConfig("http://repository.jetbrains.com/all/")
//        val repoResult = RepoFinder.RepoResult(hc,
//                Version.of("0.1-SNAPSHOT"), hc.url, Version("0.1-SNAPSHOT", "20151011.112011-29"))
//
//        val jarFile = dep.toJarFile(repoResult)
//        val url = repoResult.hostConfig.url + jarFile
//
//        val metadataXmlPath = dep.toMetadataXmlPath(false, false, "0.1-SNAPSHOT")
//            .replace("\\", "/")
//
//        Assert.assertEquals(metadataXmlPath, "org/jetbrains/spek/spek/0.1-SNAPSHOT/maven-metadata.xml")
//        Assert.assertTrue(Kurl(HostConfig(url)).exists, "Should exist: $url")
//    }

    @Test
    fun jitpackTest() {
        val id = "http://jitpack.io/com/github/JakeWharton/RxBinding/rxbinding-kotlin/542cd7e8a4/rxbinding-kotlin-542cd7e8a4.aar"
        Assert.assertTrue(Kurl(HostConfig(id)).exists)
    }

    @Test
    fun containerPomTest() {
        File(localRepo.toFullPath("nl/komponents/kovenant")).deleteRecursively()
        val dep = depFactory.create("nl.komponents.kovenant:kovenant:3.0.0")
        dep.directDependencies().forEach {
            Assert.assertTrue(it.jarFile.get().exists(), "Dependency was not downloaded: $it")
        }
    }

    @Test
    fun parentPomTest() {
        // Resolve com.squareup.retrofit2:converter-moshi:2.0.0
        // This id has a parent pom which defines moshi version to be 1.1.0. Make sure that this
        // version is being fetched instead of moshi:1.2.0-SNAPSHOT (which gets discarded anyway
        // since snapshots are not allowed to be returned when looking up a versionless id)
        val host = HostConfig("http://repository.jetbrains.com/all/")
        val id = "com.squareup.moshi:moshi:(0,]"
        val dr = aether.resolve(id)
        Assert.assertEquals(dr.dependency.version, "1.1.0")
    }

    @Test
    fun variablesShouldBeExpanded() {
        val dep = dependencyManager.createMaven("org.mapdb:mapdb:3.0.0-M3")
        val closure = dependencyManager.transitiveClosure(listOf(dep))
        val d = closure.filter { it.id.contains("eclipse-collections-api")}
        Assert.assertEquals(d.size, 1)
    }

    @Test
    fun containerPom() {
        val repoResult = RepoFinderCallable("org.jetbrains.kotlin:kotlin-project:1.0.0",
                HostConfig("http://repo1.maven.org/maven2/"),
                localRepo, pomFactory, dependencyManager).call()
        val rr = repoResult[0]
        Assert.assertTrue(rr.found)
        Assert.assertTrue(rr.localPath != null && rr.localPath!!.startsWith("junit/junit"))
        Assert.assertEquals(rr.version.toString(), "4.12")
    }
}

