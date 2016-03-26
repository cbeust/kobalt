package com.beust.kobalt.api

import com.beust.kobalt.maven.DependencyManager
import java.io.File
import java.util.concurrent.Future

class JarFinder {
    companion object {
        /**
         * @return a Future for the jar file corresponding to this id.
         */
        fun byIdFuture(id: String) : Future<File> = DependencyManager.create(id).jarFile

        /**
         * @return the jar file corresponding to this id. This might cause a network call.
         */
        fun byId(id: String) = byIdFuture(id).get()
    }
}
