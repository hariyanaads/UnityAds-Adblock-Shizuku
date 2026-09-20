package com.dbzbanten.unityadblock

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService as AndroidVpnService
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private var updatingVpnSwitch = false

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: android.content.Context?,
            intent: Intent?
        ) {
            if (intent?.action != VpnService.ACTION_VPN_STATUS) return

            when (intent?.getStringExtra(VpnService.EXTRA_STATUS)) {
                VpnService.STATUS_STARTING -> {
                    tvStatus.text = "Starting VPN..."
                    cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.unity_gray
                        )
                    )
                }

                VpnService.STATUS_STARTED -> {
                    updatingVpnSwitch = true
                    switchVpn.isChecked = true
                    updatingVpnSwitch = false

                    tvStatus.text = "VPN Active"
                    cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.unity_blue
                        )
                    )
                }

                VpnService.STATUS_FAILED -> {
                    updatingVpnSwitch = true
                    switchVpn.isChecked = false
                    updatingVpnSwitch = false

                    tvStatus.text = "VPN Failed"
                    cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.unity_gray
                        )
                    )
                }

                VpnService.STATUS_STOPPED -> {
                    updatingVpnSwitch = true
                    switchVpn.isChecked = false
                    updatingVpnSwitch = false

                    tvStatus.text = "VPN Stopped"
                    cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.unity_gray
                        )
                    )
                }
            }
        }
    }
    
    private lateinit var shizukuHelper: ShizukuHelper
    private lateinit var hostsManager: HostsManager
    private lateinit var antiDetectManager: AntiDetectManager
    
    private lateinit var cardStatus: CardView
    private lateinit var tvStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var switchVpn: Switch
    private lateinit var switchAntiDetect: Switch
    private lateinit var switchUnityBypass: Switch
    private lateinit var switchRewardSpoof: Switch
    private lateinit var btnApplyHosts: Button
    private lateinit var btnCheckShizuku: Button
    private lateinit var tvDbzBanten: TextView
    private lateinit var tvVersion: TextView
    
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initManagers()
        setupListeners()
        updateUI()
        
        // Tampilkan nama DBZBANTEN
        tvDbzBanten.text = "DBZBANTEN EDITION"
        tvVersion.text = "v1.0.0 - Unity Ads Blocker"
    }
    
    private fun initViews() {
        cardStatus = findViewById(R.id.card_status)
        tvStatus = findViewById(R.id.tv_status)
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        switchVpn = findViewById(R.id.switch_vpn)
        switchAntiDetect = findViewById(R.id.switch_anti_detect)
        switchUnityBypass = findViewById(R.id.switch_unity_bypass)
        switchRewardSpoof = findViewById(R.id.switch_reward_spoof)
        btnApplyHosts = findViewById(R.id.btn_apply_hosts)
        btnCheckShizuku = findViewById(R.id.btn_check_shizuku)
        tvDbzBanten = findViewById(R.id.tv_dbzbanten)
        tvVersion = findViewById(R.id.tv_version)
    }
    
    private fun initManagers() {
        shizukuHelper = ShizukuHelper(this)
        hostsManager = HostsManager(this)
        antiDetectManager = AntiDetectManager(this)
    }
    
    private fun setupListeners() {
        btnCheckShizuku.setOnClickListener {
            checkShizukuStatus()
        }
        
        btnApplyHosts.setOnClickListener {
            applyHostsFile()
        }
        
        switchVpn.setOnCheckedChangeListener { _, isChecked ->
            if (updatingVpnSwitch) {
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                requestVpnPermission()
            } else {
                stopVpnService()
            }
        }
        
        switchAntiDetect.setOnCheckedChangeListener { _, isChecked ->
            antiDetectManager.setEnabled(isChecked)
            Toast.makeText(this, "Anti-Detect ${if(isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
        
        switchUnityBypass.setOnCheckedChangeListener { _, isChecked ->
            UnityBypassManager.setEnabled(isChecked)
            Toast.makeText(this, "Unity Bypass ${if(isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
        
        switchRewardSpoof.setOnCheckedChangeListener { _, isChecked ->
            UnityBypassManager.setRewardSpoof(isChecked)
            Toast.makeText(this, "Reward Spoof ${if(isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkShizukuStatus() {
        when {
            Shizuku.isPreV11() -> {
                tvShizukuStatus.text = "Shizuku: Pre-v11 (Update needed)"
                tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                tvShizukuStatus.text = "Shizuku: CONNECTED ✓"
                tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                btnApplyHosts.isEnabled = true
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                tvShizukuStatus.text = "Shizuku: Permission Denied"
                requestShizukuPermission()
            }
            else -> {
                requestShizukuPermission()
            }
        }
    }
    
    private fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Toast.makeText(this, "Install Shizuku first!", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun applyHostsFile() {
        lifecycleScope.launch {
            try {
                btnApplyHosts.isEnabled = false
                btnApplyHosts.text = "Applying..."
                
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    val result = hostsManager.applyHostsViaShizuku()
                    if (result) {
                        Toast.makeText(this@MainActivity, "Hosts applied successfully!", Toast.LENGTH_SHORT).show()
                        tvStatus.text = "System hosts modified"
                        cardStatus.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.unity_blue_dark))
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to apply hosts", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Shizuku permission required!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnApplyHosts.isEnabled = true
                btnApplyHosts.text = "Apply System Hosts"
            }
        }
    }
    
    private fun requestVpnPermission() {
        val intent = AndroidVpnService.prepare(this)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        tvStatus.text = "Starting VPN..."
        cardStatus.setCardBackgroundColor(
            ContextCompat.getColor(this, R.color.unity_gray)
        )

        val intent = Intent(this, VpnService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
    
    private fun stopVpnService() {
        val intent = Intent(this, VpnService::class.java)
        intent.action = "STOP"
        startService(intent)
        tvStatus.text = "VPN Stopped"
        cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.unity_gray))
    }
    
    private fun updateUI() {
        btnApplyHosts.isEnabled = false
        checkShizukuStatus()
    }
    
    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            vpnStatusReceiver,
            IntentFilter(VpnService.ACTION_VPN_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(vpnStatusReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        checkShizukuStatus()
    }
}