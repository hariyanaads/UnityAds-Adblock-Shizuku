package com.dbzbanten.unityadblock

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager untuk bypass Unity Ads SDK
 * Menggunakan teknik intercept dan spoof reward
 */
object UnityBypassManager {
    
    private var prefs: SharedPreferences? = null
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences("unity_bypass", Context.MODE_PRIVATE)
    }
    
    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("enabled", enabled)?.apply()
    }
    
    fun isEnabled(): Boolean {
        return prefs?.getBoolean("enabled", false) ?: false
    }
    
    fun setRewardSpoof(enabled: Boolean) {
        prefs?.edit()?.putBoolean("reward_spoof", enabled)?.apply()
    }
    
    fun isRewardSpoofEnabled(): Boolean {
        return prefs?.getBoolean("reward_spoof", false) ?: false
    }
    
    fun getRewardMultiplier(): Int {
        return prefs?.getInt("multiplier", 1000) ?: 1000
    }
    
    fun setRewardMultiplier(multiplier: Int) {
        prefs?.edit()?.putInt("multiplier", multiplier)?.apply()
    }
    
    /**
     * Konfigurasi untuk intercept Unity Ads
     */
    fun getUnityBypassConfig(): String {
        return """
            {
                "block_unity_ads": true,
                "block_ironsource": true,
                "block_applovin": true,
                "block_admob": true,
                "spoof_reward": ${isRewardSpoofEnabled()},
                "reward_multiplier": ${getRewardMultiplier()},
                "anti_cheat_bypass": true,
                "hide_xposed": true,
                "traffic_manipulation": true
            }
        """.trimIndent()
    }
}