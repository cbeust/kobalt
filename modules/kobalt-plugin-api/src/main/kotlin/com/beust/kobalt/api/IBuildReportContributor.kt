package com.beust.kobalt.api

/**
 * Plug-ins that produce build reports.
 */
interface IBuildReportContributor : IContributor {
    fun generateReport(context: KobaltContext)
}
