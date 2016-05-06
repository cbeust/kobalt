package com.beust.kobalt.maven

import com.beust.kobalt.api.Kobalt
import org.eclipse.aether.artifact.DefaultArtifact

/**
 * Encapsulate a Maven id captured in one string, as used by Gradle or Ivy, e.g. "org.testng:testng:6.9.9".
 * These id's are somewhat painful to manipulate because on top of containing groupId, artifactId
 * and version, they also accept an optional packaging (e.g. "aar") and qualifier (e.g. "no_aop").
 * Determining which is which in an untyped string separated by colons is not clearly defined so
 * this class does a best attempt at deconstructing an id but there's surely room for improvement.
 *
 * This class accepts a versionless id, which needs to end with a :, e.g. "com.beust:jcommander:" (which
 * usually means "latest version") but it doesn't handle version ranges yet.
 */
class MavenId private constructor(val groupId: String, val artifactId: String, val packaging: String?,
        val classifier: String?, val version: String?) {

    companion object {
        fun isMavenId(id: String) = with(id.split(":")) {
            size >= 3 && size <= 5
        }

        fun isRangedVersion(s: String): Boolean {
            return s.first() in listOf('[', '(') && s.last() in listOf(']', ')')
        }

        /**
         * Similar to create(MavenId) but don't run IMavenIdInterceptors.
         */
        fun createNoInterceptors(id: String) : MavenId = DefaultArtifact(id).run {
                MavenId(groupId, artifactId, extension, classifier, version)
            }

        fun toKobaltId(id: String) = if (id.endsWith(":")) id + "(0,]" else id

        /**
         * The main entry point to create Maven Id's. Id's created by this function
         * will run through IMavenIdInterceptors.
         */
        fun create(originalId: String) : MavenId {
            val id = toKobaltId(originalId)
            var originalMavenId = createNoInterceptors(id)
            var interceptedMavenId = originalMavenId
            val interceptors = Kobalt.context?.pluginInfo?.mavenIdInterceptors
            if (interceptors != null) {
                interceptedMavenId = interceptors.fold(originalMavenId, {
                            id, interceptor -> interceptor.intercept(id) })
            }

            return interceptedMavenId
        }

        fun create(groupId: String, artifactId: String, packaging: String?, classifier: String?, version: String?) =
               create(toId(groupId, artifactId, packaging, classifier, version))

        fun toId(groupId: String, artifactId: String, packaging: String? = null, classifier: String? = null, version: String?) =
                "$groupId:$artifactId" +
                    (if (packaging != null && packaging != "") ":$packaging" else "") +
                    (if (classifier != null && classifier != "") ":$classifier" else "") +
                    ":$version"
    }


    val hasVersion = version != null

    val toId = MavenId.toId(groupId, artifactId, packaging, classifier, version)

}
