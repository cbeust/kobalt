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
    private val nodes = hashSetOf<T>()

    fun addNode(t: T) = nodes.add(t)

    fun addEdge(t: T, other: T) {
        addNode(t)
        addNode(other)
        dependingOn.put(t, other)
    }

    /**
     * @return the Ts sorted topologically.
     */
    fun sort() : List<T> {
        val all = ArrayList<T>(nodes)
        val result = arrayListOf<T>()
        var dependMap = HashMultimap.create<T, T>()
        dependingOn.keySet().forEach { dependMap.putAll(it, dependingOn.get(it))}
        nodes.forEach { dependMap.putAll(it, emptyList())}
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