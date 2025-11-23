package org.ntqqrev.apng

import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class ApngReaderTest {
    @Test
    fun `extract apng frames to disk`() {
        val apngPath = Path.of("../composeApp/src/jvmMain/resources/assets/qq_emoji/307/apng/307.png")
        assertTrue(Files.exists(apngPath), "APNG resource not found: $apngPath")

        val bytes = Files.readAllBytes(apngPath)
        val reader = ApngReader(bytes)

        val outputDir = Path.of("apng/test-output/frames_307")
        Files.createDirectories(outputDir)

        var count = 0
        reader.frames.forEach { frame ->
            val file = outputDir.resolve("frame_${count.toString().padStart(3, '0')}.png")
            ImageIO.write(frame.image, "png", file.toFile())
            count++
        }

        assertTrue(count > 0, "Expected at least one frame to be extracted")
    }
}
