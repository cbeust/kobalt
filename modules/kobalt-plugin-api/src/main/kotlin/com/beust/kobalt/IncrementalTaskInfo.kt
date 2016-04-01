package com.beust.kobalt

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project

/**
 * @param inputChecksum The checksum for the input to this task. It gets compared against the previous checksum
 * calculated by Kobalt. If they differ, the task gets run. If they are equal, outputChecksums are then compared.
 * @param outputChecksum The checksum for the output of this task. If null, the output is absent
 * and the task will be run. If non null, it gets compared against the checksum of the previous run and
 * if they differ, the task gets run.
 * @param task The task to run.
 */
class IncrementalTaskInfo(val inputChecksum: () -> String?,
        val outputChecksum: () -> String?,
        val task: (Project) -> TaskResult,
        val context: KobaltContext)
