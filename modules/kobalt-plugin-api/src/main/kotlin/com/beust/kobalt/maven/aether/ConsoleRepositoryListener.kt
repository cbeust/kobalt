package com.beust.kobalt.maven.aether

import com.beust.kobalt.internal.eventbus.ArtifactDownloadedEvent
import com.beust.kobalt.misc.log
import com.google.common.eventbus.EventBus
import org.eclipse.aether.AbstractRepositoryListener
import org.eclipse.aether.RepositoryEvent
import java.io.PrintStream

/**
 * A simplistic repository listener that logs events to the console.
 */
class ConsoleRepositoryListener @JvmOverloads constructor(out: PrintStream? = null, val eventBus: EventBus)
        : AbstractRepositoryListener() {
    companion object {
        val LOG_LEVEL = 4
    }

    private val out: PrintStream

    init {
        this.out = out ?: System.out
    }

    override fun artifactDeployed(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Deployed " + event!!.artifact + " to " + event.repository)
    }

    override fun artifactDeploying(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Deploying " + event!!.artifact + " to " + event.repository)
    }

    override fun artifactDescriptorInvalid(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Invalid artifact descriptor for " + event!!.artifact + ": "
                + event.exception.message)
    }

    override fun artifactDescriptorMissing(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Missing artifact descriptor for " + event!!.artifact)
    }

    override fun artifactInstalled(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Installed " + event!!.artifact + " to " + event.file)
    }

    override fun artifactInstalling(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Installing " + event!!.artifact + " to " + event.file)
    }

    override fun artifactResolved(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Resolved artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactDownloading(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Downloading artifact " + event!!.artifact + " from " + event.repository)
    }

    override fun artifactDownloaded(event: RepositoryEvent?) {
        if (event?.file != null && event?.artifact != null) {
            val artifact = event!!.artifact
            log(1, "Downloaded artifact " + artifact + " from " + event.repository)
            eventBus.post(ArtifactDownloadedEvent(artifact.toString(), event.repository))
        }
    }

    override fun artifactResolving(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Resolving artifact " + event!!.artifact)
    }

    override fun metadataDeployed(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Deployed " + event!!.metadata + " to " + event.repository)
    }

    override fun metadataDeploying(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Deploying " + event!!.metadata + " to " + event.repository)
    }

    override fun metadataInstalled(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Installed " + event!!.metadata + " to " + event.file)
    }

    override fun metadataInstalling(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Installing " + event!!.metadata + " to " + event.file)
    }

    override fun metadataInvalid(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Invalid metadata " + event!!.metadata)
    }

    override fun metadataResolved(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Resolved metadata " + event!!.metadata + " from " + event.repository)
    }

    override fun metadataResolving(event: RepositoryEvent?) {
        log(LOG_LEVEL, "Resolving metadata " + event!!.metadata + " from " + event.repository)
    }

}

