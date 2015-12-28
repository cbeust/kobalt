package com.beust.kobalt.api

/**
 * Plug-ins can alter what is passed to the compiler by implementing this interface.
 */
interface ICompilerInterceptor : IInterceptor {
    fun intercept(project: Project, context: KobaltContext, actionInfo: CompilerActionInfo): CompilerActionInfo
}
