package com.dbzbanten.unityadblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class VpnService : AndroidVpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private lateinit var hostsManager: HostsManager

    companion object {
        const val TAG = "UnityVpnService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "unity_adblock_channel"

        private const val DNS_SERVER = "8.8.8.8"
        private const val DNS_PORT = 53

        const val ACTION_VPN_STATUS =
            "com.dbzbanten.unityadblock.VPN_STATUS"
        const val EXTRA_STATUS = "status"

        const val STATUS_STARTING = "STARTING"
        const val STATUS_STARTED = "STARTED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_STOPPED = "STOPPED"
    }

    override fun onCreate() {
        super.onCreate()
        hostsManager = HostsManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
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

            sendVpnStatus(STATUS_STARTING)
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
                .setSession("Unity Ads Blocker - DNS Filter")
                .addAddress("10.0.0.2", 24)
                .addDnsServer(DNS_SERVER)
                .addRoute(DNS_SERVER, 32)

            vpnInterface = builder.establish()
                ?: throw IllegalStateException(
                    "VPN interface could not be established"
                )

            isRunning = true

            Thread {
                processPackets()
            }.start()

            sendVpnStatus(STATUS_STARTED)
            Log.d(TAG, "VPN DNS filter started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            sendVpnStatus(STATUS_FAILED)
            cleanupVpn()
            stopSelf()
        }
    }

    private fun processPackets() {
        val fd = vpnInterface?.fileDescriptor ?: return

        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)

        val packet = ByteArray(32767)

        try {
            DatagramSocket().use { upstream ->

                if (!protect(upstream)) {
                    throw IllegalStateException(
                        "Unable to protect upstream DNS socket"
                    )
                }

                upstream.soTimeout = 2500

                val dnsAddress = InetAddress.getByName(DNS_SERVER)

                while (isRunning) {
                    val length = input.read(packet)

                    if (length <= 0) {
                        continue
                    }

                    val parsed = parseDnsPacket(packet, length)
                        ?: continue

                    val domain = parsed.domain

                    if (hostsManager.isDomainBlocked(domain)) {
                        Log.d(TAG, "Blocked DNS: $domain")

                        val response = buildNxDomainResponse(
                            packet,
                            length,
                            parsed
                        )

                        output.write(response)
                        output.flush()
                        continue
                    }

                    try {
                        val query = packet.copyOfRange(
                            parsed.dnsOffset,
                            length
                        )

                        val request = DatagramPacket(
                            query,
                            query.size,
                            dnsAddress,
                            DNS_PORT
                        )

                        upstream.send(request)

                        val responseBuffer = ByteArray(4096)

                        val responsePacket = DatagramPacket(
                            responseBuffer,
                            responseBuffer.size
                        )

                        upstream.receive(responsePacket)

                        val response = buildDnsResponsePacket(
                            packet,
                            length,
                            parsed,
                            responsePacket.data,
                            responsePacket.length
                        )

                        output.write(response)
                        output.flush()

                    } catch (e: Exception) {
                        Log.d(
                            TAG,
                            "DNS upstream failed for $domain: ${e.message}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "VPN packet loop failed", e)
            }
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }

            try {
                output.close()
            } catch (_: Exception) {
            }
        }
    }

    private data class DnsPacketInfo(
        val ipHeaderLength: Int,
        val udpOffset: Int,
        val dnsOffset: Int,
        val sourceIp: ByteArray,
        val destinationIp: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val domain: String,
        val questionEnd: Int
    )

    private fun parseDnsPacket(
        packet: ByteArray,
        length: Int
    ): DnsPacketInfo? {

        if (length < 28) return null

        val version = (packet[0].toInt() ushr 4) and 0x0f

        if (version != 4) return null

        val ipHeaderLength =
            (packet[0].toInt() and 0x0f) * 4

        if (ipHeaderLength < 20 || length < ipHeaderLength + 8) {
            return null
        }

        val protocol = packet[9].toInt() and 0xff

        if (protocol != 17) return null

        val udpOffset = ipHeaderLength

        val sourcePort =
            readU16(packet, udpOffset)

        val destinationPort =
            readU16(packet, udpOffset + 2)

        if (destinationPort != DNS_PORT) {
            return null
        }

        val udpLength =
            readU16(packet, udpOffset + 4)

        if (udpLength < 8 ||
            udpOffset + udpLength > length
        ) {
            return null
        }

        val dnsOffset = udpOffset + 8

        if (length < dnsOffset + 12) {
            return null
        }

        val qdCount = readU16(packet, dnsOffset + 4)

        if (qdCount < 1) return null

        var cursor = dnsOffset + 12
        val labels = ArrayList<String>()

        while (cursor < length) {
            val labelLength =
                packet[cursor].toInt() and 0xff

            cursor++

            if (labelLength == 0) {
                break
            }

            if ((labelLength and 0xc0) != 0) {
                return null
            }

            if (labelLength > 63 ||
                cursor + labelLength > length
            ) {
                return null
            }

            labels.add(
                String(
                    packet,
                    cursor,
                    labelLength,
                    Charsets.US_ASCII
                )
            )

            cursor += labelLength
        }

        if (labels.isEmpty() || cursor + 4 > length) {
            return null
        }

        val questionEnd = cursor + 4

        val sourceIp = packet.copyOfRange(12, 16)
        val destinationIp = packet.copyOfRange(16, 20)

        return DnsPacketInfo(
            ipHeaderLength = ipHeaderLength,
            udpOffset = udpOffset,
            dnsOffset = dnsOffset,
            sourceIp = sourceIp,
            destinationIp = destinationIp,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            domain = labels.joinToString(".").lowercase(),
            questionEnd = questionEnd
        )
    }

    private fun buildNxDomainResponse(
        queryPacket: ByteArray,
        queryLength: Int,
        info: DnsPacketInfo
    ): ByteArray {

        val dnsQuestionLength =
            info.questionEnd - info.dnsOffset

        val dnsLength = 12 + dnsQuestionLength
        val totalLength = 20 + 8 + dnsLength

        val response = ByteArray(totalLength)

        buildIpUdpHeader(
            response = response,
            totalLength = totalLength,
            sourceIp = info.destinationIp,
            destinationIp = info.sourceIp,
            sourcePort = DNS_PORT,
            destinationPort = info.sourcePort
        )

        val dnsOffset = 28

        response[dnsOffset] = queryPacket[info.dnsOffset]
        response[dnsOffset + 1] = queryPacket[info.dnsOffset + 1]

        val queryFlags =
            readU16(queryPacket, info.dnsOffset + 2)

        val responseFlags =
            0x8000 or
                0x0080 or
                (queryFlags and 0x0100) or
                0x0003

        writeU16(
            response,
            dnsOffset + 2,
            responseFlags
        )

        writeU16(response, dnsOffset + 4, 1)
        writeU16(response, dnsOffset + 6, 0)
        writeU16(response, dnsOffset + 8, 0)
        writeU16(response, dnsOffset + 10, 0)

        System.arraycopy(
            queryPacket,
            info.dnsOffset + 12,
            response,
            dnsOffset + 12,
            dnsQuestionLength - 12
        )

        updateChecksums(response)

        return response
    }

    private fun buildDnsResponsePacket(
        queryPacket: ByteArray,
        queryLength: Int,
        info: DnsPacketInfo,
        dnsResponse: ByteArray,
        dnsResponseLength: Int
    ): ByteArray {

        val dnsLength = dnsResponseLength
        val totalLength = 20 + 8 + dnsLength

        val response = ByteArray(totalLength)

        buildIpUdpHeader(
            response = response,
            totalLength = totalLength,
            sourceIp = info.destinationIp,
            destinationIp = info.sourceIp,
            sourcePort = DNS_PORT,
            destinationPort = info.sourcePort
        )

        System.arraycopy(
            dnsResponse,
            0,
            response,
            28,
            dnsLength
        )

        updateChecksums(response)

        return response
    }

    private fun buildIpUdpHeader(
        response: ByteArray,
        totalLength: Int,
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int
    ) {

        response[0] = 0x45
        response[1] = 0

        writeU16(response, 2, totalLength)
        writeU16(response, 4, 0)
        writeU16(response, 6, 0)

        response[8] = 64
        response[9] = 17

        System.arraycopy(
            sourceIp,
            0,
            response,
            12,
            4
        )

        System.arraycopy(
            destinationIp,
            0,
            response,
            16,
            4
        )

        writeU16(response, 20, sourcePort)
        writeU16(response, 22, destinationPort)

        writeU16(
            response,
            24,
            totalLength - 20
        )

        writeU16(response, 26, 0)
    }

    private fun updateChecksums(packet: ByteArray) {
        writeU16(
            packet,
            10,
            0
        )

        val ipChecksum =
            internetChecksum(
                packet,
                0,
                20
            )

        writeU16(
            packet,
            10,
            ipChecksum
        )

        val udpLength =
            readU16(packet, 24)

        writeU16(packet, 26, 0)

        val udpChecksum =
            udpChecksum(
                packet,
                udpLength
            )

        writeU16(
            packet,
            26,
            if (udpChecksum == 0) 0xffff else udpChecksum
        )
    }

    private fun udpChecksum(
        packet: ByteArray,
        udpLength: Int
    ): Int {

        var sum = 0

        sum += readU16(packet, 12)
        sum += readU16(packet, 14)
        sum += readU16(packet, 16)
        sum += readU16(packet, 18)

        sum += 17
        sum += udpLength

        sum += checksumSum(
            packet,
            20,
            udpLength
        )

        return finishChecksum(sum)
    }

    private fun checksumSum(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {

        var sum = 0
        var index = offset
        val end = offset + length

        while (index + 1 < end) {
            sum +=
                ((data[index].toInt() and 0xff) shl 8) or
                    (data[index + 1].toInt() and 0xff)

            index += 2
        }

        if (index < end) {
            sum +=
                (data[index].toInt() and 0xff) shl 8
        }

        return sum
    }

    private fun internetChecksum(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        return finishChecksum(
            checksumSum(
                data,
                offset,
                length
            )
        )
    }

    private fun finishChecksum(sumInput: Int): Int {
        var sum = sumInput

        while ((sum ushr 16) != 0) {
            sum =
                (sum and 0xffff) +
                    (sum ushr 16)
        }

        return sum.inv() and 0xffff
    }

    private fun readU16(
        data: ByteArray,
        offset: Int
    ): Int {
        return ((data[offset].toInt() and 0xff) shl 8) or
            (data[offset + 1].toInt() and 0xff)
    }

    private fun writeU16(
        data: ByteArray,
        offset: Int,
        value: Int
    ) {
        data[offset] =
            ((value ushr 8) and 0xff).toByte()

        data[offset + 1] =
            (value and 0xff).toByte()
    }

    private fun stopVpn() {
        isRunning = false

        vpnInterface?.close()
        vpnInterface = null

        sendVpnStatus(STATUS_STOPPED)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "VPN Stopped")
    }

    private fun sendVpnStatus(status: String) {
        sendBroadcast(
            Intent(ACTION_VPN_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, status)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unity Ads Blocker",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent =
            Intent(this, MainActivity::class.java)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Unity Ads Blocker - DBZBANTEN"
            )
            .setContentText(
                "VPN Active - DNS filtering..."
            )
            .setSmallIcon(
                android.R.drawable.ic_secure
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked")

        sendVpnStatus(STATUS_FAILED)

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
