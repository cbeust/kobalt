package com.beust.kobalt.maven

public class DepFactory @Inject constructor(val localRepo: LocalRepo,
                                            val remoteRepo: RepoFinder,
                                            val executors: KobaltExecutors,
                                            val downloadManager: DownloadManager,
                                            val pomFactory: Pom.IFactory) {

    companion object {
        val defExecutor: ExecutorService by lazy {
            Kobalt.INJECTOR.getInstance(Key.get(ExecutorService::class.java, DependencyExecutor::class.java))
        }
    }

    /**
     * Parse the id and return the correct IClasspathDependency
     */
    public fun create(id: String, executor: ExecutorService = defExecutor, localFirst: Boolean = true)
            : IClasspathDependency {
        if (id.startsWith(FileDependency.PREFIX_FILE)) {
            return FileDependency(id.substring(FileDependency.PREFIX_FILE.length))
        } else {
            val mavenId = MavenId.create(id)
            var version = mavenId.version
            var packaging = mavenId.packaging
            var repoResult: RepoFinder.RepoResult?

            if (version == null || MavenId.isRangedVersion(version)) {
                var localVersion: String? = version
                if (localFirst) localVersion = localRepo.findLocalVersion(mavenId.groupId, mavenId.artifactId, mavenId.packaging)
                if (localFirst && localVersion != null) {
                    version = localVersion
                } else {
                    repoResult = remoteRepo.findCorrectRepo(id)
                    if (!repoResult.found) {
                        throw KobaltException("Couldn't resolve $id")
                    } else {
                        version = repoResult.version?.version
                    }
                }
            }

            return MavenDependency(MavenId.create(mavenId.groupId, mavenId.artifactId, packaging, version),
                    executor, localRepo, remoteRepo, pomFactory, downloadManager)
        }
    }
}
