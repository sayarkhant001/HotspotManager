package com.example.domain.models

/** One WAN-capable interface on the router (ether / pppoe-out). */
data class UplinkInfo(
    val iface: String = "",
    val kind: String = "ether",          // "ether" | "pppoe"
    val address: String = "",            // WAN IP/CIDR
    val gateway: String = "",            // gateway used by an existing default route
    val dhcpBound: Boolean = false,
    val pppoeConnected: Boolean = false,
    val disabled: Boolean = false,
    val hasDefaultRoute: Boolean = false
) {
    val isOnline: Boolean
        get() = !disabled && (dhcpBound || pppoeConnected || hasDefaultRoute)
}

/** Live traffic counters for one uplink interface. */
data class UplinkTraffic(
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L
)

/** One uplink slot in a load-balance plan. */
data class UplinkAssignment(
    val iface: String,
    val gateway: String = "",   // blank = auto (pppoe interface / ether interface gateway)
    val weight: Int = 1         // PCC bandwidth share 1..4 (ignored in failover mode)
)

/** The whole multi-WAN plan the app deploys as one RSC script. */
data class LoadBalancePlan(
    val strategy: String = "pcc",           // "pcc" | "failover"
    val uplinks: List<UplinkAssignment> = emptyList(),
    val hotspotInterface: String = "hotspot-bridge"
)