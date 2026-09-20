package com.pythonistavp.roledeepseek.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * BlurHash: компактная (20-40 символов) ASCII-копия аватара.
 * Хранится в БД и рисуется как мягкая подложка, пока Coil грузит картинку.
 */
object BlurHash {

    private const val CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    fun encode(bitmap: Bitmap, componentsX: Int = 4, componentsY: Int = 3): String {
        val width = 32
        val height = 32
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        val factors = Array(componentsX * componentsY) { FloatArray(3) }
        for (j in 0 until componentsY) {
            for (i in 0 until componentsX) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        val pixel = pixels[x + y * width]
                        val basis = cos(PI * i * x / width).toFloat() *
                            cos(PI * j * y / height).toFloat()
                        r += basis * srgbToLinear((pixel shr 16) and 0xff)
                        g += basis * srgbToLinear((pixel shr 8) and 0xff)
                        b += basis * srgbToLinear(pixel and 0xff)
                    }
                }
                val scale = 1f / (width * height)
                val index = i + j * componentsX
                factors[index][0] = r * scale
                factors[index][1] = g * scale
                factors[index][2] = b * scale
            }
        }

        val dc = factors[0]
        val ac = factors.copyOfRange(1, factors.size)

        var maximumValue = 0f
        for (factor in ac) {
            for (channel in 0..2) {
                val value = abs(factor[channel])
                maximumValue = maxOf(maximumValue, linearToSrgb(value))
            }
        }
        val quantisedMaximumValue = (maximumValue * 166f - 0.5f).toInt().coerceIn(0, 82)
        val maxValue = (quantisedMaximumValue + 1) / 166f

        val hash = StringBuilder()
        hash.append(encode83((componentsX - 1) + (componentsY - 1) * 9, 1))
        hash.append(encode83(encodeDc(dc), 4))
        for (factor in ac) hash.append(encode83(encodeAc(factor, maxValue), 2))
        if (hash.length < 6) hash.append(CHARS[0])
        return hash.toString()
    }

    /**
     * Кэш готовых подложек: при скролле списка один и тот же хэш встречается
     * многократно, и считать его каждый раз заново незачем.
     * Битые/пустые хэши не кэшируем — они бесплатны.
     */
    private val cache = android.util.LruCache<String, Bitmap>(CACHE_ENTRIES)

    /** То же, что [decode], но с кэшем по строке хэша. */
    fun decodeCached(hash: String?, width: Int = 32, height: Int = 32): Bitmap? {
        val key = hash?.trim().orEmpty()
        if (key.length < 6) return null
        cache.get(key)?.let { return it }
        val bitmap = decode(key, width, height) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private const val CACHE_ENTRIES = 64

    fun decode(hash: String?, width: Int = 32, height: Int = 32): Bitmap? {
        val value = hash?.trim().orEmpty()
        if (value.length < 6) return null
        return runCatching {
            var pointer = 0
            val sizeFlag = decode83(value.substring(pointer, pointer + 1)) - 1
            pointer += 1
            val componentsX = sizeFlag % 9 + 1
            val componentsY = sizeFlag / 9 + 1
            val quantisedMaximumValue = decode83(value.substring(pointer, pointer + 2))
            pointer += 2
            val maximumValue = (quantisedMaximumValue + 1) / 166f

            val colors = Array(componentsX * componentsY) { FloatArray(3) }
            colors[0] = decodeDc(decode83(value.substring(pointer, pointer + 4)))
            pointer += 4
            for (i in 1 until componentsX * componentsY) {
                if (pointer + 2 > value.length) break
                val q = decode83(value.substring(pointer, pointer + 2))
                pointer += 2
                colors[i] = decodeAc(q, maximumValue)
            }

            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    for (j in 0 until componentsY) {
                        for (i in 0 until componentsX) {
                            val basis = cos(PI * i * x / width).toFloat() *
                                cos(PI * j * y / height).toFloat()
                            val color = colors[i + j * componentsX]
                            r += color[0] * basis
                            g += color[1] * basis
                            b += color[2] * basis
                        }
                    }
                    pixels[x + y * width] = Color.rgb(
                        toInt(linearToSrgb(r)),
                        toInt(linearToSrgb(g)),
                        toInt(linearToSrgb(b)),
                    )
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        }.getOrNull()
    }

    private fun toInt(value: Float): Int =
        (value * 255f + 0.5f).toInt().coerceIn(0, 255)

    private fun encode83(value: Int, length: Int): String {
        var result = ""
        for (i in 1..length) {
            val digit = (value / 83f.pow(length - i).roundToInt()) % 83
            result += CHARS[digit]
        }
        return result
    }

    private fun decode83(value: String): Int {
        var result = 0
        for (char in value) {
            val digit = CHARS.indexOf(char)
            if (digit == -1) return -1
            result = result * 83 + digit
        }
        return result
    }

    private fun encodeDc(color: FloatArray): Int =
        (toInt(linearToSrgb(color[0])) shl 16) +
            (toInt(linearToSrgb(color[1])) shl 8) +
            toInt(linearToSrgb(color[2]))

    private fun decodeDc(value: Int): FloatArray = floatArrayOf(
        srgbToLinear((value shr 16) and 0xff),
        srgbToLinear((value shr 8) and 0xff),
        srgbToLinear(value and 0xff),
    )

    private fun encodeAc(color: FloatArray, maximumValue: Float): Int {
        fun quantise(value: Float): Int =
            (linearToSrgb(value / maximumValue).coerceIn(0f, 1f) * 18f + 0.5f)
                .toInt()
                .coerceIn(0, 18)
        return quantise(color[0]) * 19 * 19 + quantise(color[1]) * 19 + quantise(color[2])
    }

    private fun decodeAc(value: Int, maximumValue: Float): FloatArray {
        val r = value / (19 * 19)
        val g = (value / 19) % 19
        val b = value % 19
        fun sign(v: Int) = ((v - 9).toFloat() / 9f) * maximumValue
        return floatArrayOf(
            srgbToLinearV(sign(r)),
            srgbToLinearV(sign(g)),
            srgbToLinearV(sign(b)),
        )
    }

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun srgbToLinearV(v: Float): Float = v

    private fun linearToSrgb(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }
}
