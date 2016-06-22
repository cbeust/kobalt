package com.beust.kobalt.maven.aether

import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.log
import org.eclipse.aether.transfer.AbstractTransferListener
import org.eclipse.aether.transfer.MetadataNotFoundException
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.transfer.TransferResource
import java.io.PrintStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ConsoleTransferListener @JvmOverloads constructor(out: PrintStream? = null) : AbstractTransferListener() {

    private val out: PrintStream

    private val downloads = ConcurrentHashMap<TransferResource, Long>()

    private var lastLength: Int = 0

    init {
        this.out = out ?: System.out
    }

    override fun transferInitiated(event: TransferEvent?) {
        val message = if (event!!.requestType == TransferEvent.RequestType.PUT) "Uploading" else "Downloading"

        log(2, message + ": " + event.resource.repositoryUrl + event.resource.resourceName)
    }

    override fun transferProgressed(event: TransferEvent?) {
        val resource = event!!.resource
        downloads.put(resource, java.lang.Long.valueOf(event.transferredBytes))

        val buffer = StringBuilder(64)

        for (entry in downloads.entries) {
            val total = entry.key.contentLength
            val complete = entry.value.toLong()

            buffer.append(getStatus(complete, total)).append("  ")
        }

        val pad = lastLength - buffer.length
        lastLength = buffer.length
        pad(buffer, pad)
        buffer.append('\r')

        out.print(buffer)
    }

    private fun getStatus(complete: Long, total: Long): String {
        if (total >= 1024) {
            return toKB(complete).toString() + "/" + toKB(total) + " KB "
        } else if (total >= 0) {
            return complete.toString() + "/" + total + " B "
        } else if (complete >= 1024) {
            return toKB(complete).toString() + " KB "
        } else {
            return complete.toString() + " B "
        }
    }

    private fun pad(buffer: StringBuilder, spaces: Int) {
        var spaces = spaces
        val block = "                                        "
        while (spaces > 0) {
            val n = Math.min(spaces, block.length)
            buffer.append(block, 0, n)
            spaces -= n
        }
    }

    override fun transferSucceeded(event: TransferEvent) {
        transferCompleted(event)

        val resource = event.resource
        val contentLength = event.transferredBytes
        if (contentLength >= 0) {
            val type = if (event.requestType == TransferEvent.RequestType.PUT) "Uploaded" else "Downloaded"
            val len = if (contentLength >= 1024) toKB(contentLength).toString() + " KB"
                else contentLength.toString() + " B"

            var throughput = ""
            val duration = System.currentTimeMillis() - resource.transferStartTime
            if (duration > 0) {
                val bytes = contentLength - resource.resumeOffset
                val format = DecimalFormat("0.0", DecimalFormatSymbols(Locale.ENGLISH))
                val kbPerSec = bytes / 1024.0 / (duration / 1000.0)
                throughput = " at " + format.format(kbPerSec) + " KB/sec"
            }

            log(2, type + ": " + resource.repositoryUrl + resource.resourceName + " (" + len
                    + throughput + ")")
        }
    }

    override fun transferFailed(event: TransferEvent) {
        transferCompleted(event)

        if (event.exception !is MetadataNotFoundException) {
            if (KobaltLogger.LOG_LEVEL > 2) {
                event.exception.printStackTrace(out)
            }
        }
    }

    private fun transferCompleted(event: TransferEvent) {
        downloads.remove(event.resource)

        val buffer = StringBuilder(64)
        pad(buffer, lastLength)
        buffer.append('\r')
        out.print(buffer)
    }

    override fun transferCorrupted(event: TransferEvent?) {
        event!!.exception.printStackTrace(out)
    }

    protected fun toKB(bytes: Long): Long {
        return (bytes + 1023) / 1024
    }

}

