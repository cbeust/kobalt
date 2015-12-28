package com.beust.kobalt.api

import com.beust.kobalt.misc.toString

data public class Task(val pluginName: String, val taskName: String) {
    override public fun toString(): String {
        return toString("Task", pluginName, taskName)
    }
}