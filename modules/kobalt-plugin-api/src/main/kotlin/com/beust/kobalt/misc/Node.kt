package com.beust.kobalt.misc

public data class Node<T>(val value: T) {
    val children = arrayListOf<Node<T>>()
    var parent: Node<T>? = null

    public fun addChildren(values: List<Node<T>>) {
        values.forEach {
            it.parent = this
            children.add(it)
        }
    }

    private fun p(s: String) {
        println(s)
    }

    public fun dump(r: T, children: List<Node<T>>, indent: Int) {
        p(" ".repeat(indent) + r)
        children.forEach { dump(it.value, it.children, indent + 2) }
    }

    public fun dump(indent: Int = 0) {
        dump(value, children, indent)
    }
}
