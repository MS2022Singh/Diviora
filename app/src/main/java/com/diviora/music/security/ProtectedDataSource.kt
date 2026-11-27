package com.diviora.music.security

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AesCipherDataSink
import androidx.media3.datasource.AesCipherDataSource
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.diviora.music.BuildConfig
import java.io.File

/** Where encrypted offline copies live: app-private internal storage (not reachable by other apps or a USB cable). */
fun offlineDir(context: Context): File = File(context.filesDir, "offline").apply { mkdirs() }

/** The AES-CTR nonce of an offline file comes from its own name, so writer and reader always agree. */
internal fun nonceKeyFor(file: File) = "diviora-offline:" + file.name

/**
 * Plays encrypted offline copies (decrypting while streaming, never writing plaintext to disk) and everything else
 * through the normal sources. Network requests identify the app to protected content servers.
 */
@OptIn(UnstableApi::class)
class ProtectedDataSourceFactory(private val context: Context, private val mediaKey: () -> ByteArray) : DataSource.Factory {
    constructor(context: Context, vault: KeyVault) : this(context, vault::mediaKey)

    private val normal = DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory()
            .setUserAgent("DIVIORA-Music/${BuildConfig.VERSION_NAME}")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(contentHeaders()),
    )

    override fun createDataSource(): DataSource = RoutingDataSource()

    private inner class RoutingDataSource : DataSource {
        private var active: DataSource? = null
        private val listeners = ArrayList<TransferListener>()

        override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }

        override fun open(dataSpec: DataSpec): Long {
            val path = dataSpec.uri.takeIf { it.scheme == "file" }?.path
            val offline = path != null && File(path).canonicalPath.startsWith(offlineDir(context).canonicalPath)
            val ds: DataSource = if (offline) AesCipherDataSource(mediaKey(), FileDataSource()) else normal.createDataSource()
            listeners.forEach(ds::addTransferListener)
            active = ds
            val spec = if (offline) dataSpec.buildUpon().setKey(nonceKeyFor(File(path!!))).build() else dataSpec
            return ds.open(spec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() { try { active?.close() } finally { active = null } }
    }

    companion object {
        /** Headers that let a protected content server recognise the DIVIORA app (see the developer guide, secure server). */
        fun contentHeaders(): Map<String, String> = buildMap {
            if (BuildConfig.CONTENT_KEY.isNotBlank()) put("X-Diviora-Key", BuildConfig.CONTENT_KEY)
        }
    }
}

/** Writes an encrypted offline copy. */
@OptIn(UnstableApi::class)
fun encryptingSink(vault: KeyVault, target: File): Pair<DataSink, DataSpec> = encryptingSink(vault.mediaKey(), target)

@OptIn(UnstableApi::class)
fun encryptingSink(key: ByteArray, target: File): Pair<DataSink, DataSpec> =
    AesCipherDataSink(key, PlainFileSink()) to DataSpec.Builder().setUri(Uri.fromFile(target)).setKey(nonceKeyFor(target)).build()

/** Minimal DataSink that writes to the file named by the DataSpec (receives only ciphertext from AesCipherDataSink). */
@OptIn(UnstableApi::class)
private class PlainFileSink : DataSink {
    private var out: java.io.BufferedOutputStream? = null
    override fun open(dataSpec: DataSpec) { out = java.io.BufferedOutputStream(java.io.FileOutputStream(File(dataSpec.uri.path!!)), 64 * 1024) }
    override fun write(buffer: ByteArray, offset: Int, length: Int) { out!!.write(buffer, offset, length) }
    override fun close() { try { out?.close() } finally { out = null } }
}
