package com.beust.kobalt.maven.aether

import com.beust.kobalt.misc.log
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
        log(2, "Deployed " + event!!.artifact + " to " + event.repository)
    }

    override fun artifactDeploying(event: RepositoryEvent?) {
        log(2, "Deploying " + event!!.artifact + " to " + event.repository)
    }

    override fun artifactDescriptorInvalid(event: RepositoryEvent?) {
        log(2, "Invalid artifact descriptor for " + event!!.artifact + ": "
                + event.exception.message)
    }

    override fun artifactDescriptorMissing(event: RepositoryEvent?) {
        log(2, "Missing artifact descriptor for " + event!!.artifact)
    }

    override fun artifactInstalled(event: RepositoryEvent?) {
        log(2, "Installed " + event!!.artifact + " to " + event.file)
    }

    override fun artifactInstalling(event: RepositoryEvent?) {
        log(2, "Installing " + event!!.artifact + " to " + event.file)
    }

    override fun artifactResolved(event: RepositoryEvent?) {
        log(2, "Resolved artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactDownloading(event: RepositoryEvent?) {
        log(1, "Downloading artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactDownloaded(event: RepositoryEvent?) {
        log(2, "Downloaded artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactResolving(event: RepositoryEvent?) {
        log(2, "Resolving artifact " + event!!.artifact)
    }

    override fun metadataDeployed(event: RepositoryEvent?) {
        log(2, "Deployed " + event!!.metadata + " to " + event.repository)
    }

    override fun metadataDeploying(event: RepositoryEvent?) {
        log(2, "Deploying " + event!!.metadata + " to " + event.repository)
    }

    override fun metadataInstalled(event: RepositoryEvent?) {
        log(2, "Installed " + event!!.metadata + " to " + event.file)
    }

    override fun metadataInstalling(event: RepositoryEvent?) {
        log(2, "Installing " + event!!.metadata + " to " + event.file)
    }

    override fun metadataInvalid(event: RepositoryEvent?) {
        log(2, "Invalid metadata " + event!!.metadata)
    }

    override fun metadataResolved(event: RepositoryEvent?) {
        log(2, "Resolved metadata " + event!!.metadata + " from " + event.repository)
    }

    override fun metadataResolving(event: RepositoryEvent?) {
        log(2, "Resolving metadata " + event!!.metadata + " from " + event.repository)
    }

}

