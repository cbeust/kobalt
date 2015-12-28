package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.api.Kobalt
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.*
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.dom.asElementList
import kotlinx.dom.parseXml
import org.w3c.dom.NodeList
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Find the repo that contains the given dependency among a list of repos. Searches are performed in parallel and
 * cached so we never make a network call for the same dependency more than once.
 */
public class RepoFinder @Inject constructor(val executors: KobaltExecutors) {
    public fun findCorrectRepo(id: String): RepoResult {
        return FOUND_REPOS.get(id)
    }

    data class RepoResult(val hostConfig: HostConfig, val found: Boolean, val version: Version? = null,
                          val hasJar: Boolean = true, val snapshotVersion: Version? = null)

    private val FOUND_REPOS: LoadingCache<String, RepoResult> = CacheBuilder.newBuilder()
            .build(object : CacheLoader<String, RepoResult>() {
                override fun load(key: String): RepoResult {
                    return loadCorrectRepo(key)
                }
            })

    /**
     * Schedule an HTTP request to each repo in its own thread.
     */
    private fun loadCorrectRepo(id: String): RepoResult {
        val executor = executors.newExecutor("RepoFinder-$id", Kobalt.repos.size)
        val cs = ExecutorCompletionService<RepoResult>(executor)

        val results = arrayListOf<RepoResult>()
        try {
            log(2, "Looking for $id")
            Kobalt.repos.forEach { cs.submit(RepoFinderCallable(id, it)) }
            for (i in 0..Kobalt.repos.size - 1) {
                try {
                    val result = cs.take().get(2000, TimeUnit.MILLISECONDS)
                    log(2, "  Result for repo #$i: $result")
                    if (result.found) {
                        log(2, "Located $id in ${result.hostConfig.url}")
                        results.add(result)
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
            results.sort({ left, right -> left.version!!.compareTo(right.version!!) })
            return results[0]
        } else {
            return RepoResult(HostConfig(""), false, Version.of(id))
        }
    }

    /**
     * Execute a single HTTP request to one repo.
     */
    inner class RepoFinderCallable(val id: String, val repo: HostConfig) : Callable<RepoResult> {
        override fun call(): RepoResult {
            val repoUrl = repo.url
            log(2, "  Checking $repoUrl for $id")

            val mavenId = MavenId.create(id)
            val groupId = mavenId.groupId
            val artifactId = mavenId.artifactId

            if (mavenId.version == null) {
                val ud = UnversionedDep(groupId, artifactId)
                val isLocal = repoUrl.startsWith(FileDependency.PREFIX_FILE)
                val foundVersion = findCorrectVersionRelease(ud.toMetadataXmlPath(false, isLocal), repoUrl)
                if (foundVersion != null) {
                    return RepoResult(repo, true, Version.of(foundVersion))
                } else {
                    return RepoResult(repo, false)
                }
            } else {
                val version = Version.of(mavenId.version)
                if (version.isSnapshot()) {
                    val dep = SimpleDep(mavenId)
                    val isLocal = repoUrl.startsWith(FileDependency.PREFIX_FILE)
                    val metadataXmlPath = dep.toMetadataXmlPath(false, isLocal, version.version)
                    val snapshotVersion =
                            if (isLocal) version
                            else findSnapshotVersion(metadataXmlPath, repoUrl, mavenId.version)
                    if (snapshotVersion != null) {
                        val url = repoUrl + metadataXmlPath
                        val kurl = Kurl(HostConfig(url))
                        val found = kurl.exists
                        return RepoResult(repo, found, version, true /* hasJar, potential bug here */,
                                snapshotVersion)
                    } else {
                        return RepoResult(repo, false)
                    }
                } else if (version.isRangedVersion() ) {
                    val foundVersion = findRangedVersion(SimpleDep(mavenId), repoUrl)
                    if (foundVersion != null) {
                        return RepoResult(repo, true, foundVersion)
                    } else {
                        return RepoResult(repo, false)
                    }

                } else {
                    val dep = SimpleDep(mavenId)
                    // Try to find the jar file
                    val urlJar = repo.copy(url = repo.url + dep.toJarFile(dep.version))
                    val hasJar = Kurl(urlJar).exists
                    val found =
                            if (!hasJar) {
                                // No jar, try to find the directory
                                val url = repo.copy(url = repoUrl
                                        + File(dep.toJarFile(dep.version)).parentFile.path.replace("\\", "/"))
                                Kurl(url).exists
                            } else {
                                true
                            }
                    log(2, "Result for $repoUrl for $id: $found")
                    return RepoResult(repo, found, Version.of(dep.version), hasJar)
                }
            }
        }
    }

    val XPATH_FACTORY = XPathFactory.newInstance();
    val XPATH = XPATH_FACTORY.newXPath();

    fun findCorrectVersionRelease(metadataPath: String, repoUrl: String): String? {
        val XPATHS = listOf(
                XPATH.compile("/metadata/version"),
                XPATH.compile("/metadata/versioning/latest"),
                XPATH.compile("/metadata/versioning/release"))
        // No version in this dependency, find out the most recent one by parsing maven-metadata.xml, if it exists
        val url = repoUrl + metadataPath
        try {
            val doc = parseXml(url)
            arrayListOf(XPATHS.forEach {
                val result = it.evaluate(doc, XPathConstants.STRING) as String
                if (!result.isEmpty()) {
                    return result
                }
            })
        } catch(ex: Exception) {
            log(2, "Couldn't find metadata at $url: ${ex.message}")
        }
        return null
    }

    fun findRangedVersion(dep: SimpleDep, repoUrl: String): Version? {
        val l = listOf(dep.groupId.replace(".", "/"), dep.artifactId.replace(".", "/"), "maven-metadata.xml")
        var metadataPath = l.joinToString("/")

        val versionsXpath = XPATH.compile("/metadata/versioning/versions/version")

        // No version in this dependency, find out the most recent one by parsing maven-metadata.xml, if it exists
        val url = repoUrl + metadataPath
        try {
            val doc = parseXml(url)
            val version = Version.of(dep.version)
            if (version.isRangedVersion()) {
                val versions = (versionsXpath.evaluate(doc, XPathConstants.NODESET) as NodeList)
                        .asElementList().map { Version.of(it.textContent) }
                return version.select(versions)
            } else {
                return Version.of(XPATH.compile("/metadata/versioning/versions/version/$version")
                        .evaluate(doc, XPathConstants.STRING) as String)
            }
        } catch(ex: Exception) {
            log(2, "Couldn't find metadata at ${url}")
        }
        return null
    }

    fun findSnapshotVersion(metadataPath: String, repoUrl: String, snapshotVersion: String): Version? {
        val timestamp = XPATH.compile("/metadata/versioning/snapshot/timestamp")
        val buildNumber = XPATH.compile("/metadata/versioning/snapshot/buildNumber")
        // No version in this dependency, find out the most recent one by parsing maven-metadata.xml, if it exists
        val url = repoUrl + metadataPath
        try {
            val doc = parseXml(url)
            val ts = timestamp.evaluate(doc, XPathConstants.STRING)
            val bn = buildNumber.evaluate(doc, XPathConstants.STRING)
            if (!Strings.isEmpty(ts.toString()) && !Strings.isEmpty(bn.toString())) {
                return Version(snapshotVersion, ts.toString() + "-" + bn.toString())
            } else {
                val lastUpdated = XPATH.compile("/metadata/versioning/lastUpdated")
                if (!lastUpdated.toString().isEmpty()) {
                    return Version.of(lastUpdated.toString())
                }

            }
        } catch(ex: Exception) {
            log(2, "Couldn't find metadata at $url")
        }
        return null
    }


}
