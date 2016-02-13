package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.inject.assistedinject.Assisted
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the download of files from a given HostConfig.
 */
@Singleton
class DownloadManager @Inject constructor(val factory: ArtifactFetcher.IFactory) {
    class Key(val hostInfo: HostConfig, val fileName: String, val executor: ExecutorService) {
        override fun equals(other: Any?): Boolean {
            return (other as Key).hostInfo.url == hostInfo.url
        }

        override fun hashCode(): Int {
            return hostInfo.url.hashCode()
        }
    }

    private val CACHE : LoadingCache<Key, Future<File>> = CacheBuilder.newBuilder()
        .build(object : CacheLoader<Key, Future<File>>() {
            override fun load(key: Key): Future<File> {
                return key.executor.submit(factory.create(key.hostInfo, key.fileName))
            }
        })

    fun download(hostInfo: HostConfig, fileName: String, executor: ExecutorService)
            : Future<File> = CACHE.get(Key(hostInfo, fileName, executor))
}

/**
 * Fetches an artifact (a file in a Maven repo, .jar, -javadoc.jar, ...) to the given local file.
 */
class ArtifactFetcher @Inject constructor(@Assisted("hostInfo") val hostInfo: HostConfig,
        @Assisted("fileName") val fileName: String,
        val files: KFiles) : Callable<File> {
    interface IFactory {
        fun create(@Assisted("hostInfo") hostInfo: HostConfig, @Assisted("fileName") fileName: String) : ArtifactFetcher
    }

    override fun call() : File {
        val k = Kurl(hostInfo.copy(url = hostInfo.url + ".md5"))
        val remoteMd5 =
            if (k.exists) k.string.trim(' ', '\t', '\n').substring(0, 32)
            else null

        val tmpFile = Paths.get(fileName + ".tmp")
        val file = Paths.get(fileName)
        val url = hostInfo.url
        if (! Files.exists(file)) {
            with(tmpFile.toFile()) {
                parentFile.mkdirs()
                Kurl(hostInfo).toFile(this)
            }
            log(2, "Done downloading, renaming $tmpFile to $file")
            Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING)
            log(1, "  Downloaded $url")
            log(2, "     to $file")
        }

        val localMd5 = Md5.toMd5(file.toFile())
        if (remoteMd5 != null && remoteMd5 != localMd5) {
            warn("MD5 not matching for $url")
        } else {
            log(2, "No md5 found for $url, skipping md5 check")
        }

        return file.toFile()
    }
}
