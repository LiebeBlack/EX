package com.apex.files.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.helpTips
import com.apex.files.ui.theme.ApexBlack
import com.apex.files.ui.theme.ApexBorderSubtle

/**
 * Flat black top bar: back arrow, title (optionally with a subtitle),
 * a contextual help button ("?") and the screen's actions, with a subtle
 * bottom border. The help button shows tips for the current screen.
 */
@Composable
fun ApexTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val tips = remember(navigator.current) { helpTips(navigator.current) }
    var showHelp by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .background(ApexBlack),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 58.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                ApexIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", onClick = onBack)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (tips != null) {
                ApexIconButton(
                    Icons.Outlined.HelpOutline,
                    "Ayuda de esta pantalla",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showHelp = true },
                )
            }
            actions()
        }
        HorizontalDivider(color = ApexBorderSubtle, thickness = 1.dp)
    }

    if (showHelp && tips != null) {
        HelpSheet(tips = tips, onDismiss = { showHelp = false })
    }
}