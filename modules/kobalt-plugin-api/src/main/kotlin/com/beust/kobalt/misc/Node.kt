package com.beust.kobalt.misc

data class Node<T>(val value: T) {
    val children = arrayListOf<Node<T>>()
    var parent: Node<T>? = null

    fun addChildren(values: List<Node<T>>) {
        values.forEach {
            it.parent = this
            children.add(it)
        }
    }

    private fun p(s: String) {
        kobaltLog(1, s)
    }

    fun dump(r: T, children: List<Node<T>>, indent: Int) {
        p(" ".repeat(indent) + r)
        children.forEach { dump(it.value, it.children, indent + 2) }
    }

    fun dump(indent: Int = 0) {
        dump(value, children, indent)
    }
}
