package com.beust.kobalt.maven

import com.beust.kobalt.TestModule
import com.beust.kobalt.misc.DependencyExecutor
import com.beust.kobalt.misc.MainModule
import com.google.inject.Guice
import org.testng.Assert
import org.testng.annotations.Test
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@org.testng.annotations.Guice(modules = arrayOf(TestModule::class))
public class RemoteRepoTest @Inject constructor(val repoFinder: RepoFinder,
        @DependencyExecutor val executor: ExecutorService){

    val INJECTOR = Guice.createInjector(MainModule())

    @Test
    public fun mavenMetadata() {
        val dep = MavenDependency.create("org.codehaus.groovy:groovy-all:")
        Assert.assertEquals(dep.id.split(":")[2], "2.4.4")
    }

    @Test
    public fun metadataForSnapshots() {
        val jar = MavenDependency.create("org.apache.maven.wagon:wagon-provider-test:2.10-SNAPSHOT", executor)
                .jarFile
        Assert.assertTrue(jar.get().exists())
    }
}
