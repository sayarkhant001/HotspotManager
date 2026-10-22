package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Router auto-discovery for the 0-tech-knowledge onboarding flow.
 *
 * When the phone is attached to the router's Wi-Fi, the phone's own DHCP
 * gateway IS the MikroTik's LAN address — so the user never has to type an IP.
 * Candidate order:
 *  1. live DHCP gateway from ConnectivityManager (the network the phone is on)
 *  2. DHCP gateway from WifiManager (broad legacy fallback)
 *  3. RouterOS factory default (192.168.88.1)
 *  4. this system's standard hotspot gateway (10.10.10.1)
 */
object RouterDiscovery {

    /** Candidate router IPs in priority order. */
    fun candidateIps(context: Context): List<String> = buildList {
        gatewayFromConnectivity(context)?.let { add(it) }
        gatewayFromWifiManager(context)?.let { g -> if (!contains(g)) add(g) }
        add("192.168.88.1")
        add("10.10.10.1")
    }.distinct()

    private fun gatewayFromConnectivity(context: Context): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        var gw: String? = null
        for (route in lp.routes) {
            if (route.isDefaultRoute) {
                val g = route.gateway ?: continue
                if (g.address.size == 4) { gw = g.hostAddress; break }
            }
        }
        gw
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun gatewayFromWifiManager(context: Context): String? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo ?: return null
        if (dhcp.gateway != 0) {
            val g = dhcp.gateway
            "%d.%d.%d.%d".format(
                g and 0xFF, (g shr 8) and 0xFF, (g shr 16) and 0xFF, (g shr 24) and 0xFF
            )
        } else null
    } catch (_: Exception) { null }

    /** True if the RouterOS API port answers (quick TCP probe). */
    fun apiPortReachable(host: String, port: Int = 8728, timeoutMs: Int = 1500): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            true
        }
    } catch (_: Exception) { false }

    /** First candidate whose API port actually answers; null when none respond. */
    fun detectRouter(context: Context): String? =
        candidateIps(context).firstOrNull { apiPortReachable(it) }
}