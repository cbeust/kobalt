package com.beust.kobalt.maven

import com.beust.kobalt.misc.KFiles
import com.beust.kobalt.misc.kobaltLog
import java.io.File
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

class Md5 {
    companion object {
//        private fun md5(file: File) : String {
//            if (file.isDirectory) {
//                println("PROBLEM")
//            }
//            val md5 = MessageDigest.getInstance("MD5")
//            val bytes = file.readBytes()
//            md5.update(bytes, 0, bytes.size)
//            return DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()
//        }

        /**
         * Calculate a checksum for all the files/directories. The conversion from File to
         * bytes can be customized by the @param{toBytes} parameter. The default implementation calculates
         * a checksum of the last modified timestamp.
         */
        fun toMd5Directories(filesOrDirectories: List<File>,
                toBytes: (File) -> ByteArray = { "${it.path} ${it.lastModified()} ${it.length()}".toByteArray() } )
                        : String? {
            if (filesOrDirectories.any(File::exists)) {
                MessageDigest.getInstance("MD5").let { md5 ->
                    var fileCount = 0
                    filesOrDirectories.filter(File::exists).forEach { file ->
                        if (file.isFile) {
                            kobaltLog(2, "      Calculating checksum of $file")
                            val bytes = toBytes(file)
                            md5.update(bytes, 0, bytes.size)
                            fileCount++
                        } else {
                            val files = KFiles.findRecursively(file) // , { f -> f.endsWith("java")})
                            kobaltLog(2, "      Calculating checksum of ${files.size} files in $file")
                            files.map {
                                File(file, it)
                            }.filter {
                                it.isFile
                            }.forEach {
                                fileCount++
                                val bytes = toBytes(it)
                                md5.update(bytes, 0, bytes.size)
                            }
                        }
                    }

                    // The output directory might exist but with no files in it, in which case
                    // we must run the task
                    if (fileCount > 0) {
                        val result = DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()
                        return result
                    } else {
                        return null
                    }
                }
            } else {
                return null
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

