package com.beust.kobalt

import com.beust.kobalt.misc.KFiles

object Constants {
    const val LOG_DEFAULT_LEVEL = 1
    const val LOG_MAX_LEVEL = 3
    val BUILD_FILE_NAME = "Build.kt"
    val BUILD_FILE_DIRECTORY = "kobalt/src"
    val BUILD_FILE_PATH = KFiles.joinDir(BUILD_FILE_DIRECTORY, BUILD_FILE_NAME)
    val KOTLIN_COMPILER_VERSION = "1.1.0-beta-22"

    internal val DEFAULT_REPOS = listOf<String>(
            //            "https://maven-central.storage.googleapis.com/",
            "http://repo1.maven.org/maven2/",
            "https://jcenter.bintray.com/",
            "http://repository.jetbrains.com/all/",
            "https://dl.bintray.com/kotlin/kotlin-eap",
            "https://dl.bintray.com/kotlin/kotlin-eap-1.1"

            // snapshots
//            "https://oss.sonatype.org/content/repositories/snapshots/"
//            , "https://repository.jboss.org/nexus/content/repositories/root_repository/"
    )

}
