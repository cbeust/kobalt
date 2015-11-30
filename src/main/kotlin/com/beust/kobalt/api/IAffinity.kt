package com.beust.kobalt.api

interface IAffinity {
    companion object {
        /**
         * The recommended default affinity if your plug-in can run this project. Use a higher
         * number if you expect to compete against other actors and you'd like to win over them.
         */
        const val DEFAULT_POSITIVE_AFFINITY = 100
    }

    /**
     * @return an integer indicating the affinity of your actor for the given project. The actor that returns
     * the highest affinity gets selected.
     */
    fun affinity(project: Project, context: KobaltContext) : Int
}
