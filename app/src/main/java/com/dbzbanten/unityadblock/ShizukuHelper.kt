package com.dbzbanten.unityadblock

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.*

class ShizukuHelper(private val context: Context) {
    
    fun isShizukuAvailable(): Boolean {
        return Shizuku.isPreV11() || Shizuku.pingBinder()
    }
    
    fun hasPermission(): Boolean {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }
    
    fun executeCommand(command: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            
            if (error.isNotEmpty()) "Error: $error" else output
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
    
    fun writeFileWithRoot(path: String, content: String): Boolean {
        return try {
            // Gunakan Shizuku untuk menulis ke file sistem
            val tempFile = File(context.cacheDir, "temp_hosts")
            tempFile.writeText(content)
            
            val command = "cat ${tempFile.absolutePath} > $path && chmod 644 $path"
            val result = executeCommand(command)
            
            tempFile.delete()
            result.contains("Error").not()
        } catch (e: Exception) {
            false
        }
    }
    
    fun mountSystemWritable(): Boolean {
        val commands = listOf(
            "mount -o remount,rw /system",
            "mount -o remount,rw /",
            "mount -o remount,rw /system/etc"
        )
        
        for (cmd in commands) {
            executeCommand(cmd)
        }
        return true
    }
    
    fun backupHosts(): Boolean {
        return try {
            executeCommand("cp /system/etc/hosts /system/etc/hosts.bak")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun restoreHosts(): Boolean {
        return try {
            executeCommand("cp /system/etc/hosts.bak /system/etc/hosts")
            true
        } catch (e: Exception) {
            false
        }
    }
}