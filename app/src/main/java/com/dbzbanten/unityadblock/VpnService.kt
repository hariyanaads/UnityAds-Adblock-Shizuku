package com.dbzbanten.unityadblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class VpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private lateinit var hostsManager: HostsManager
    
    companion object {
        const val TAG = "UnityVpnService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "unity_adblock_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        hostsManager = HostsManager(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )
            }

            startVpn()
            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service", e)
            cleanupVpn()
            stopSelf()
            return START_NOT_STICKY
        }
    }
    
    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("Unity Ads Blocker")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .establish()
                ?: throw IllegalStateException("VPN interface could not be established")

            vpnInterface = builder
            isRunning = true

            Thread { processPackets() }.start()

            Log.d(TAG, "VPN Started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            cleanupVpn()
            stopSelf()
        }
    }
    
    private fun processPackets() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        
        val buffer = ByteBuffer.allocate(32767)
        
        while (isRunning) {
            try {
                val length = input.read(buffer.array())
                if (length > 0) {
                    // Process packet - cek apakah tujuannya domain yang diblokir
                    // Implementasi sederhana: drop paket ke IP yang terdaftar
                    buffer.clear()
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Error processing packet: ${e.message}")
            }
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN Stopped")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unity Ads Blocker",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Unity Ads Blocker - DBZBANTEN")
            .setContentText("VPN Active - Blocking ads...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked")
        cleanupVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        cleanupVpn()
        super.onDestroy()
    }

    private fun cleanupVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}