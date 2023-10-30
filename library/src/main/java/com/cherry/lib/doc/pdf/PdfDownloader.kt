package com.cherry.lib.doc.pdf

import android.content.Context
import com.cherry.lib.doc.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

/*
 * -----------------------------------------------------------------
 * Copyright (C) 2018-2028, by Victor, All rights reserved.
 * -----------------------------------------------------------------
 * File: PdfDownloader
 * Author: Victor
 * Date: 2023/09/28 11:13
 * Description: 
 * -----------------------------------------------------------------
 */

internal class PdfDownloader(url: String, private val listener: StatusListener) {
    interface StatusListener {
        fun getContext(): Context
        fun onDownloadStart() {}
        fun onDownloadProgress(currentBytes: Long, totalBytes: Long) {}
        fun onDownloadSuccess(absolutePath: String) {}
        fun onError(error: Throwable) {}
        fun getCoroutineScope(): CoroutineScope
    }

    init {
        listener.getCoroutineScope().launch(Dispatchers.IO) { download(url) }
    }

    private fun download(downloadUrl: String) {
        var format = FileUtils.getFileFormatForUrl(downloadUrl)
        listener.getCoroutineScope().launch(Dispatchers.Main) { listener.onDownloadStart() }
        val outputFile = File(listener.getContext().cacheDir, "doc.$format")
        if (outputFile.exists())
            outputFile.delete()
        try {
            val bufferSize = 8192
            val url = URL(downloadUrl)
            val connection = url.openConnection()
            connection.connect()

            val totalLength = connection.contentLength
            val inputStream = BufferedInputStream(url.openStream(), bufferSize)
            val outputStream = outputFile.outputStream()
            var downloaded = 0

            do {
                val data = ByteArray(bufferSize)
                val count = inputStream.read(data)
                if (count == -1)
                    break
                if (totalLength > 0) {
                    downloaded += bufferSize
                    listener.getCoroutineScope().launch(Dispatchers.Main) {
                        listener.onDownloadProgress(
                            downloaded.toLong(),
                            totalLength.toLong()
                        )
                    }
                }
                outputStream.write(data, 0, count)
            } while (true)
        } catch (e: Exception) {
            e.printStackTrace()
            listener.getCoroutineScope().launch(Dispatchers.Main) { listener.onError(e) }
            return
        }
        listener.getCoroutineScope().launch(Dispatchers.Main) { listener.onDownloadSuccess(outputFile.absolutePath) }
    }
}
