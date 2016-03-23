package com.beust.kobalt.maven.aether

import org.eclipse.aether.AbstractRepositoryListener
import org.eclipse.aether.RepositoryEvent
import java.io.PrintStream

/**
 * A simplistic repository listener that logs events to the console.
 */
class ConsoleRepositoryListener @JvmOverloads constructor(out: PrintStream? = null) : AbstractRepositoryListener() {

    private val out: PrintStream

    init {
        this.out = out ?: System.out
    }

    override fun artifactDeployed(event: RepositoryEvent?) {
        out.println("Deployed " + event!!.artifact + " to " + event.repository)
    }

    override fun artifactDeploying(event: RepositoryEvent?) {
        out.println("Deploying " + event!!.artifact + " to " + event.repository)
    }

    override fun artifactDescriptorInvalid(event: RepositoryEvent?) {
        out.println("Invalid artifact descriptor for " + event!!.artifact + ": "
                + event.exception.message)
    }

    override fun artifactDescriptorMissing(event: RepositoryEvent?) {
        out.println("Missing artifact descriptor for " + event!!.artifact)
    }

    override fun artifactInstalled(event: RepositoryEvent?) {
        out.println("Installed " + event!!.artifact + " to " + event.file)
    }

    override fun artifactInstalling(event: RepositoryEvent?) {
        out.println("Installing " + event!!.artifact + " to " + event.file)
    }

    override fun artifactResolved(event: RepositoryEvent?) {
        out.println("Resolved artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactDownloading(event: RepositoryEvent?) {
        out.println("Downloading artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactDownloaded(event: RepositoryEvent?) {
        out.println("Downloaded artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactResolving(event: RepositoryEvent?) {
        out.println("Resolving artifact " + event!!.artifact)
    }

    override fun metadataDeployed(event: RepositoryEvent?) {
        out.println("Deployed " + event!!.metadata + " to " + event.repository)
    }

    override fun metadataDeploying(event: RepositoryEvent?) {
        out.println("Deploying " + event!!.metadata + " to " + event.repository)
    }

    override fun metadataInstalled(event: RepositoryEvent?) {
        out.println("Installed " + event!!.metadata + " to " + event.file)
    }

    override fun metadataInstalling(event: RepositoryEvent?) {
        out.println("Installing " + event!!.metadata + " to " + event.file)
    }

    override fun metadataInvalid(event: RepositoryEvent?) {
        out.println("Invalid metadata " + event!!.metadata)
    }

    override fun metadataResolved(event: RepositoryEvent?) {
        out.println("Resolved metadata " + event!!.metadata + " from " + event.repository)
    }

    override fun metadataResolving(event: RepositoryEvent?) {
        out.println("Resolving metadata " + event!!.metadata + " from " + event.repository)
    }

}

