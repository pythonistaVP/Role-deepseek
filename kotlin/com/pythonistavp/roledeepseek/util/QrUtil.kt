package com.pythonistavp.roledeepseek.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Экспорт карточки в QR: JSON сжимаем gzip, кодируем base64url и рисуем QR.
 * Такой QR можно отсканировать и вставить текстом в диалоге импорта.
 */
object QrUtil {

    private const val PREFIX = "RDZS1:"
    private const val MAX_PAYLOAD = 2000

    fun compress(json: String): String {
        val raw = ByteArrayOutputStream()
        GZIPOutputStream(raw).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.encodeToString(
            raw.toByteArray(),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
    }

    fun decompress(payload: String): String? {
        if (!payload.startsWith(PREFIX)) return null
        return runCatching {
            val bytes = Base64.decode(
                payload.removePrefix(PREFIX),
                Base64.NO_WRAP or Base64.URL_SAFE,
            )
            GZIPInputStream(ByteArrayInputStream(bytes))
                .use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    fun isQrPayload(text: String): Boolean = text.trim().startsWith(PREFIX)

    fun encodeAsset(text: String, size: Int = 768): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        var y = 0
        while (y < size) {
            var x = 0
            while (x < size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                x++
            }
            y++
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** QR для карточки; null, если карточка не влезает в один код. */
    fun encodeCard(json: String, size: Int = 768): Bitmap? {
        val payload = compress(json)
        if (payload.length > MAX_PAYLOAD) return null
        return encodeAsset(payload, size)
    }
}
