package com.beust.kobalt.api

import com.beust.kobalt.misc.ToString

data public class Task(val pluginName: String, val taskName: String) {
    override public fun toString() : String {
        return ToString("Task", pluginName, taskName).s
    }
}