package com.beust.kobalt.maven

import com.beust.kobalt.Args
import com.beust.kobalt.TestModule
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.DependencyExecutor
import org.testng.Assert
import org.testng.annotations.Test
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@Test
@org.testng.annotations.Guice(modules = arrayOf(TestModule::class))
class RemoteRepoTest @Inject constructor(val repoFinder: RepoFinder,
        @DependencyExecutor val executor: ExecutorService, val args: Args){

    @Test
    fun mavenMetadata() {
        val dep = MavenDependency.create("org.codehaus.groovy:groovy-all:")
        // Note: this test might fail if a new version of Groovy gets uploaded, need
        // to find a stable (i.e. abandoned) package
        Assert.assertEquals(dep.id.split(":")[2], "2.4.6")
    }

    @Test(enabled = false)
    fun metadataForSnapshots() {
        val jar = MavenDependency.create("org.apache.maven.wagon:wagon-provider-test:2.10-SNAPSHOT",
                executor = executor).jarFile
        Assert.assertTrue(jar.get().exists())
    }

    fun resolveAarWithVersion() {
        val repoResult = repoFinder.findCorrectRepo("com.jakewharton.timber:timber:4.1.0")
        with(repoResult) {
            Assert.assertEquals(path, "com/jakewharton/timber/timber/4.1.0/timber-4.1.0.aar")
        }
    }

    @Test(groups = arrayOf("broken"), enabled = false)
    fun resolveAarWithoutVersion() {
        val repoResult = repoFinder.findCorrectRepo("com.jakewharton.timber:timber:")
        with(repoResult) {
            Assert.assertEquals(path, "com/jakewharton/timber/timber/4.1.0/timber-4.1.0.aar")
        }
    }
}
