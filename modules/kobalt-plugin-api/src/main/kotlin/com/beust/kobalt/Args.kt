package com.beust.kobalt

import com.beust.jcommander.Parameter

class Args {
    @Parameter
    var targets: List<String> = arrayListOf()

    @Parameter(names = arrayOf("-bf", "--buildFile"), description = "The build file")
    var buildFile: String? = null

    @Parameter(names = arrayOf("--checkVersions"), description = "Check if there are any newer versions of the " +
            "dependencies")
    var checkVersions = false

    @Parameter(names = arrayOf("--client"))
    var client: Boolean = false

    @Parameter(names = arrayOf("--dev"), description = "Turn on dev mode, resulting in a more verbose log output")
    var dev: Boolean = false

    @Parameter(names = arrayOf("--download"), description = "Force a download from the downloadUrl in the wrapper")
    var download: Boolean = false

    @Parameter(names = arrayOf("--dryRun"), description = "Display all the tasks that will get run without " +
            "actually running them")
    var dryRun: Boolean = false

    @Parameter(names = arrayOf("--help", "--usage"), description = "Display the help")
    var usage: Boolean = false

    @Parameter(names = arrayOf("-i", "--init"), description = "Invoke the templates named, separated by a comma")
    var templates: String? = null

    @Parameter(names = arrayOf("--listTemplates"), description = "List the available templates")
    var listTemplates: Boolean = false

    @Parameter(names = arrayOf("--log"), description = "Define the log level (1-3)")
    var log: Int = 1

    companion object {
        const val DEFAULT_SERVER_PORT = 1234
    }

    @Parameter(names = arrayOf("--plugins"), description = "Comma-separated list of plug-in Maven id's")
    var pluginIds: String? = null

    @Parameter(names = arrayOf("--pluginJarFiles"), description = "Comma-separated list of plug-in jar files")
    var pluginJarFiles: String? = null

    @Parameter(names = arrayOf("--port"), description = "Port, if --server was specified")
    var port: Int = DEFAULT_SERVER_PORT

    @Parameter(names = arrayOf("--profiles"), description = "Comma-separated list of profiles to run")
    var profiles: String? = null

    @Parameter(names = arrayOf("--resolve"),
            description = "Resolve the given comma-separated dependencies and display their dependency tree")
    var dependencies: String? = null

    @Parameter(names = arrayOf("--projectInfo"), description = "Display information about the current projects")
    var projectInfo: Boolean = false

    @Parameter(names = arrayOf("--server"), description = "Run in server mode")
    var serverMode: Boolean = false

    @Parameter(names = arrayOf("--tasks"), description = "Display the tasks available for this build")
    var tasks: Boolean = false

    @Parameter(names = arrayOf("--update"), description = "Update to the latest version of Kobalt")
    var update: Boolean = false
}

