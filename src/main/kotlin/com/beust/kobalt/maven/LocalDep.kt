package com.beust.kobalt.maven

import com.beust.kobalt.maven.CompletedFuture
import com.beust.kobalt.misc.Strings
import java.io.File
import java.util.concurrent.Future
import kotlin.properties.Delegates

open public class LocalDep(override val groupId: String, override val artifactId: String,
        override val packaging: String?, override val version: String,
        open val localRepo: LocalRepo) : SimpleDep(groupId, artifactId, packaging, version) {

    fun toAbsoluteJarFilePath(v: String) = localRepo.toFullPath(toJarFile(v))

    fun toAbsolutePomFile(v: String): String {
        return localRepo.toFullPath(Strings.Companion.join("/", arrayListOf(toPomFile(v))))
    }

}
