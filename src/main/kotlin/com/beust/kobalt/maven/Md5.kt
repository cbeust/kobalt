package com.beust.kobalt.maven

import java.io.File
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

public class Md5 {
    companion object {
        fun toMd5(file: File) = MessageDigest.getInstance("MD5").let { md5 ->
                file.forEachBlock { bytes, size ->
                    md5.update(bytes, 0, size)
                }
                DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()
            }
    }
}
