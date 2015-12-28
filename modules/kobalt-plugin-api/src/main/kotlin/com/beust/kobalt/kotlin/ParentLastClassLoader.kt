package com.beust.kobalt.kotlin

import java.net.URL
import java.net.URLClassLoader

/**
 * A parent-last classloader that will try the child classloader first and then the parent.
 * Used by the wrapper to launch a new Kobalt with not interferences from its own classes.
 * Will probably be made obsolete by making the wrapper a standalone module instead of
 * being inside Kobalt itself.
 */
public class ParentLastClassLoader(val classpath: List<URL>)
: ClassLoader(Thread.currentThread().contextClassLoader) {
    private val childClassLoader: ChildURLClassLoader

    init {
        val urls: Array<URL> = classpath.toTypedArray()
        childClassLoader = ChildURLClassLoader(urls, FindClassClassLoader(this.parent))
    }

    /**
     * This class makes it possible to call findClass on a classloader
     */
    private class FindClassClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        override public fun findClass(name: String) = super.findClass(name)
    }

    /**
     * This class delegates (child then parent) for the findClass method for a URLClassLoader.
     * We need this because findClass is protected in URLClassLoader
     */
    private class ChildURLClassLoader(urls: Array<URL>, val realParent: FindClassClassLoader)
    : URLClassLoader(urls, null) {

        override public fun findClass(name: String): Class<*> {
            try {
                // first try to use the URLClassLoader findClass
                return super.findClass(name)
            } catch(e: ClassNotFoundException) {
                // if that fails, we ask our real parent classloader to load the class (we give up)
                return realParent.loadClass(name)
            }
        }
    }

    override public @Synchronized fun loadClass(name: String, resolve: Boolean): Class<*> {
        try {
            // first we try to find a class inside the child classloader
            return childClassLoader.findClass(name)
        } catch(e: ClassNotFoundException) {
            // didn't find it, try the parent
            return super.loadClass(name, resolve)
        }
    }
}
