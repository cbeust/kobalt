package com.beust.kobalt.internal

import com.beust.kobalt.Plugins
import com.beust.kobalt.api.BasePlugin
import com.beust.kobalt.maven.KobaltException
import java.lang.reflect.Method
import java.util.concurrent.Callable

open public class TaskResult(val success: Boolean = true, val errorMessage: String? = null)
