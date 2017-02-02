package com.beust.kobalt.internal

/**
 * Generic operations on graph-like structures.
 */
object GraphUtil {
    /**
     * Apply the operation in `closure` to all the nodes in the tree.
     */
    fun <T> map(roots: List<T>, children: (T) -> List<T>, closure: (T) -> Unit) {
        roots.forEach {
            closure(it)
            map(children(it), children, closure)
        }
    }

    /**
     * Display each node in the roots by calling the `display` function on each of them.
     */
    fun <T> displayGraph(roots: List<T>,
            children: (T) -> List<T>,
            display: (node: T, indent: String) -> Unit) {

        fun pd(node: T, indent: String) {
            display(node, indent)
            children(node).forEach {
                pd(it, indent + "    ")
            }
        }
        roots.forEach {
            pd(it, "")
        }
    }
}

