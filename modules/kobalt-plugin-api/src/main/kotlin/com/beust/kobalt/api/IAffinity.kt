package com.beust.kobalt.api

/**
 * Base interface for affinity interfaces.
 */
interface IAffinity {
    companion object {
        /**
         * The recommended default affinity if your plug-in can run this project. Use a higher
         * number if you expect to compete against other actors and you'd like to win over them.
         */
        const val DEFAULT_POSITIVE_AFFINITY = 100
    }
}

