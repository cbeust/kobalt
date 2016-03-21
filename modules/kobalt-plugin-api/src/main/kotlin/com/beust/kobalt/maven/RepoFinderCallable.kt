package com.beust.kobalt.maven

import com.beust.kobalt.HostConfig
import com.beust.kobalt.maven.dependency.FileDependency
import com.beust.kobalt.misc.Version
import com.beust.kobalt.misc.log
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import kotlinx.dom.asElementList
import kotlinx.dom.parseXml
import org.w3c.dom.NodeList
import java.io.File
import java.util.concurrent.Callable
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Execute a single HTTP request to one repo. This Callable can return more than one RepoResult
 * if the artifact we're tying to locate is a container pom (in which case, we'll return one
 * positive RepoResult for each of the artifacts listed in that .pom file). For example:
 * http://repo1.maven.org/maven2/nl/komponents/kovenant/kovenant/3.0.0/
 */
class RepoFinderCallable @Inject constructor(@Assisted val id: String,
        @Assisted val repo: HostConfig, val localRepo: LocalRepo, val pomFactory: Pom.IFactory)
            : Callable<List<RepoFinder .RepoResult>> {

    interface IFactory {
        fun create(@Assisted id: String, @Assisted repo: HostConfig) : RepoFinderCallable
    }

    override fun call(): List<RepoFinder.RepoResult> {
        val repoUrl = repo.url
        log(2, "  Checking $repoUrl for $id")

        val mavenId = MavenId.create(id)
        val groupId = mavenId.groupId
        val artifactId = mavenId.artifactId

        if (mavenId.version == null) {
            val ud = UnversionedDep(groupId, artifactId)
            val isLocal = repoUrl.startsWith(FileDependency.PREFIX_FILE)
            val path = ud.toMetadataXmlPath(false, isLocal)
            val foundVersion = findCorrectVersionRelease(path, repoUrl)
            // When looking up a versionless id, never return a SNAPSHOT
            if (foundVersion != null && ! foundVersion.contains("SNAPSHOT")) {
                return listOf(RepoFinder.RepoResult(repo, Version.of(foundVersion), repoUrl + path))
            } else {
                return listOf(RepoFinder.RepoResult(repo))
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
                    val url = repoUrl + dep.toDirectory(fileSystem = false, v = dep.version) +
                            dep.artifactId + "-" + snapshotVersion.noSnapshotVersion +
                            "-" + snapshotVersion.snapshotTimestamp + ".jar"
                    return listOf(RepoFinder.RepoResult(repo, version, url, snapshotVersion))
                } else {
                    return listOf(RepoFinder.RepoResult(repo))
                }
            } else if (version.isRangedVersion() ) {
                val foundVersion = findRangedVersion(SimpleDep(mavenId), repoUrl)
                if (foundVersion != null) {
                    return listOf(RepoFinder.RepoResult(repo, foundVersion))
                } else {
                    return listOf(RepoFinder.RepoResult(repo))
                }

            } else {
                val dep = SimpleDep(mavenId)
                // Try to find the jar file
                val depPomFile = dep.toPomFile(dep.version)
                val attemptPaths = listOf(dep.toJarFile(dep.version), dep.toAarFile(dep.version), depPomFile)
                val attemptUrls = attemptPaths.map { repo.copy(url = repo.url + it )} +
                        attemptPaths.map { repo.copy(url = repo.url + File(it).parentFile.path.replace("\\", "/")) }

                val firstFound = attemptUrls.map { Kurl(it)}.firstOrNull { it.exists }
                if (firstFound != null) {
                    val url = firstFound.hostInfo.url
                    if (url.endsWith("ar")) {
                        log(3, "Result for $repoUrl for $id: $firstFound")
                        return listOf(RepoFinder.RepoResult(repo, Version.of(dep.version), firstFound.hostInfo.url))
                    } else if (url.endsWith(".pom")) {
                        log(2, "Found container pom: " + firstFound)
                        File(localRepo.toFullPath(depPomFile)).let { pomFile ->
                            pomFile.parentFile.mkdirs()
                            Kurl(HostConfig(url)).toFile(pomFile)
                            val dependencies = pomFactory.create(id, pomFile).dependencies
                            val result = arrayListOf<RepoFinder.RepoResult>()
                            dependencies.map { it.id }.forEach {
                                result.addAll(RepoFinderCallable(it, repo, localRepo, pomFactory).call())
                            }
                            return result
                        }
                    } else {
                        return listOf(RepoFinder.RepoResult(repo, Version.of(dep.version), firstFound.hostInfo.url))
                    }
                } else {
                    log(3, "Couldn't find $dep on $repoUrl")
                    return emptyList()
                }
            }
        }
    }

    val XPATH_FACTORY = XPathFactory.newInstance();
    val XPATH = XPATH_FACTORY.newXPath();

    private fun findCorrectVersionRelease(metadataPath: String, repoUrl: String): String? {
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
                if (! result.isEmpty()) {
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
        var metadataPath =  l.joinToString("/")

        val versionsXpath = XPATH.compile("/metadata/versioning/versions/version")

        // No version in this dependency, find out the most recent one by parsing maven-metadata.xml, if it exists
        val url = repoUrl + metadataPath
        try {
            val doc = parseXml(url)
            val version = Version.of(dep.version)
            if(version.isRangedVersion()) {
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
            if (! ts.toString().isEmpty() && ! bn.toString().isEmpty()) {
                return Version(snapshotVersion, ts.toString() + "-" + bn.toString())
            } else {
                val lastUpdated = XPATH.compile("/metadata/versioning/lastUpdated")
                if (! lastUpdated.toString().isEmpty()) {
                    return Version.of(lastUpdated.toString())
                }

            }
        } catch(ex: Exception) {
            log(2, "Couldn't find metadata at $url")
        }
        return null
    }

}