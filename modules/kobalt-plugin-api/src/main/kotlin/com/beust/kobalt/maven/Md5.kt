package com.beust.kobalt.maven

import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

public class Md5 {
    companion object {
        fun toMd5Directories(directories: List<File>) : String {
            MessageDigest.getInstance("MD5").let { md5 ->
                directories.forEach { file ->
                    val files = KFiles.findRecursively(file) // , { f -> f.endsWith("java")})
                    log(2, "  Calculating checksum of ${files.size} files")
                    files.map {
                        File(file, it)
                    }.filter {
                        it.isFile
                    }.forEach {
                        val bytes = it.readBytes()
                        md5.update(bytes, 0, bytes.size)
                    }
                }
                val result = DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()
                return result
            }
        }

        fun toMd5(file: File) = MessageDigest.getInstance("MD5").let { md5 ->
                file.forEachBlock { bytes, size ->
                    md5.update(bytes, 0, size)
                }
                DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()
            }
    }
}

