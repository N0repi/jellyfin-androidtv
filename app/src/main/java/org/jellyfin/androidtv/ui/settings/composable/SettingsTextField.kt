package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun SettingsTextField(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	keyboardType: KeyboardType = KeyboardType.Text,
	isSecret: Boolean = false,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val colors = if (focused) {
		JellyfinTheme.colorScheme.inputFocused to JellyfinTheme.colorScheme.onInputFocused
	} else {
		JellyfinTheme.colorScheme.input to JellyfinTheme.colorScheme.onInput
	}

	Column(modifier = modifier.fillMaxWidth()) {
		Text(
			text = label,
			modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
		)
		ProvideTextStyle(
			LocalTextStyle.current.copy(
				color = colors.second,
				fontSize = 16.sp,
			)
		) {
			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				modifier = Modifier
					.fillMaxWidth()
					.border(2.dp, colors.first, RoundedCornerShape(percent = 30))
					.padding(horizontal = 16.dp, vertical = 12.dp),
				singleLine = true,
				interactionSource = interactionSource,
				keyboardOptions = KeyboardOptions(
					keyboardType = keyboardType,
					showKeyboardOnFocus = true,
				),
				visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
				textStyle = LocalTextStyle.current,
				cursorBrush = SolidColor(colors.first),
			)
		}
	}
}
