package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.tkolymp.tkolympapp.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
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
            label = label?.let { { Text(it) } },
            modifier = Modifier.weight(1f),
            placeholder = { Text("0") }
        )
        Box(
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 72.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled) { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(unit, style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
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
