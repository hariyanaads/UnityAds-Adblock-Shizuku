package com.dbzbanten.unityadblock

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.File

class ShizukuHelper(private val context: Context) {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun executeCommand(command: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )

            method.isAccessible = true

            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as ShizukuRemoteProcess

            val output = process.inputStream.bufferedReader().use {
                it.readText()
            }

            val error = process.errorStream.bufferedReader().use {
                it.readText()
            }

            val exitCode = process.waitFor()
            process.destroy()

            if (exitCode != 0) {
                "Error: exit=$exitCode ${error.ifBlank { output }}".trim()
            } else {
                output
            }
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }

    fun writeFileWithRoot(path: String, content: String): Boolean {
        return try {
            val tempFile = File(context.cacheDir, "temp_hosts")
            tempFile.writeText(content)

            val escapedTemp = tempFile.absolutePath.replace("'", "'\\''")
            val escapedPath = path.replace("'", "'\\''")

            val command =
                "cat '$escapedTemp' > '$escapedPath' && chmod 644 '$escapedPath'"

            val result = executeCommand(command)

            tempFile.delete()

            !result.startsWith("Error:") &&
                !result.startsWith("Exception:")
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
            val result = executeCommand(cmd)

            if (!result.startsWith("Error:") &&
                !result.startsWith("Exception:")) {
                return true
            }
        }

        return false
    }

    fun backupHosts(): Boolean {
        return try {
            val result = executeCommand(
                "cp /system/etc/hosts /system/etc/hosts.bak"
            )
            !result.startsWith("Error:") &&
                !result.startsWith("Exception:")
        } catch (e: Exception) {
            false
        }
    }

    fun restoreHosts(): Boolean {
        return try {
            val result = executeCommand(
                "cp /system/etc/hosts.bak /system/etc/hosts"
            )
            !result.startsWith("Error:") &&
                !result.startsWith("Exception:")
        } catch (e: Exception) {
            false
        }
    }
}
