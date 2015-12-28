package com.beust.kobalt.api

import com.beust.kobalt.maven.DepFactory
import com.beust.kobalt.misc.KobaltExecutors
import java.io.File
import java.util.concurrent.Future

public class JarFinder {
    companion object {
        /**
         * @return a Future for the jar file corresponding to this id.
         */
        fun byIdFuture(id: String): Future<File> {
            val executor = Kobalt.INJECTOR.getInstance(KobaltExecutors::class.java).miscExecutor
            val depFactory = Kobalt.INJECTOR.getInstance(DepFactory::class.java)
            return depFactory.create(id, executor).jarFile
        }

        /**
         * @return the jar file corresponding to this id. This might cause a network call.
         */
        fun byId(id: String) = byIdFuture(id).get()
    }
}
