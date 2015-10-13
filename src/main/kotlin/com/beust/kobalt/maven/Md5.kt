package com.beust.kobalt.maven

import com.beust.kobalt.file
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

public class Md5 {
    companion object {
        fun toMd5(file: File) = toMd5(Files.readAllBytes(Paths.get(file.toURI())))

        fun toMd5(bytes: ByteArray): String {
            val result = StringBuilder()
            val md5 = MessageDigest.getInstance("MD5").digest(bytes)
            md5.forEach {
                val byte = it.toInt() and 0xff
                if (byte < 16) result.append("0")
                result.append(Integer.toHexString(byte))
            }
            return result.toString()
        }
    }
}
