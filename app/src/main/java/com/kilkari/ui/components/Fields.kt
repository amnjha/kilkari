package com.kilkari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/** Label-left / value-right editable row, the pattern every Kilkari sheet uses. */
/**
 * Minimum height of a form row, so a tapped field is a comfortable target rather than the
 * height its text happens to need. Material's minimum touch target is 48dp; the extra gives
 * the label and value room to breathe.
 */
val FORM_ROW_HEIGHT = 56.dp

@Composable
fun ValueField(
    label: String,
    value: String,
    placeholder: String = "",
    divider: Boolean = true,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onChange: (String) -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                // A form row is a touch target: 40dp of text and padding sat under the 48dp
                // Android minimum, and read as cramped against the space around it.
                .heightIn(min = FORM_ROW_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, fontFamily = Sans, fontSize = 14.sp, color = KC.Muted)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) {
                    Text(
                        placeholder, fontFamily = Sans, fontSize = 16.sp,
                        color = KC.Faint, textAlign = TextAlign.End,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    keyboardOptions = keyboard,
                    cursorBrush = SolidColor(KC.Coral),
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = KC.Ink, textAlign = TextAlign.End,
                        )
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
    }
}

/** Read-only counterpart of [ValueField] for values picked elsewhere (dates, pickers). */
@Composable
fun ReadOnlyField(label: String, value: String, divider: Boolean = true, onClick: (() -> Unit)? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
            Text(
                value, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = if (onClick != null) KC.Coral else KC.Ink,
            )
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
    }
}

/** The sheet-flavoured field: lilac rounded block rather than a card row. */
@Composable
fun SheetField(
    label: String,
    value: String,
    placeholder: String = "",
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    big: Boolean = false,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .padding(horizontal = 14.dp, vertical = if (big) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) {
                Text(
                    placeholder, fontFamily = Sans,
                    fontSize = if (big) 18.sp else 14.sp,
                    color = KC.Faint, textAlign = TextAlign.End,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = keyboard,
                cursorBrush = SolidColor(KC.Coral),
                textStyle = TextStyle(
                    fontFamily = Sans, fontWeight = FontWeight.Bold,
                    fontSize = if (big) 18.sp else 14.sp,
                    color = KC.Ink, textAlign = TextAlign.End,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Static label/value block used inside sheets for things the app decides (date, clinic). */
@Composable
fun SheetStatic(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
        Text(value, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink)
    }
}

/** Radio row for schedule pickers. */
@Composable
fun RadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = KC.Ink,
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                }
            }
            RadioDot(selected)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .border(2.dp, if (selected) KC.Coral else KC.CoralPale, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (selected) KC.Coral else Color.Transparent),
        )
    }
}

/** Small stat block: caption above a display-weight number. Used on Growth, Teeth, Vaccines. */
@Composable
fun StatCell(
    caption: String,
    value: String,
    note: String? = null,
    noteColor: Color = KC.Muted,
    modifier: Modifier = Modifier,
) {
    // Fills the row's height so a cell carrying a note is not taller than its neighbours.
    // See [StatRow], which is what gives the row a height to fill.
    KCard(modifier.fillMaxHeight(), corner = 16) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(caption, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
            Text(
                value, fontFamily = Display, fontWeight = FontWeight.Bold,
                fontSize = 20.sp, lineHeight = 26.sp, color = KC.Ink,
            )
            if (note != null) {
                Text(
                    note, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, color = noteColor,
                )
            }
        }
    }
}

/**
 * A row of [StatCell]s sharing the tallest one's height.
 *
 * The note line is optional, so a row mixing cells that have one with cells that do not came
 * out visibly ragged — the cards started level and ended at different depths. Measuring the
 * row at its minimum intrinsic height gives every cell the same box to fill.
 */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Plus / minus stepper around a display-weight number — the feed amount control. */
@Composable
fun Stepper(label: String, valueText: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KC.Screen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepperButton("−", onDecrement)
            Text(
                valueText, fontFamily = Display, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = KC.Ink,
                modifier = Modifier.width(72.dp), textAlign = TextAlign.Center,
            )
            StepperButton("+", onIncrement)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(KC.Surface)
            .border(1.dp, KC.BorderStrong, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = Sans, fontSize = 20.sp, color = KC.Coral)
    }
}
