package com.em.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class EmVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "com.em.client.CONNECT"
        const val ACTION_DISCONNECT = "com.em.client.DISCONNECT"
        var isRunning = false
        var currentConfig = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                currentConfig = intent.getStringExtra("CONFIG") ?: ""
                startVpnTunnel()
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
            }
        }
        return START_STICKY
    }

    private fun startVpnTunnel() {
        if (isRunning) return
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, "EM_VPN_CHANNEL")
            .setContentTitle("EM CLIENT")
            .setContentText("Connected • Encrypted Liquid Tunnel Active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        val builder = Builder()
            .setSession("EM CLIENT")
            .setMtu(1500)
            .addAddress("172.19.0.1", 30)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {}

        vpnInterface = builder.establish()
        isRunning = true
    }

    private fun stopVpnTunnel() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "EM_VPN_CHANNEL",
                "EM CLIENT VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }
}
