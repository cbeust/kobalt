package com.beust.kobalt.maven

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

public class Md5 {
    companion object {
        fun toMd5(file: File) =
                MessageDigest.getInstance("MD5").let { md5 ->
                    file.forEachBlock { bytes, size ->
                        md5.update(bytes, 0, size)
                    }
                    md5.digest().toHex()
                }

        fun toMd5(bytes: ByteArray): String =
                MessageDigest.getInstance("MD5").digest(bytes).toHex()
    }
}

private fun ByteArray.toHex() = buildString {
    forEach {
        val byte = it.toInt() and 0xff
        if (byte < 16) append("0")
        append(Integer.toHexString(byte))
    }
}