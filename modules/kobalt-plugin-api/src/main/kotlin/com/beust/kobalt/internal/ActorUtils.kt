package com.beust.kobalt.internal

import com.beust.kobalt.api.IProjectAffinity
import com.beust.kobalt.api.ISimpleAffinity
import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project

class ActorUtils {
    companion object {
        /**
         * Return the plug-in actor with the highest affinity.
         */
        fun <T : IProjectAffinity> selectAffinityActor(project: Project, context: KobaltContext, actors: List<T>)
            = actors.maxBy { it.affinity(project, context) }

        /**
         * Return the plug-in actor with the highest affinity.
         */
        fun <T : ISimpleAffinity<A>, A> selectAffinityActor(actors: List<T>, arg: A) = actors.maxBy { it.affinity(arg) }
    }

}
