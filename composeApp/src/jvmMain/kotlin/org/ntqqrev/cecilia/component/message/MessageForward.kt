package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text
import org.ntqqrev.cecilia.model.Element

@Composable
fun MessageForward(forward: Element.Forward) {
    Box(Modifier.widthIn(max = 300.dp)) {
        Layer {
            Column {
                Box(Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp, bottom = 6.dp)) {
                    Text(forward.title)
                }
                Box(
                    Modifier.background(color = FluentTheme.colors.stroke.divider.default)
                        .fillMaxWidth()
                        .height(1.dp)
                )
                Box(Modifier.padding(vertical = 4.dp, horizontal = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        forward.preview.forEach { line ->
                            Text(
                                text = line,
                                style = FluentTheme.typography.caption,
                                color = FluentTheme.colors.text.text.secondary,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    Text(
                        text = forward.summary,
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.tertiary,
                    )
                }
            }
        }
    }
}