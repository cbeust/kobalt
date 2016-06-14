package com.beust.kobalt.maven.aether

import com.beust.kobalt.internal.KobaltSettings
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.repository.LocalRepository
import java.io.File

object Booter {

    fun newRepositorySystem(): RepositorySystem {
        return ManualRepositorySystemFactory.newRepositorySystem()
        // return org.eclipse.aether.examples.guice.GuiceRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.sisu.SisuRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.plexus.PlexusRepositorySystemFactory.newRepositorySystem();
    }

    fun newRepositorySystemSession(system: RepositorySystem, repo: File, settings: KobaltSettings): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession(settings)

        val localRepo = LocalRepository(repo.absolutePath)
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)

        session.transferListener = ConsoleTransferListener()
        session.repositoryListener = ConsoleRepositoryListener()

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session
    }

//    fun newRepositories(repositories: Collection<String>)
//            = repositories.map { RemoteRepository.Builder("maven", "default", it).build() }
}

