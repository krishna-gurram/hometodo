package dev.krishnagurram.hometodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.hometodo.ui.theme.HomeColors

// Small building blocks shared by every screen, like a components/ folder in React.
// `internal` = visible anywhere in this app module, hidden from other modules.

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 8.dp, top = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = HomeColors.InkMuted,
    )
}

/** The white rounded card that groups rows. */
@Composable
internal fun CardSurface(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HomeColors.Card, RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
internal fun ColorDot(hex: String, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .background(parseHex(hex), CircleShape)
    )
}

/** The empty circle you tap to complete a chore or check off an item. */
@Composable
internal fun CheckCircleButton(label: String, onClick: () -> Unit) {
    // IconButton gives a 48dp touch target around the 26dp circle. The semantics label
    // is what TalkBack reads aloud, like aria-label on the web.
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            Modifier
                .size(26.dp)
                .border(2.dp, HomeColors.Ring, CircleShape)
        )
    }
}

/** A filled green circle with a tick, for things already done. */
@Composable
internal fun CheckedCircle() {
    Box(
        Modifier
            .size(26.dp)
            .background(HomeColors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Back arrow plus optional trailing actions, the top of every inner screen. */
@Composable
internal fun BackBar(onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HomeColors.Ink)
        }
        Box(Modifier.weight(1f))
        actions()
    }
}

internal fun parseHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
