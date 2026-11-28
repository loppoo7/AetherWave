package com.aetherwave.core.crash

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.aetherwave.R

/** Shows the report saved by [CrashReporter] from the previous run, with a Copy button. */
@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Read string resources here, in composable scope, not inside the click handler.
    val reportTitle = stringResource(R.string.crash_report_title)
    val copyHint = stringResource(R.string.crash_copy_hint)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(
                    copyHint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Text(report, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText(reportTitle, report))
            }) { Text(stringResource(R.string.action_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
