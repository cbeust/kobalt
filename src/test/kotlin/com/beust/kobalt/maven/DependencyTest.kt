package com.beust.kobalt.maven

import com.beust.kobalt.TestModule
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.StringVersion
import org.testng.Assert
import org.testng.annotations.*
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.properties.Delegates

@Guice(modules = arrayOf(TestModule::class))
class DependencyTest @Inject constructor(val executors: KobaltExecutors) {

    @DataProvider
    fun dpVersions(): Array<Array<out Any>> {
        return arrayOf(
                arrayOf("0.1", "0.1.1"),
                arrayOf("0.1", "1.4"),
                arrayOf("6.9.4", "6.9.5"),
                arrayOf("1.7", "1.38"),
                arrayOf("1.70", "1.380"),
                arrayOf("3.8.1", "4.5"),
                arrayOf("18.0-rc1", "19.0"),
                arrayOf("3.0.5.RELEASE", "3.0.6")
                )
    }

    private var executor: ExecutorService by Delegates.notNull()

    @BeforeClass
    fun bc() {
        executor = executors.newExecutor("DependencyTest", 5)
    }

    @AfterClass
    fun ac() {
        executor.shutdown()
    }

    @Test(dataProvider = "dpVersions")
    fun versionSorting(k: String, v: String) {
        val dep1 = StringVersion(k)
        val dep2 = StringVersion(v)
        Assert.assertTrue(dep1 < dep2)
        Assert.assertTrue(dep2 > dep1)
    }
}

