package com.beust.kobalt.plugin.android

import com.android.builder.core.AndroidBuilder
import com.android.builder.core.ErrorReporter
import com.android.builder.core.LibraryRequest
import com.android.builder.model.SyncIssue
import com.android.builder.sdk.DefaultSdkLoader
import com.android.builder.sdk.SdkLoader
import com.android.ide.common.blame.Message
import com.android.ide.common.process.*
import com.android.ide.common.res2.*
import com.android.sdklib.repository.FullRevision
import com.android.utils.StdLogger
import com.beust.kobalt.homeDir
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File

class KobaltProcessResult : ProcessResult {
    override fun getExitValue(): Int {
        return 0
    }

    override fun assertNormalExitValue(): ProcessResult? {
        throw UnsupportedOperationException()
    }

    override fun rethrowFailure(): ProcessResult? {
        throw UnsupportedOperationException()
    }
}

class KobaltJavaProcessExecutor : JavaProcessExecutor {
    override fun execute(javaProcessInfo: JavaProcessInfo?, processOutputHandler: ProcessOutputHandler?)
            : ProcessResult? {
        log(1, "Executing " + javaProcessInfo!!)
        return KobaltProcessResult()
    }
}

class KobaltProcessOutputHandler : BaseProcessOutputHandler() {
    override fun handleOutput(processOutput: ProcessOutput) =
            log(1, "AndroidBuild output" + processOutput.standardOutput)
}

class KobaltErrorReporter : ErrorReporter(ErrorReporter.EvaluationMode.STANDARD) {
    override fun handleSyncError(data: String?, type: Int, msg: String?): SyncIssue? {
        throw UnsupportedOperationException()
    }

    override fun receiveMessage(message: Message?) {
        throw UnsupportedOperationException()
    }
}

class ProjectLayout {
    val mergeBlame: File? = null
    val publicText: File? = null

}

class AndroidBuild {
    val annotationsJar = File("/Users/beust/adt-bundle-mac-x86_64-20140702/sdk/tools/lib/annotations.jar")
    val adb = File("/Users/beust/adt-bundle-mac-x86_64-20140702/sdk/platform-tools/adb")

    fun run() {
        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val processExecutor = DefaultProcessExecutor(logger)
        val javaProcessExecutor = KobaltJavaProcessExecutor()
        val sdkLoader : SdkLoader = DefaultSdkLoader.getLoader(File("/Users/beust/adt-bundle-mac-x86_64-20140702/sdk"))
        val repositories = sdkLoader.repositories
        val sdkInfo = sdkLoader.getSdkInfo(logger)
        val androidBuilder = AndroidBuilder("com.beust.kobalt", "Cedric Beust",
                processExecutor,
                javaProcessExecutor,
                KobaltErrorReporter(),
                logger,
                true /* verbose */)

        val processOutputHandler = KobaltProcessOutputHandler()
        val dir : String = KFiles.joinDir(homeDir("kotlin/kobalt-examples/android-flavors"))
        val outputDir = KFiles.joinDir(dir, "kobaltBuild", "intermediates", "res", "merged", "pro",
                "debug")
        val layout = ProjectLayout()
        val preprocessor = NoOpResourcePreprocessor()

        // AndroidTargetHash.getTargetHashString(target))
        val targetHash = "android-22"
        val buildToolsRevision = FullRevision(23, 0, 1)
        val libraryRequests = arrayListOf<LibraryRequest>()
        androidBuilder.setTargetInfo(sdkLoader.getSdkInfo(logger),
                sdkLoader.getTargetInfo(targetHash, buildToolsRevision, logger),
                libraryRequests)

        val writer = MergedResourceWriter(File(outputDir),
                androidBuilder.getAaptCruncher(processOutputHandler),
                false /* don't crunch */,
                false /* don't process 9patch */,
                layout.publicText,
                layout.mergeBlame,
                preprocessor)
        println("Repositories: $repositories")
        val target = androidBuilder.target
        val dxJar = androidBuilder.dxJar
        val resourceMerger = ResourceMerger()

        //
        // Resources
        //
        listOf("main", "free", "release").forEach {
            val path = "$dir/src/$it/res"
            val set = ResourceSet(path)
            set.addSource(File(path))
            set.loadFromFiles(logger)

            val generated = GeneratedResourceSet(set)
            set.setGeneratedSet(generated)

            resourceMerger.addDataSet(set)
        }


        resourceMerger.mergeData(writer, true)
        println("")
    }
}
