package com.selfness.mapswork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selfness.mapswork.BuildConfig
import com.selfness.mapswork.update.*

@Composable
fun UpdateCard(status: UpdateStatus, onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit) {
    val busy = status.phase in listOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING)
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("업데이트", style = MaterialTheme.typography.titleMedium)
            Text("현재 버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(when (status.phase) {
                UpdatePhase.IDLE -> "새 버전이 있는지 확인할 수 있어요."
                UpdatePhase.CHECKING -> "새 버전을 확인하고 있어요."
                UpdatePhase.CURRENT -> "최신 버전을 사용하고 있어요."
                UpdatePhase.AVAILABLE -> "${status.release?.versionName} 새 버전을 받을 수 있어요."
                UpdatePhase.UNSUPPORTED -> "새 버전을 설치하려면 휴대폰의 Android 업데이트가 필요해요."
                UpdatePhase.DOWNLOADING -> "업데이트를 받고 있어요."
                UpdatePhase.READY -> "다운로드가 끝났어요. 설치하면 저장한 상점이 유지돼요."
                UpdatePhase.ERROR -> status.message ?: "업데이트를 확인하지 못했어요."
            }, style = MaterialTheme.typography.bodySmall,
                color = if (status.phase == UpdatePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.phase in listOf(UpdatePhase.AVAILABLE, UpdatePhase.READY)) {
                status.release?.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
            if (status.phase == UpdatePhase.DOWNLOADING) {
                val total = status.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { (status.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${status.downloadedBytes * 100 / total}%", style = MaterialTheme.typography.labelSmall)
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (status.phase == UpdatePhase.CHECKING) "확인 중" else "업데이트 확인")
            }
            if (status.phase == UpdatePhase.AVAILABLE || (status.phase == UpdatePhase.ERROR && status.release != null)) {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("업데이트 다운로드") }
            }
            if (status.phase == UpdatePhase.READY) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("업데이트 설치") }
            }
        }
    }
}
