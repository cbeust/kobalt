package com.beust.kobalt.maven

import org.jetbrains.kotlin.rmi.toHexString
import java.io.File
import java.security.MessageDigest

public class Md5 {
    companion object {
        fun toMd5(file: File) = MessageDigest.getInstance("MD5").let { md5 ->
                file.forEachBlock { bytes, size ->
                    md5.update(bytes, 0, size)
                }
                md5.digest().toHexString()
            }
    }
}
