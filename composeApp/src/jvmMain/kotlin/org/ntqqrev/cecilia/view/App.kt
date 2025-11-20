package org.ntqqrev.cecilia.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    Text("Hello, Cecilia!", fontSize = 24.sp, textAlign = TextAlign.Center)
}