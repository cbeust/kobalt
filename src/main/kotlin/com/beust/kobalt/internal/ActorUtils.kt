package com.beust.kobalt.internal

import com.beust.kobalt.api.IAffinity
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project

class ActorUtils {
    companion object {
        fun <T : IAffinity> selectAffinityActor(project: Project, context: KobaltContext,
                actors: List<T>) : T?
            = actors.maxBy { it.affinity(project, context) }
    }
}
