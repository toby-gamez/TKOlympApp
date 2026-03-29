package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun QuantityInput(
    value: Int,
    onValueChange: (Int, String) -> Unit,
    units: List<String>,
    defaultUnit: String? = null,
    label: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var number by remember { mutableStateOf(value) }
    var unit by remember { mutableStateOf(defaultUnit ?: units.firstOrNull().orEmpty()) }
    var expanded by remember { mutableStateOf(false) }

    // Outlined border for the whole field
    val borderColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(8.dp)

    Column(modifier = modifier.background(Color.Transparent)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(0.5.dp, borderColor, shape)
                .clip(shape)
                .background(Color.Transparent)
                .height(56.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = number.toString(),
                onValueChange = {
                    val v = it.filter { ch -> ch.isDigit() }.take(4)
                    number = v.toIntOrNull() ?: 0
                    onValueChange(number, unit)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .fillMaxHeight(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("0") }
            )
            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 64.dp)
                    .clickable(enabled) { expanded = true }
                    .padding(horizontal = 8.dp)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(unit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.background(Color.Transparent))
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 80.dp)
                ) {
                    units.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u) },
                            onClick = {
                                unit = u
                                expanded = false
                                onValueChange(number, unit)
                            }
                        )
                    }
                }
            }
        }
    }
}
