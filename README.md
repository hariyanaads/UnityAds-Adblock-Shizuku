# Unity Ads Blocker - DBZBANTEN Edition

![Unity Ads Blocker](https://img.shields.io/badge/Unity-Ads%20Blocker-blue)
![Shizuku](https://img.shields.io/badge/Shizuku-Required-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

Aplikasi VPN Adblock khusus untuk memblokir Unity Ads dan SDK iklan lainnya pada game Android. Menggunakan Shizuku untuk akses file sistem tanpa root.

## Fitur

- 🛡️ **Blokir Unity Ads** - Memblokir semua domain Unity Ads
- 🎮 **Game Bypass** - Bypass anti-cheat dan detektor root
- 💰 **Reward Spoof** - Multiply reward dari iklan (1000x)
- 🔒 **Shizuku Integration** - Non-root system hosts modification
- 🌐 **VPN Mode** - Local VPN untuk device non-root
- 🎯 **Anti-Detect** - Bypass Ruru, Momo, dan detektor lainnya

## Screenshot

Tampilan aplikasi menggunakan tema Unity Ads (Blue) dengan nama DBZBANTEN.

## Persyaratan

- Android 8.0+ (API 26)
- Shizuku terinstall ([Download dari Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api))
- USB Debugging atau Wireless Debugging aktif

## Instalasi

### Mode Shizuku (Rekomendasi)
1. Install Shizuku dari Play Store
2. Aktifkan Shizuku via Wireless Debugging atau ADB
3. Buka Unity Ads Blocker
4. Tap "Check Shizuku Connection"
5. Tap "Apply System Hosts"
6. Selesai!

### Mode VPN (Alternatif)
1. Buka aplikasi
2. Aktifkan toggle "VPN Mode"
3. Izinkan permission VPN
4. Iklan akan diblokir via local VPN

## Daftar yang Diblokir

### Unity Ads
- unityads.unity3d.com
- auction.unityads.unity3d.com
- analytics.unity3d.com
- Semua subdomain Unity Ads

### SDK Lainnya
- IronSource
- AppLovin
- AdMob
- Mintegral
- Vungle
- AdColony
- Chartboost
- Tapjoy
- Adjust
- AppsFlyer

## Bypass Anti-Detect

Aplikasi ini menyertakan teknik bypass untuk:
- RootBeer
- SafetyNet
- Play Integrity API
- Ruru (Xposed Detector)
- Momo (Root Detector)

## Struktur Proyek