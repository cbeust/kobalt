package com.beust.kobalt.maven

import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.misc.KobaltExecutors
import com.beust.kobalt.misc.Strings
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.dom.parseXml

/**
 * Find the repo that contains the given dependency among a list of repos. Searches are performed in parallel and
 * cached so we never make a network call for the same dependency more than once.
 */
public class RepoFinder @Inject constructor(val http: Http, val executors: KobaltExecutors) {
    public fun findCorrectRepo(id: String): RepoResult {
        return FOUND_REPOS.get(id)
    }

    data class RepoResult(val repoUrl: String, val found: Boolean, val version: String, val hasJar: Boolean = true,
            val snapshotVersion: String = "")

    private val FOUND_REPOS: LoadingCache<String, RepoResult> = CacheBuilder.newBuilder()
            .build(object : CacheLoader<String, RepoResult>() {
        override fun load(key: String): RepoResult {
            return loadCorrectRepo(key)
        }})

    /**
     * Schedule an HTTP request to each repo in its own thread.
     */
    private fun loadCorrectRepo(id: String): RepoResult {
        val executor = executors.newExecutor("RepoFinder-${id}", Kobalt.repos.size)
        val cs = ExecutorCompletionService<RepoResult>(executor)

        try {
            log(2, "Looking for $id")
            Kobalt.repos.forEach { cs.submit(RepoFinderCallable(id, it)) }
            for (i in 0..Kobalt.repos.size - 1) {
                try {
                    val result = cs.take().get(2000, TimeUnit.MILLISECONDS)
                    log(2, "Result for repo #$i: $result")
                    if (result.found) {
                        log(2, "Located $id in ${result.repoUrl}")
                        return result
                    }
                } catch(ex: Exception) {
                    warn("Error: $ex")
                }
            }
            return RepoResult("", false, id)
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Execute a single HTTP request to one repo.
     */

    inner class RepoFinderCallable(val id: String, val repoUrl: String) : Callable<RepoResult> {
        override fun call(): RepoResult {
            log(2, "Checking $repoUrl for $id")

            val c = id.split(":")
            if (! MavenDependency.hasVersion(id)) {
                val ud = UnversionedDep(c[0], c[1])
                val foundVersion = findCorrectVersionRelease(ud.toMetadataXmlPath(false), repoUrl)
                if (foundVersion != null) {
                    return RepoResult(repoUrl, true, foundVersion)
                } else {
                    return RepoResult(repoUrl, false, "")
                }
            } else {
                if (c[2].contains("SNAPSHOT")) {
                    val dep = SimpleDep(c[0], c[1], c[2])
                    val snapshotVersion = findSnapshotVersion(dep.toMetadataXmlPath(false), repoUrl)
                    if (snapshotVersion != null) {
                        return RepoResult(repoUrl, true, c[2], true /* hasJar, potential bug here */, snapshotVersion)
                    } else {
                        return RepoResult(repoUrl, false, "")
                    }
                } else {
                    val dep = SimpleDep(c[0], c[1], c[2])
                    // Try to find the jar file
                    val urlJar = repoUrl + dep.toJarFile(dep.version)
                    val hasJar = http.get(urlJar).code == 200

                    val found =
                        if (! hasJar) {
                            // No jar, try to find the directory
                            val url = repoUrl + File(dep.toJarFile(dep.version)).parentFile.path
                            http.get(url).code == 200
                        } else {
                            true
                        }
                    log(2, "Result for $repoUrl for $id: $found")
                    return RepoResult(repoUrl, found, dep.version, hasJar)
                }
            }
        }
    }

    val XPATH_FACTORY = XPathFactory.newInstance();
    val XPATH = XPATH_FACTORY.newXPath();

    fun findCorrectVersionRelease(metadataPath: String, repoUrl: String): String? {
        val XPATHS = arrayListOf(
                XPATH.compile("/metadata/version"),
                XPATH.compile("/metadata/versioning/latest"),
                XPATH.compile("/metadata/versioning/release"))
        // No version in this dependency, find out the most recent one by parsing maven-metadata.xml, if it exists
        val url = repoUrl + metadataPath
        try {
            val doc = parseXml(url)
            arrayListOf(XPATHS.forEach {
                val result = it.evaluate(doc, XPathConstants.STRING) as String
                if (! result.isEmpty()) {
                    return result
                }
            })
        } catch(ex: Exception) {
            log(2, "Couldn't find metadata at $url")
        }
        return null
    }

    fun findSnapshotVersion(metadataPath: String, repoUrl: String): String? {
        val timestamp = XPATH.compile("/metadata/versioning/snapshot/timestamp")
        val buildNumber = XPATH.compile("/metadata/versioning/snapshot/buildNumber")
        // No version in this dependency, find out the most recent one by parsing maven-metadata.xml, if it exists
        val url = repoUrl + metadataPath
        try {
            val doc = parseXml(url)
            val ts = timestamp.evaluate(doc, XPathConstants.STRING)
            val bn = buildNumber.evaluate(doc, XPathConstants.STRING)
            if (! Strings.isEmpty(ts.toString()) && ! Strings.isEmpty(bn.toString())) {
                return ts.toString() + "-" + bn.toString()
            }
        } catch(ex: Exception) {
            log(2, "Couldn't find metadata at $url")
        }
        return null
    }



}
