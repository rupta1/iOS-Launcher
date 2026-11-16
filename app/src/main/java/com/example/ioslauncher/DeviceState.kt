package com.example.ioslauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class DeviceState(
    val online: Boolean,
    val transport: String,
    val wifiSsid: String,
    val bluetooth: String,
    val cellularType: String
)

object DeviceStateReader {
    @Suppress("DEPRECATION", "MissingPermission")
    fun read(context: Context): DeviceState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Offline / unknown"
        }

        val locationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val ssid = if (locationGranted) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.connectionInfo?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" } ?: "Unavailable"
            } catch (_: Exception) { "Unavailable" }
        } else "Permission required"

        val bluetooth = try {
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) "Permission required"
            else {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                if (bm.adapter?.isEnabled == true) "On" else "Off"
            }
        } catch (_: Exception) { "Unavailable" }

        val cellular = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                "Permission required"
            } else {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    else -> "Unknown"
                }
            }
        } catch (_: Exception) { "Unavailable" }

        return DeviceState(online, transport, ssid, bluetooth, cellular)
    }
}
