package com.beust.kobalt.maven.aether

object Exceptions {
    fun printStackTrace(t: Throwable) {
//        t.printStackTrace(System.out)

        println("PRINT STACK TRACE FOR $t")
        println(t.message)
        t.stackTrace.forEach {
            println("   " + it)
        }
    }
}
