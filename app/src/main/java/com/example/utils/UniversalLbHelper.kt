package com.example.utils

import com.example.domain.models.LoadBalancePlan
import com.example.domain.models.UplinkAssignment

/**
 * Multi-WAN Load Balancing RSC generator (100% app-side, no VPS).
 * "pcc": new connections spread by bandwidth weights; per-uplink routing
 * tables + check-gateway + failover routes + NAT. "failover": priority
 * distances in the main table. Every rule is tagged "LB-MANAGED" so cleanup
 * removes exactly what we deployed — the shop's own rules are never touched.
 */
object UniversalLbHelper {

    const val LB_TAG = "LB-MANAGED"

    private fun esc(s: String): String = s.replace("\"", "").trim()

    private fun gwOf(u: UplinkAssignment): String = esc(u.gateway).ifBlank { esc(u.iface) }

    fun generateCleanupScript(): String = buildString {
        appendLine("# ==== HotspotManager Load Balancing cleanup ====")
        appendLine(":do { :foreach r in=[/ip firewall mangle find comment~\"$LB_TAG\"] do={ /ip firewall mangle remove \${'$'}r } } on-error={}")
        appendLine(":do { :foreach r in=[/ip firewall nat find comment~\"$LB_TAG\"] do={ /ip firewall nat remove \${'$'}r } } on-error={}")
        appendLine(":do { :foreach r in=[/ip route find comment~\"$LB_TAG\"] do={ /ip route remove \${'$'}r } } on-error={}")
        appendLine(":do { :foreach r in=[/route find comment~\"$LB_TAG\"] do={ /route remove \${'$'}r } } on-error={}")
        appendLine(":do { :foreach r in=[/routing table find comment~\"$LB_TAG\"] do={ /routing table remove \${'$'}r } } on-error={}")
        appendLine(":log info \"HotspotManager LB cleanup done\"")
    }

    fun generateDeployScript(plan: LoadBalancePlan): String {
        val ups = plan.uplinks.filter { it.iface.isNotBlank() }
        require(ups.size >= 2) { "At least 2 uplinks required" }
        val hotspot = esc(plan.hotspotInterface).ifBlank { "hotspot-bridge" }
        return buildString {
            appendLine("# ==== HotspotManager Multi-WAN Load Balancing ====")
            appendLine("# ${plan.strategy}: ${ups.joinToString(",") { it.iface }}")
            appendLine(":do { :foreach r in=[/ip firewall mangle find comment~\"$LB_TAG\"] do={ /ip firewall mangle remove \${'$'}r } } on-error={}")
            appendLine(":do { :foreach r in=[/ip firewall nat find comment~\"$LB_TAG\"] do={ /ip firewall nat remove \${'$'}r } } on-error={}")
            appendLine(":do { :foreach r in=[/ip route find comment~\"$LB_TAG\"] do={ /ip route remove \${'$'}r } } on-error={}")
            appendLine(":do { :foreach r in=[/route find comment~\"$LB_TAG\"] do={ /route remove \${'$'}r } } on-error={}")
            appendLine(":do { :foreach r in=[/routing table find comment~\"$LB_TAG\"] do={ /routing table remove \${'$'}r } } on-error={}")
            for (u in ups) {
                val i = esc(u.iface)
                appendLine(":do { :if ([:len [/ip firewall nat find chain=srcnat out-interface=\"$i\" action=masquerade]] = 0) do={ /ip firewall nat add chain=srcnat out-interface=\"$i\" action=masquerade comment=\"$LB_TAG\" } } on-error={}")
            }
            if (plan.strategy == "failover") {
                ups.forEachIndexed { idx, u ->
                    val gw = gwOf(u)
                    appendLine(":do { /route add dst-address=0.0.0.0/0 gateway=$gw check-gateway=ping distance=${idx + 1} comment=\"$LB_TAG\" } on-error={ :do { /ip route add dst-address=0.0.0.0/0 gateway=$gw check-gateway=ping distance=${idx + 1} comment=\"$LB_TAG\" } on-error={} }")
                }
            } else {
                ups.forEachIndexed { n, _ ->
                    appendLine(":do { /routing table add name=lb-tbl-$n fib comment=\"$LB_TAG\" } on-error={}")
                }
                val weights = ups.map { it.weight.coerceIn(1, 4) }
                val total = weights.sum()
                var rem = 0
                ups.forEachIndexed { n, _ ->
                    repeat(weights[n]) {
                        appendLine(":do { /ip firewall mangle add chain=prerouting in-interface=\"$hotspot\" connection-state=new dst-address-type=!local per-connection-classifier=both-addresses-and-ports:$total/$rem action=mark-connection new-connection-mark=lb-con-$n passthrough=yes comment=\"$LB_TAG\" } on-error={}")
                        rem++
                    }
                }
                ups.forEachIndexed { n, _ ->
                    appendLine(":do { /ip firewall mangle add chain=prerouting in-interface=\"$hotspot\" connection-mark=lb-con-$n action=mark-routing new-routing-mark=lb-tbl-$n passthrough=yes comment=\"$LB_TAG\" } on-error={}")
                }
                ups.forEachIndexed { n, u ->
                    appendLine(":do { /route add dst-address=0.0.0.0/0 gateway=${gwOf(u)} routing-table=lb-tbl-$n check-gateway=ping distance=1 comment=\"$LB_TAG\" } on-error={ :do { /ip route add dst-address=0.0.0.0/0 gateway=${gwOf(u)} routing-mark=lb-tbl-$n check-gateway=ping distance=1 comment=\"$LB_TAG\" } on-error={} }")
                    ups.forEachIndexed { m, other ->
                        if (m != n) {
                            val d = m + 2
                            appendLine(":do { /route add dst-address=0.0.0.0/0 gateway=${gwOf(other)} routing-table=lb-tbl-$n check-gateway=ping distance=$d comment=\"$LB_TAG failover\" } on-error={ :do { /ip route add dst-address=0.0.0.0/0 gateway=${gwOf(other)} routing-mark=lb-tbl-$n check-gateway=ping distance=$d comment=\"$LB_TAG failover\" } on-error={} }")
                        }
                    }
                }
                ups.drop(1).forEachIndexed { k, u ->
                    val d = k + 2
                    appendLine(":do { /route add dst-address=0.0.0.0/0 gateway=${gwOf(u)} routing-table=main check-gateway=ping distance=$d comment=\"$LB_TAG\" } on-error={ :do { /ip route add dst-address=0.0.0.0/0 gateway=${gwOf(u)} check-gateway=ping distance=$d comment=\"$LB_TAG\" } on-error={} }")
                }
            }
            appendLine(":log info \"HotspotManager LB deployed (${plan.strategy}, ${ups.size} uplinks)\"")
        }
    }
}