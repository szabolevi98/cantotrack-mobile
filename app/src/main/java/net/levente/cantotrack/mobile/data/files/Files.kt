package net.levente.cantotrack.mobile.data.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.Attachment
import net.levente.cantotrack.mobile.data.api.Connection
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max

/**
 * A ticket's files on the phone: downloaded once into the cache, opened in
 * whatever app the phone has for them, and drawn small for the list.
 */
class AttachmentFiles(private val context: Context, private val api: ApiClient) {
    private val thumbnails = LruCache<Int, Bitmap>(40)

    private fun fileOf(attachment: Attachment): File {
        val safe = attachment.name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
        return File(File(context.cacheDir, "attachments").apply { mkdirs() }, "${attachment.id}-$safe")
    }

    /** The file itself, from the cache or downloaded now. */
    suspend fun file(connection: Connection, attachment: Attachment): File {
        val file = fileOf(attachment)
        if (!file.exists() || file.length() == 0L) api.download(connection, attachment.id, file)
        return file
    }

    fun cachedThumbnail(attachment: Attachment): Bitmap? = thumbnails.get(attachment.id)

    /** A picture's small version, for a row of thumbnails. */
    suspend fun thumbnail(connection: Connection, attachment: Attachment, size: Int): Bitmap? {
        thumbnails.get(attachment.id)?.let { return it }
        val file = file(connection, attachment)
        val bitmap = withContext(Dispatchers.IO) { Images.decode(file.path, size) } ?: return null
        thumbnails.put(attachment.id, bitmap)
        return bitmap
    }

    /** Opens it in another app; false when the phone has none for it. */
    fun open(file: File, type: String): Boolean {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

/** A file picked or photographed, ready to be sent. */
class PreparedFile(val name: String, val type: String, val bytes: ByteArray)

object Images {
    /** The longest side a photo is sent at: plenty to read a screen, far less than a phone takes. */
    private const val LONGEST = 2560

    /** A place for the camera app to write a photo into. */
    fun newPhoto(context: Context): Uri {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        // Photos that were sent, or given up on, are not kept.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000 }?.forEach { it.delete() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    /**
     * Reads what [uri] points at. A picture is turned the right way up and
     * made smaller — a phone's twelve megapixels are more than the server
     * takes — and sent as a JPEG; anything else goes as it is.
     */
    suspend fun prepare(context: Context, uri: Uri): PreparedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val type = resolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

        if (type.startsWith("image/") && type != "image/gif") {
            shrink(context, uri)?.let { bytes ->
                return@withContext PreparedFile(name.substringBeforeLast('.') + ".jpg", "image/jpeg", bytes)
            }
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("cannot read $uri")
        PreparedFile(name, type, bytes)
    }

    private fun shrink(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONGEST) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, LONGEST.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val result = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded

        return ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    /** A picture file at about [size] pixels on its shorter side. */
    fun decode(path: String, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
