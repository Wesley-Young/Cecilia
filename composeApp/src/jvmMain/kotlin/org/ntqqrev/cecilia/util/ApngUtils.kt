package org.ntqqrev.cecilia.util

import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import javax.imageio.ImageIO

object APNG {
    data class Frame(
        val image: BufferedImage,
        val delayMillis: Long,
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val disposeOp: Int,
        val blendOp: Int
    )

    fun parse(bytes: ByteArray): List<Frame> {
        val stream = ByteArrayInputStream(bytes)
        val signature = stream.readNBytes(PNG_SIGNATURE.size)
        require(signature contentEquals PNG_SIGNATURE) { "Not a PNG/APNG file" }

        var ihdrData: ByteArray? = null
        var iendChunk: ByteArray? = null
        val sharedChunks = mutableListOf<ByteArray>()
        val frameBuilders = mutableListOf<FrameBuilder>()
        var builder: FrameBuilder? = null
        var seenIdat = false

        while (stream.available() > 0) {
            val lengthBytes = stream.readNBytes(4)
            if (lengthBytes.size < 4) break
            val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int
            val typeBytes = stream.readNBytes(4)
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = stream.readNBytes(length)
            val crc = stream.readNBytes(4)
            if (data.size != length || crc.size != 4) break

            when (type) {
                "IHDR" -> ihdrData = data
                "acTL" -> {} // Animation control chunk is not required here.
                "fcTL" -> {
                    if (builder != null) {
                        frameBuilders.add(builder)
                    }
                    builder = FrameBuilder(parseFrameControl(data), mutableListOf())
                }

                "IDAT" -> {
                    seenIdat = true
                    requireNotNull(builder) { "Encountered IDAT before frame control (fcTL)" }
                    builder.dataChunks.add(data)
                }

                "fdAT" -> {
                    seenIdat = true
                    requireNotNull(builder) { "Encountered fdAT before frame control (fcTL)" }
                    // Strip the first 4 bytes (sequence number) and treat as IDAT data.
                    builder.dataChunks.add(data.copyOfRange(4, data.size))
                }

                "IEND" -> {
                    if (builder != null) {
                        frameBuilders.add(builder)
                        builder = null
                    }
                    iendChunk = makeChunk(typeBytes, ByteArray(0))
                }

                else -> {
                    // Store non-animation ancillary chunks that appear before the first IDAT.
                    if (!seenIdat && type !in ANIMATION_CHUNKS) {
                        sharedChunks.add(makeChunk(typeBytes, data))
                    }
                }
            }
        }

        if (builder != null) {
            frameBuilders.add(builder)
        }

        val ihdr = ihdrData ?: error("Missing IHDR chunk")
        val iend = iendChunk ?: makeChunk("IEND".toByteArray(Charsets.US_ASCII), ByteArray(0))

        return compositeFrames(frameBuilders, ihdr, sharedChunks, iend)
    }

    private fun compositeFrames(
        frameBuilders: List<FrameBuilder>,
        baseIhdr: ByteArray,
        sharedChunks: List<ByteArray>,
        iendChunk: ByteArray
    ): List<Frame> {
        val canvasWidth = ByteBuffer.wrap(baseIhdr, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        val canvasHeight = ByteBuffer.wrap(baseIhdr, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val canvas = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)

        val result = mutableListOf<Frame>()
        for (frame in frameBuilders) {
            val ihdrChunk = makeChunk(
                IHDR_BYTES,
                patchIhdr(baseIhdr, frame.control.width, frame.control.height)
            )

            val output = ByteArrayOutputStream()
            output.write(PNG_SIGNATURE)
            output.write(ihdrChunk)
            sharedChunks.forEach { output.write(it) }
            frame.dataChunks.forEach { data ->
                output.write(makeChunk(IDAT_BYTES, data))
            }
            output.write(iendChunk)

            val frameImage: BufferedImage = ImageIO.read(ByteArrayInputStream(output.toByteArray()))
                ?: error("Failed to decode APNG frame")

            val beforeForPrevious =
                if (frame.control.disposeOp == DISPOSE_PREVIOUS) deepCopy(canvas) else null

            val g = canvas.createGraphics()
            when (frame.control.blendOp) {
                BLEND_SOURCE -> {
                    g.composite = AlphaComposite.Src
                    g.drawImage(frameImage, frame.control.xOffset, frame.control.yOffset, null)
                }

                else -> {
                    g.composite = AlphaComposite.SrcOver
                    g.drawImage(frameImage, frame.control.xOffset, frame.control.yOffset, null)
                }
            }
            g.dispose()

            result.add(
                Frame(
                    image = deepCopy(canvas),
                    delayMillis = calculateDelay(frame.control.delayNum, frame.control.delayDen),
                    width = canvasWidth,
                    height = canvasHeight,
                    xOffset = frame.control.xOffset,
                    yOffset = frame.control.yOffset,
                    disposeOp = frame.control.disposeOp,
                    blendOp = frame.control.blendOp
                )
            )

            when (frame.control.disposeOp) {
                DISPOSE_BACKGROUND -> {
                    val clear = canvas.createGraphics()
                    clear.composite = AlphaComposite.Clear
                    clear.fillRect(
                        frame.control.xOffset,
                        frame.control.yOffset,
                        frame.control.width,
                        frame.control.height
                    )
                    clear.dispose()
                }

                DISPOSE_PREVIOUS -> {
                    if (beforeForPrevious != null) {
                        val restore = canvas.createGraphics()
                        restore.composite = AlphaComposite.Src
                        restore.drawImage(beforeForPrevious, 0, 0, null)
                        restore.dispose()
                    }
                }
            }
        }

        return result
    }

    private fun patchIhdr(ihdr: ByteArray, width: Int, height: Int): ByteArray {
        val patched = ihdr.copyOf()
        val buffer = ByteBuffer.wrap(patched).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0, width)
        buffer.putInt(4, height)
        return patched
    }

    private fun calculateDelay(delayNum: Int, delayDen: Int): Long {
        val denominator = if (delayDen == 0) 100 else delayDen
        val millis = (delayNum * 1000.0 / denominator).toLong()
        return if (millis <= 0) 10 else millis
    }

    private fun parseFrameControl(data: ByteArray): FrameControl {
        require(data.size >= 26) { "Invalid fcTL chunk" }
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val sequenceNumber = buffer.int
        val width = buffer.int
        val height = buffer.int
        val xOffset = buffer.int
        val yOffset = buffer.int
        val delayNum = buffer.short.toInt() and 0xFFFF
        val delayDen = buffer.short.toInt() and 0xFFFF
        val disposeOp = buffer.get().toInt() and 0xFF
        val blendOp = buffer.get().toInt() and 0xFF
        return FrameControl(
            sequenceNumber,
            width,
            height,
            xOffset,
            yOffset,
            delayNum,
            delayDen,
            disposeOp,
            blendOp
        )
    }

    private fun makeChunk(type: ByteArray, data: ByteArray): ByteArray {
        require(type.size == 4) { "Chunk type must be 4 bytes" }
        val crc32 = CRC32()
        crc32.update(type)
        crc32.update(data)

        val output = ByteArrayOutputStream()
        output.write(intToBytes(data.size))
        output.write(type)
        output.write(data)
        output.write(intToBytes(crc32.value.toInt()))
        return output.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

    private fun deepCopy(src: BufferedImage): BufferedImage {
        val copy = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        g.composite = AlphaComposite.Src
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return copy
    }

    private data class FrameBuilder(
        val control: FrameControl,
        val dataChunks: MutableList<ByteArray>
    )

    private data class FrameControl(
        val sequenceNumber: Int,
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val delayNum: Int,
        val delayDen: Int,
        val disposeOp: Int,
        val blendOp: Int
    )

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
    private val IHDR_BYTES = "IHDR".toByteArray(Charsets.US_ASCII)
    private val IDAT_BYTES = "IDAT".toByteArray(Charsets.US_ASCII)
    private val ANIMATION_CHUNKS = setOf("acTL", "fcTL", "fdAT")
    private const val BLEND_SOURCE = 0
    private const val DISPOSE_BACKGROUND = 1
    private const val DISPOSE_PREVIOUS = 2
}
