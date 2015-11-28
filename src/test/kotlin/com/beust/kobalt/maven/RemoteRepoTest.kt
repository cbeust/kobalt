package com.beust.kobalt.maven

import com.beust.kobalt.Args
import com.beust.kobalt.TestModule
import com.beust.kobalt.maven.dependency.MavenDependency
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.MainModule
import com.google.inject.Guice
import org.testng.Assert
import org.testng.annotations.Test
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@org.testng.annotations.Guice(modules = arrayOf(TestModule::class))
public class RemoteRepoTest @Inject constructor(val repoFinder: RepoFinder,
        @DependencyExecutor val executor: ExecutorService, val args: Args){

    @Test
    public fun mavenMetadata() {
        val dep = MavenDependency.create("org.codehaus.groovy:groovy-all:")
        // Note: this test might fail if a new version of Groovy gets uploaded, need
        // to find a stable (i.e. abandoned) package
        Assert.assertEquals(dep.id.split(":")[2], "2.4.5")
    }

    @Test(enabled = false)
    public fun metadataForSnapshots() {
        val jar = MavenDependency.create("org.apache.maven.wagon:wagon-provider-test:2.10-SNAPSHOT", executor)
                .jarFile
        Assert.assertTrue(jar.get().exists())
    }
}
