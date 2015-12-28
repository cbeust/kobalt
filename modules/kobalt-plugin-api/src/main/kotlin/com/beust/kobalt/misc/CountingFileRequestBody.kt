package com.beust.kobalt.misc

import com.squareup.okhttp.MediaType
import com.squareup.okhttp.RequestBody
import okio.BufferedSink
import okio.Okio
import java.io.File

/**
 * An OkHttp RequestBody subclass that counts the outgoing bytes and offers progress callbacks.
 */
class CountingFileRequestBody(val file: File, val contentType: String,
                              val listenerCallback: (Long) -> Unit) : RequestBody() {

    val SEGMENT_SIZE = 4096L

    override fun contentLength() = file.length()

    override fun contentType() = MediaType.parse(contentType)

    override fun writeTo(sink: BufferedSink) {
        Okio.source(file).use { source ->
            var total = 0L
            var read: Long = source.read(sink.buffer(), SEGMENT_SIZE)

            while (read != -1L) {
                total += read
                sink.flush();
                listenerCallback(total)
                read = source.read(sink.buffer(), SEGMENT_SIZE)
            }
        }
    }

    //    companion object {
    //        private val MEDIA_TYPE_BINARY = MediaType.parse("application/octet-stream")
    //
    //        fun progressUpload(file: File, url: String) {
    //            val totalSize = file.length()
    //
    //            val progressListener = object : ProgressListener {
    //                override fun transferred(num: Long) {
    //                    val progress: Float = (num.toFloat() * 100) / totalSize
    //                    print("\rProgress: $progress")
    //                }
    //            }
    //
    //            val request = Request.Builder()
    //                    .url(url)
    //                    //                    .post(RequestBody.create(MEDIA_TYPE_BINARY, file))
    //                    .put(CountingFileRequestBody(file, "application/octet-stream", progressListener))
    //                    //                    .post(requestBody)
    //                    .build();
    //
    //            val response = OkHttpClient().newCall(request).execute()
    //            if (! response.isSuccessful) {
    //                println("ERROR")
    //            } else {
    //                println("SUCCESS")
    //            }
    //        }
    //    }
}


