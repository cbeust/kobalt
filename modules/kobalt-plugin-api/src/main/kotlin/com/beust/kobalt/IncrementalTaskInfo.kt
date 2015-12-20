package com.beust.kobalt

import com.beust.kobalt.api.Project

class IncrementalTaskInfo(val inputChecksum: String, val outputChecksum: String, task: (Project) -> TaskResult)
