package com.beust.kobalt

class TestResult(val success: Boolean, val shortMessage: String? = null, val longMessage: String? = null)

open class TaskResult(val success: Boolean = true,
        val testResult: TestResult? = null,
        val errorMessage: String? = null
)
