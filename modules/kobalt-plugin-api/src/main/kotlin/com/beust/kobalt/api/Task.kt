package com.beust.kobalt.api

import com.beust.kobalt.misc.toString

data class Task(val pluginName: String, val taskName: String) {
    override fun toString() = toString("Task", pluginName, taskName)
}
