package com.beust.kobalt.maven

import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.log
import java.io.File
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

public class Md5 {
    companion object {
//        private fun md5(file: File) : String {
//            val md5 = MessageDigest.getInstance("MD5")
//            val bytes = file.readBytes()
//            md5.update(bytes, 0, bytes.size)
//            return DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()
//        }

        fun toMd5Directories(directories: List<File>) : String {
            MessageDigest.getInstance("MD5").let { md5 ->
                directories.forEach { file ->
                    if (file.isFile) {
                        val bytes = file.readBytes()
                        md5.update(bytes, 0, bytes.size)
                    } else {
                        val files = KFiles.findRecursively(file) // , { f -> f.endsWith("java")})
                        log(2, "  Calculating checksum of ${files.size} files in $file")
                        files.map {
                            File(file, it)
                        }.filter {
                            it.isFile
                        }.forEach {
                            val bytes = it.readBytes()
                            md5.update(bytes, 0, bytes.size)
                        }
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

