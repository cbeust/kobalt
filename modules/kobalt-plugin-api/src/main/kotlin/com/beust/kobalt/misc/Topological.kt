package com.beust.kobalt.misc

import com.beust.kobalt.KobaltException
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashMultimap
import java.util.*

/**
 * Sort items topologically. These items need to have overridden hashCode() and equals().
 */
class Topological<T> {
    private val dependingOn = ArrayListMultimap.create<T, T>()

    fun addEdge(t: T, other: T) {
        dependingOn.put(t, other)
    }

    fun addEdge(t: T, others: Array<out T>) {
        dependingOn.putAll(t, others.toArrayList())
    }

    /**
     * @return the Ts sorted topologically.
     */
    fun sort(all: ArrayList<T>): List<T> {
        val result = arrayListOf<T>()
        var dependMap = HashMultimap.create<T, T>()
        dependingOn.keySet().forEach { dependMap.putAll(it, dependingOn.get(it)) }
        while (all.size > 0) {
            val freeNodes = all.filter {
                dependMap.get(it).isEmpty()
            }
            if (freeNodes.isEmpty()) {
                throw KobaltException("The dependency graph has a cycle: $all")
            }
            result.addAll(freeNodes)
            all.removeAll(freeNodes)
            val newMap = HashMultimap.create<T, T>()
            dependMap.keySet().forEach {
                val l = dependingOn.get(it)
                l.removeAll(freeNodes)
                newMap.putAll(it, l)
            }
            dependMap = newMap
        }
        return result
    }
}