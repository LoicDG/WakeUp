package com.loic.wakeup.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loic.wakeup.data.SettingsStore
import androidx.compose.runtime.collectAsState
import java.util.Locale

/** Split a 24-hour hour (0..23) into a 12-hour reading (1..12) and an AM/PM flag. */
fun to12Hour(hour24: Int): Pair<Int, Boolean> {
    val isPm = hour24 >= 12
    val hour12 = ((hour24 + 11) % 12) + 1
    return hour12 to isPm
}

/**
 * Plain-string clock for non-Compose contexts (e.g. notifications): `"23:00"` in
 * 24-hour mode, `"11:00 PM"` in 12-hour mode.
 */
fun formatClock(hour: Int, minute: Int, use24Hour: Boolean): String =
    if (use24Hour) {
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    } else {
        val (hour12, isPm) = to12Hour(hour)
        String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, if (isPm) "PM" else "AM")
    }

/**
 * A clock reading in 12-hour form: the digits in [style], with a quiet AM/PM
 * suffix set smaller and baseline-aligned so it rides the bottom of the numerals
 * as one typographic unit rather than a separate label. Storage stays 24-hour;
 * this is purely how the time is shown.
 */
@Composable
fun TimeText(
    hour: Int,
    minute: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    amPmColor: Color = color.copy(alpha = 0.7f),
) {
    val use24Hour by SettingsStore.use24Hour.collectAsState()
    if (use24Hour) {
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = style,
            color = color,
            modifier = modifier,
        )
        return
    }
    val (hour12, isPm) = to12Hour(hour)
    val amPmStyle = style.copy(
        fontSize = style.fontSize * 0.42f,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = "%d:%02d".format(hour12, minute),
            style = style,
            color = color,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isPm) "PM" else "AM",
            style = amPmStyle,
            color = amPmColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}
