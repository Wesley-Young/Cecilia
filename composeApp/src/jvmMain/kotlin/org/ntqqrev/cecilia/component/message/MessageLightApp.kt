package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.serialization.json.decodeFromJsonElement
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.lightapp.LightAppPayload
import org.ntqqrev.cecilia.model.lightapp.MiniApp01Detail
import java.awt.Cursor
import java.awt.Desktop
import java.net.URI

@Composable
fun MessageLightApp(lightApp: Element.LightApp) {
    when (lightApp.payload.app) {
        "com.tencent.miniapp_01" if ("detail_1" in lightApp.payload.meta) -> {
            val detail = LightAppPayload.jsonModule
                .decodeFromJsonElement<MiniApp01Detail>(lightApp.payload.meta["detail_1"]!!)
            MiniApp01DetailView(lightApp, detail)
        }

        else -> {
            Text(lightApp.toString())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniApp01DetailView(lightApp: Element.LightApp, detail: MiniApp01Detail) {
    Box(
        Modifier.width(300.dp)
            .background(color = Color.White, shape = RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .onClick {
                runCatching {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(detail.qqdocurl))
                    }
                }
            }
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KamelImage(
                    resource = { asyncPainterResource(detail.icon) },
                    contentDescription = detail.title,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = detail.title,
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(detail.desc)
            KamelImage(
                resource = { asyncPainterResource(detail.preview) },
                contentDescription = detail.title,
            )
        }
    }
}