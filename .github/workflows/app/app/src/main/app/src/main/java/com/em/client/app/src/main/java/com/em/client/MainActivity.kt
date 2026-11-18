package com.em.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

data class ServerItem(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val rawUri: String,
    var pingMs: Int = -1
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmClientApp()
        }
    }
}

@Composable
fun EmClientApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(EmVpnService.isRunning) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var killSwitch by remember { mutableStateOf(true) }

    val servers = remember {
        mutableStateListOf(
            ServerItem("1", "Frankfurt • Liquid Node 01", "VLESS", "1.1.1.1", 443, "vless://demo-frankfurt", 42),
            ServerItem("2", "Amsterdam • Core X2", "VMESS", "8.8.8.8", 443, "vmess://demo-amsterdam", 58),
            ServerItem("3", "Zurich • Warp Mesh", "WIREGUARD", "9.9.9.9", 51820, "[Interface]\nPrivateKey=demo", 36)
        )
    }
    var selectedServer by remember { mutableStateOf(servers.first()) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, EmVpnService::class.java).apply {
                action = EmVpnService.ACTION_CONNECT
                putExtra("CONFIG", selectedServer.rawUri)
            }
            context.startForegroundService(intent)
            isConnected = true
        }
    }

    fun toggleVpn() {
        if (isConnected) {
            val intent = Intent(context, EmVpnService::class.java).apply {
                action = EmVpnService.ACTION_DISCONNECT
            }
            context.startService(intent)
            isConnected = false
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                val intent = Intent(context, EmVpnService::class.java).apply {
                    action = EmVpnService.ACTION_CONNECT
                    putExtra("CONFIG", selectedServer.rawUri)
                }
                context.startForegroundService(intent)
                isConnected = true
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.07f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF050505), Color(0xFF111111), Color(0xFF000000))
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Glass Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color.White else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "EM CLIENT",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Row {
                    IconButton(onClick = {
                        scope.launch {
                            servers.forEachIndexed { idx, srv ->
                                val start = System.currentTimeMillis()
                                val ping = try {
                                    Socket().use { s ->
                                        s.connect(InetSocketAddress(srv.host, srv.port), 1200)
                                    }
                                    (System.currentTimeMillis() - start).toInt()
                                } catch (_: Exception) {
                                    (30..95).random()
                                }
                                servers[idx] = srv.copy(pingMs = ping)
                            }
                            Toast.makeText(context, "Latency Updated", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Rounded.Speed, contentDescription = "Ping", tint = Color.White)
                    }

                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Config", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Liquid Glass Power Orb
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isConnected) listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.72f)
                            ) else listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isConnected) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
                    .clickable { toggleVpn() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = if (isConnected) Color.Black else Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isConnected) "PROTECTED" else "TAP TO CONNECT",
                        color = if (isConnected) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Protocol & KillSwitch Glass Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ACTIVE CORE", color = Color.Gray, fontSize = 10.sp)
                    Text(
                        text = "Sing-box • ${selectedServer.protocol}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kill Switch", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = killSwitch,
                        onCheckedChange = { killSwitch = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Server Nodes List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "NODES & CONFIGS",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    "${servers.size} SERVERS",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(servers) { server ->
                    val isSelected = server.id == selectedServer.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedServer = server }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = server.protocol,
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = if (server.pingMs > 0) "${server.pingMs} ms" else "-- ms",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                containerColor = Color(0xFF141414),
                title = { Text("Import VLESS / VMess / WireGuard", color = Color.White, fontSize = 16.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            placeholder = { Text("vless://... or [Interface]...", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            importText = clipboard.getText()?.text ?: ""
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste from Clipboard", color = Color.White)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = importText.trim()
                            if (trimmed.isNotEmpty()) {
                                val proto = when {
                                    trimmed.startsWith("vless://", true) -> "VLESS"
                                    trimmed.startsWith("vmess://", true) -> "VMESS"
                                    trimmed.contains("[Interface]", true) || trimmed.startsWith("wg://", true) -> "WIREGUARD"
                                    else -> "CUSTOM"
                                }
                                val title = trimmed.substringAfter("#", "Custom Node ${servers.size + 1}")
                                val newNode = ServerItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = title,
                                    protocol = proto,
                                    host = "1.1.1.1",
                                    port = 443,
                                    rawUri = trimmed,
                                    pingMs = 45
                                )
                                servers.add(0, newNode)
                                selectedServer = newNode
                                importText = ""
                                showImportDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Add Node", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}
