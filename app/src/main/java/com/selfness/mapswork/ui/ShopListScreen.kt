package com.selfness.mapswork.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.selfness.mapswork.core.Shop
import com.selfness.mapswork.location.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopListScreen(
    shops: List<Shop>, alertsEnabled: Boolean, status: RegistrationStatus,
    onAdd: () -> Unit, onEdit: (String) -> Unit, onSettings: () -> Unit,
    onToggle: (String, Boolean) -> Unit, snackbar: SnackbarHostState,
) {
    val displayedStatus = if (alertsEnabled) status else RegistrationStatus(RegistrationPhase.PAUSED)
    Scaffold(
        topBar = { TopAppBar(title = { Text("가까운 상점", fontWeight = FontWeight.Bold) }, actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "알림 설정") }
        }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd,
            modifier = Modifier.semantics { contentDescription = "상점 추가" },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("상점 추가") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(onClick = onSettings, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (displayedStatus.phase == RegistrationPhase.READY) Icons.Default.CheckCircle else Icons.Default.Info,
                            null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(displayedStatus.description(), style = MaterialTheme.typography.titleSmall)
                            Text("알림 설정에서 상태를 확인할 수 있어요", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.KeyboardArrowRight, null)
                    }
                }
            }
            item {
                Text("저장한 상점 ${shops.size}곳 · 알림 선택 ${shops.count { it.enabled }}/100",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shops.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Place, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("다시 들르고 싶은 곳을 저장해 보세요", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("지도에서 위치를 골라 두면\n근처에 갔을 때 알려 드려요.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(shops, key = { it.id }) { shop ->
                Card {
                    Row(Modifier.fillMaxWidth().clickable { onEdit(shop.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(shop.name, style = MaterialTheme.typography.titleMedium)
                            Text("반경 ${radiusLabel(shop.radiusMeters)} · ${if (shop.enabled) "알림 선택됨" else "알림 꺼짐"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = shop.enabled, onCheckedChange = { onToggle(shop.id, it) })
                    }
                }
            }
        }
    }
}
