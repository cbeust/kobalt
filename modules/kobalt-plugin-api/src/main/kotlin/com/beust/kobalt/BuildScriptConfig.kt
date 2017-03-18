package com.beust.kobalt

import com.beust.kobalt.api.annotation.Directive

class BuildScriptConfig {
    /** The list of repos used to locate plug-ins. */
    @Directive
    fun repos(vararg r: String) = newRepos(*r)

    /** The list of plug-ins to use for this build file. */
    @Directive
    fun plugins(vararg pl: String) = newPlugins(*pl)

    /** The build file classpath. */
    @Directive
    fun buildFileClasspath(vararg bfc: String) = newBuildFileClasspath(*bfc)

    // The following settings modify the compiler used to compile the build file.
    // Projects should use kotlinCompiler { compilerVersion } to configure the Kotin compiler for their source files.
    var kobaltCompilerVersion : String? = null
    var kobaltCompilerRepo: String? = null
    var kobaltCompilerFlags: String? = null
}