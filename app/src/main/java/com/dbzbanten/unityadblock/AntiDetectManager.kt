package com.dbzbanten.unityadblock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manager untuk bypass detektor root dan Xposed
 * Menggunakan teknik anti-deteksi yang kuat
 */
class AntiDetectManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("anti_detect", Context.MODE_PRIVATE)
    
    companion object {
        const val TAG = "AntiDetect"
        
        // Daftar package detektor yang harus di-bypass
        val DETECTOR_PACKAGES = listOf(
            "com.scottyab.rootbeer.sample",
            "com.koushikdutta.rootchecker",
            "org.rootdetector",
            "com.rootdetector",
            "com.google.android.gms",
            "com.playgames.services",
            "com.unity3d.ads",
            "com.ironsource.app",
            "com.applovin.sdk"
        )
    }
    
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }
    
    fun isEnabled(): Boolean {
        return prefs.getBoolean("enabled", false)
    }
    
    /**
     * Teknik bypass yang digunakan:
     * 1. Hide Xposed/LSPosed dari ClassLoader
     * 2. Spoof Build properties
     * 3. Block exec() commands
     * 4. Hide files yang terkait root
     */
    fun getBypassScript(): String {
        return """
            #!/system/bin/sh
            # Anti-Detect Script by DBZBANTEN
            
            # Hide Xposed dari detektor
            export XPOSED_HIDE=1
            
            # Spoof Build properties
            resetprop ro.debuggable 0
            resetprop ro.secure 1
            resetprop ro.build.tags release-keys
            resetprop ro.build.type user
            
            # Hide Magisk
            magisk --hide
            
            # Block deteksi file
            for path in /su /system/bin/su /system/xbin/su /magisk /data/adb/magisk; do
                mount -o bind /dev/null "$path" 2>/dev/null
            done
            
            # Hide Shizuku traces
            pm hide moe.shizuku.privileged.api 2>/dev/null
            
            echo "Anti-Detect Active"
        """.trimIndent()
    }
    
    fun applyBypassViaShizuku(shizukuHelper: ShizukuHelper): Boolean {
        return try {
            val script = getBypassScript()
            val tempFile = java.io.File.createTempFile("bypass", ".sh")
            tempFile.writeText(script)
            
            shizukuHelper.executeCommand("sh ${tempFile.absolutePath}")
            tempFile.delete()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying bypass: ${e.message}")
            false
        }
    }
}