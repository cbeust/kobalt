package com.beust.kobalt

import com.beust.kobalt.misc.KFiles

object Constants {
    val BUILD_FILE_NAME = "Build.kt"
    val BUILD_FILE_DIRECTORY = "kobalt/src"
    val BUILD_FILE_PATH = KFiles.joinDir(BUILD_FILE_DIRECTORY, BUILD_FILE_NAME)

    internal val DEFAULT_REPOS = listOf<String>(
            "http://repo1.maven.org/maven2/",
            "https://maven-central.storage.googleapis.com/",
            "https://jcenter.bintray.com/",

            // snapshots
            "https://oss.sonatype.org/content/repositories/snapshots/"

            // The following repos contain snapshots, don't include them by default
//            , "https://repository.jboss.org/nexus/content/repositories/root_repository/"
//            , "http://repository.jetbrains.com/all/"
    )

}
