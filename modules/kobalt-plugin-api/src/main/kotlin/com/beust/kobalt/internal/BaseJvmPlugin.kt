package com.beust.kobalt.internal

import com.beust.kobalt.api.ConfigPlugin
import com.beust.kobalt.api.ICompilerFlagContributor

/**
 * Base class for JVM language plug-ins.
 */
abstract class BaseJvmPlugin<T>: ConfigPlugin<T>(), ICompilerFlagContributor