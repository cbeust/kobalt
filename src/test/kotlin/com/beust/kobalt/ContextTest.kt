package com.beust.kobalt

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.maven.aether.KobaltAether
import com.google.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.DataProvider
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.io.File

@Guice(modules = arrayOf(TestModule::class))
class ContextTest @Inject constructor(override val aether: KobaltAether): BaseTest(aether) {

    val GROUP = "org.testng"
    val ARTIFACT = "testng"
    val VERSION = "6.9.11"
    val REPO_PATH = TEST_KOBALT_SETTINGS.localCache
    val id = "$GROUP:$ARTIFACT:$VERSION"

    @DataProvider
    fun dp() : Array<Array<out Any?>> {
        return arrayOf(
            arrayOf(KobaltContext.FileType.JAR, "$ARTIFACT-$VERSION.jar"),
            arrayOf(KobaltContext.FileType.POM, "$ARTIFACT-$VERSION.pom"),
            arrayOf(KobaltContext.FileType.JAVADOC, "$ARTIFACT-$VERSION-javadoc.jar"),
            arrayOf(KobaltContext.FileType.SOURCES, "$ARTIFACT-$VERSION-sources.jar")
        )
    }

    private fun runTest(id: String, fileType: KobaltContext.FileType, expected: String) {
        val file = context.fileFor(id, fileType)
        assertThat(file.absolutePath).isEqualTo(expected)
    }

    @Test(dataProvider = "dp", description = "Test KobaltContext#fileForId")
    fun fileForIdShouldWork(fileType: KobaltContext.FileType, expectedFileName: String) {
        runTest(id, fileType, expected = listOf(REPO_PATH, GROUP.replace('.', File.separatorChar), ARTIFACT, VERSION,
                expectedFileName)
            .joinToString(File.separator))
    }

    @Test(description = "Test KobaltContext#fileForId for the OTHER file type")
    fun fileForIdOther() {
        runTest("io.reactivex:rxandroid:aar:1.0.1", KobaltContext.FileType.OTHER,
                expected = listOf(REPO_PATH, File("io/reactivex/rxandroid/1.0.1/rxandroid-1.0.1.aar").path)
                        .joinToString(File.separator))
    }
}
