package com.beust.kobalt.api

import com.beust.kobalt.maven.MavenId

/**
 * Plug-ins can rewrite Maven id's before Kobalt sees them with this interface.
 */
interface IMavenIdInterceptor : IInterceptor {
    fun intercept(mavenId: MavenId): MavenId
}
