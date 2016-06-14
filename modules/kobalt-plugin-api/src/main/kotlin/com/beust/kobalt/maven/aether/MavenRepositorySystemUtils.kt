package com.beust.kobalt.maven.aether

import com.beust.kobalt.internal.KobaltSettings
import org.apache.maven.repository.internal.*
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.artifact.DefaultArtifactType
import org.eclipse.aether.impl.*
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager
import org.eclipse.aether.util.graph.selector.AndDependencySelector
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector
import org.eclipse.aether.util.graph.transformer.*
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser
import org.eclipse.aether.util.repository.DefaultProxySelector
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy

object MavenRepositorySystemUtils {

    fun newServiceLocator(): DefaultServiceLocator {
        val locator = DefaultServiceLocator()
        locator.addService(ArtifactDescriptorReader::class.java, DefaultArtifactDescriptorReader::class.java)
        locator.addService(VersionResolver::class.java, DefaultVersionResolver::class.java)
        locator.addService(VersionRangeResolver::class.java, DefaultVersionRangeResolver::class.java)
        locator.addService(MetadataGeneratorFactory::class.java, SnapshotMetadataGeneratorFactory::class.java)
        locator.addService(MetadataGeneratorFactory::class.java, VersionsMetadataGeneratorFactory::class.java)
        return locator
    }

    fun newSession(settings: KobaltSettings): DefaultRepositorySystemSession {
        val session = DefaultRepositorySystemSession()
        val depTraverser = FatArtifactTraverser()
        session.dependencyTraverser = depTraverser
        val depManager = ClassicDependencyManager()
        session.dependencyManager = depManager
        val depFilter = AndDependencySelector(*arrayOf(ScopeDependencySelector(*arrayOf("test", "provided")), OptionalDependencySelector(), ExclusionDependencySelector()))
        session.dependencySelector = depFilter
        val transformer = ConflictResolver(NearestVersionSelector(), JavaScopeSelector(), SimpleOptionalitySelector(), JavaScopeDeriver())
        ChainedDependencyGraphTransformer(*arrayOf(transformer, JavaDependencyContextRefiner()))
        session.dependencyGraphTransformer = transformer
        val stereotypes = DefaultArtifactTypeRegistry()
        stereotypes.add(DefaultArtifactType("pom"))
        stereotypes.add(DefaultArtifactType("maven-plugin", "jar", "", "java"))
        stereotypes.add(DefaultArtifactType("jar", "jar", "", "java"))
        stereotypes.add(DefaultArtifactType("ejb", "jar", "", "java"))
        stereotypes.add(DefaultArtifactType("ejb-client", "jar", "client", "java"))
        stereotypes.add(DefaultArtifactType("test-jar", "jar", "tests", "java"))
        stereotypes.add(DefaultArtifactType("javadoc", "jar", "javadoc", "java"))
        stereotypes.add(DefaultArtifactType("java-source", "jar", "sources", "java", false, false))
        stereotypes.add(DefaultArtifactType("war", "war", "", "java", false, true))
        stereotypes.add(DefaultArtifactType("ear", "ear", "", "java", false, true))
        stereotypes.add(DefaultArtifactType("rar", "rar", "", "java", false, true))
        stereotypes.add(DefaultArtifactType("par", "par", "", "java", false, true))
        session.artifactTypeRegistry = stereotypes
        session.artifactDescriptorPolicy = SimpleArtifactDescriptorPolicy(true, true)
        val sysProps = System.getProperties()
        session.setSystemProperties(sysProps)
        session.setConfigProperties(sysProps)
        settings.proxyConfigs?.let { proxyConfigs->
            session.proxySelector = DefaultProxySelector().apply {
                proxyConfigs.forEach {config->
                    add(config.toAetherProxy(), config.nonProxyHosts)
                }
            }
        }
        return session
    }
}

