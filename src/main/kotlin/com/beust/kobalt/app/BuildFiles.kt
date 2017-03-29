package com.beust.kobalt.app

class BuildFiles {
    // Parse all the files found in kobalt/src/*kt, extract their buildScriptInfo blocks,
    // save the location where they appear (file, start/end).

    // Compile each of these buildScriptInfo separately, note which new build files they add
    // and at which location

    // Go back over all the files from kobalt/src/*kt, insert each new build file in it,
    // save it as a modified, concatenated build file

    // Create buildScript.jar out of compiling all these modified build files
}
