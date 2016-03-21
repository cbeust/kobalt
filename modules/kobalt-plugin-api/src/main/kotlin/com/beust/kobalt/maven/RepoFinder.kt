package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.Version
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Find the repo that contains the given dependency among a list of repos. Searches are performed in parallel and
 * cached so we never make a network call for the same dependency more than once.
 */
class RepoFinder @Inject constructor(val executors: KobaltExecutors, val finderFactory: RepoFinderCallable.IFactory) {
    fun findCorrectRepo(id: String) = FOUND_REPOS.get(id)

    /**
     * archiveUrl: full URL
     */
    data class RepoResult(val hostConfig: HostConfig, val version: Version? = null,
            val archiveUrl: String? = null, val snapshotVersion: Version? = null) {
        val found = archiveUrl != null

        val localPath = archiveUrl?.substring(hostConfig.url.length)
        // If it's a snapshot, we download a specific timestamped jar file but we need to save it under
        // the SNAPSHOT-3.2.jar name.
        val path = if (snapshotVersion != null && snapshotVersion.snapshotTimestamp != null && localPath != null) {
                val ind = localPath.indexOf(snapshotVersion.snapshotTimestamp)
                val lastDot = localPath.lastIndexOf(".")
                val result = localPath.substring(0, ind) + "SNAPSHOT" +
                    localPath.substring(lastDot)
                result
            } else {
                    localPath
            }
    }

    private val FOUND_REPOS: LoadingCache<String, RepoResult> = CacheBuilder.newBuilder()
            .build(object : CacheLoader<String, RepoResult>() {
        override fun load(key: String): RepoResult {
            return loadCorrectRepo(key)
        }})

    /**
     * Schedule an HTTP request to each repo in its own thread.
     */
    private fun loadCorrectRepo(id: String): RepoResult {
        val executor = executors.newExecutor("RepoFinder-$id", Kobalt.repos.size)
        val cs = ExecutorCompletionService<List<RepoResult>>(executor)

        val results = arrayListOf<RepoResult>()
        try {
            log(2, "Looking for $id")
            Kobalt.repos.forEach { cs.submit(finderFactory.create(id, it)) }
            for (i in 0..Kobalt.repos.size - 1) {
                try {
                    val repos = cs.take().get(2000, TimeUnit.MILLISECONDS)
                    repos.forEach { result ->
                        if (result.found) {
                            log(2, "Located $id in ${result.hostConfig.url}")
                            results.add(result)
                        } else {
                            log(3, "  Result for repo #$i: $result")
                        }
                    }
                } catch(ex: Exception) {
                    warn("Error: $ex")
                }
            }
        } finally {
            executor.shutdownNow()
        }

        if (results.size > 0) {
//            results.sortByDescending { Versions.toLongVersion(it.version) }
            results.sort({ left, right -> right.version!!.compareTo(left.version!!) })
            return results[0]
        } else {
            return RepoResult(HostConfig(""), Version.of(id))
        }
    }
}
