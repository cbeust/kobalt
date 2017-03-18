package com.beust.kobalt.plugin.apt

import com.beust.kobalt.api.annotation.Directive

class KaptConfig(@Directive var outputDir: String = "generated/source/apt")