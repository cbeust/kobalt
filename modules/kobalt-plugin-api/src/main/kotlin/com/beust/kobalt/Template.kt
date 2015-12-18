package com.beust.kobalt

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.Reader

public class Template(val reader: Reader, val writer: PrintWriter, val map: Map<String, Any>) {

    public fun execute() {
        BufferedReader(reader).let {
            it.forEachLine { line ->
                var replacedLine = line
                map.keys.forEach { key ->
                    replacedLine = replacedLine.replace("{{$key}}", map.get(key).toString(), false)
                }
                writer.append(replacedLine).append("\n")
            }
        }
    }
}
