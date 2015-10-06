package com.beust.kobalt

import com.beust.jcommander.Parameter

class Args {
    @Parameter
    var targets: List<String> = arrayListOf()

    @Parameter(names = arrayOf("-bf", "--buildFile"), description = "The build file")
    var buildFile: String? = null

    @Parameter(names = arrayOf("--dryRun"), description = "Display all the tasks that will get run without " +
            "actually running them")
    var dryRun: Boolean = false

    @Parameter(names = arrayOf("--tasks"), description = "Display the tasks available for this build")
    var tasks: Boolean = false

    @Parameter(names = arrayOf("--log"), description = "Define the log level (1-3)")
    var log: Int = 1

    @Parameter(names = arrayOf("-i", "--init"), description = "Create a new build file based on the current project")
    var init: Boolean = false

    @Parameter(names = arrayOf("--checkVersions"), description = "Check if there are any newer versions of the " +
            "dependencies")
    var checkVersions = false
}

