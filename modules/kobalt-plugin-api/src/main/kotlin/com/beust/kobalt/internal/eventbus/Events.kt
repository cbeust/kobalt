package com.beust.kobalt.internal.eventbus

import org.eclipse.aether.repository.ArtifactRepository

class ArtifactDownloadedEvent(val artifactId: String, val repository: ArtifactRepository)
