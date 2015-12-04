package com.beust.kobalt.plugin.android

import com.android.builder.core.AndroidBuilder
import com.android.builder.core.ErrorReporter
import com.android.builder.core.LibraryRequest
import com.android.builder.dependency.ManifestDependency
import com.android.builder.model.SyncIssue
import com.android.builder.sdk.DefaultSdkLoader
import com.android.builder.sdk.SdkLoader
import com.android.ide.common.blame.Message
import com.android.ide.common.process.*
import com.android.ide.common.res2.*
import com.android.manifmerger.ManifestMerger2
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkManager
import com.android.utils.StdLogger
import com.beust.kobalt.Variant
import com.beust.kobalt.api.Project
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
//    val annotationsJar = File("/Users/beust/adt-bundle-mac-x86_64-20140702/sdk/tools/lib/annotations.jar")
//    val adb = File("/Users/beust/adt-bundle-mac-x86_64-20140702/sdk/platform-tools/adb")

    fun run(project: Project, variant: Variant, config: AndroidConfig) {
        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val processExecutor = DefaultProcessExecutor(logger)
        val javaProcessExecutor = KobaltJavaProcessExecutor()
        val androidHome = File(AndroidFiles.androidHome(project, config))
        val sdkLoader : SdkLoader = DefaultSdkLoader.getLoader(androidHome)
        val androidBuilder = AndroidBuilder(project.name, "kobalt-android-plugin",
                processExecutor,
                javaProcessExecutor,
                KobaltErrorReporter(),
                logger,
                false /* verbose */)

        val processOutputHandler = KobaltProcessOutputHandler()
        val dir : String = project.directory
        val outputDir = AndroidFiles.mergedResources(project, variant)
        val layout = ProjectLayout()
        val preprocessor = NoOpResourcePreprocessor()
        val libraryRequests = arrayListOf<LibraryRequest>()
        val sdk = sdkLoader.getSdkInfo(logger)
        val sdkManager = SdkManager.createManager(androidHome.absolutePath, logger)
        val maxPlatformTarget = sdkManager.targets.filter { it.isPlatform }.last()
        val maxPlatformTargetHash = AndroidTargetHash.getPlatformHashString(maxPlatformTarget.version)

        androidBuilder.setTargetInfo(sdk,
                sdkLoader.getTargetInfo(maxPlatformTargetHash, maxPlatformTarget.buildToolInfo.revision, logger),
                libraryRequests)

        val writer = MergedResourceWriter(File(outputDir),
                androidBuilder.getAaptCruncher(processOutputHandler),
                false /* don't crunch */,
                false /* don't process 9patch */,
                layout.publicText,
                layout.mergeBlame,
                preprocessor)
        val target = androidBuilder.target
        val dxJar = androidBuilder.dxJar
        val resourceMerger = ResourceMerger()

        //
        // Manifest
        //
        val mainManifest = File("src/main/AndroidManifest.xml")

        val appInfo = AppInfo(mainManifest, config)
        val manifestOverlays = listOf<File>()
        val libraries = listOf<ManifestDependency>()
        val outManifest = AndroidFiles.mergedManifest(project, variant)
        val outAaptSafeManifestLocation = KFiles.joinDir(project.directory, project.buildDirectory, "generatedSafeAapt")
        val reportFile = File(KFiles.joinDir(project.directory, project.buildDirectory, "manifest-merger-report.txt"))
        androidBuilder.mergeManifests(mainManifest, manifestOverlays, libraries,
                null /* package override */,
                appInfo.versionCode,
                appInfo.versionName,
                appInfo.minSdkVersion,
                appInfo.targetSdkVersion,
                23,
                outManifest,
                outAaptSafeManifestLocation,
                ManifestMerger2.MergeType.APPLICATION,
                emptyMap(),
                reportFile)

        //
        // Resources
        //
        listOf("main", variant.productFlavor.name, variant.buildType.name).forEach {
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
