package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.models.AccessPointDevice

data class AirMetroBridgePair(
    val pairId: String,
    val pairTitle: String,
    val baseDevice: AccessPointDevice,
    val cpeDevice: AccessPointDevice?,
    val isLinked: Boolean = (cpeDevice != null && baseDevice.isOnline && cpeDevice.isOnline),
    val totalRxBps: Long = baseDevice.currentRxBps + (cpeDevice?.currentRxBps ?: 0L),
    val totalTxBps: Long = baseDevice.currentTxBps + (cpeDevice?.currentTxBps ?: 0L),
    val locationNotes: String = "",
    val linkHealthPercent: Int = if (cpeDevice != null && baseDevice.isOnline && cpeDevice.isOnline) 98 else if (baseDevice.isOnline || (cpeDevice?.isOnline == true)) 50 else 0,
    val isAutoDetected: Boolean = false,
    val matchConfidence: Int = 100,
    val detectionReason: String = ""
)

object AirMetroPairManager {
    private const val PREFS_NAME = "airmetro_pairs_prefs"
    private const val KEY_PAIR_MAP = "pair_map_"
    private const val KEY_PAIR_TITLE = "pair_title_"

    fun buildPairs(
        context: Context,
        accessPoints: List<AccessPointDevice>
    ): List<AirMetroBridgePair> {
        val bridgeDevices = accessPoints.filter {
            DeviceModelDetector.isAirMetroOrBridge(it.name, it.name, it.model, it.macAddress) ||
            !DeviceModelDetector.isClientDevice(it.name, it.name, it.model, it.macAddress)
        }
        if (bridgeDevices.isEmpty()) return emptyList()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val usedMacs = mutableSetOf<String>()
        val pairs = mutableListOf<AirMetroBridgePair>()

        // 1. Process explicit user pairings from SharedPreferences
        for (device in bridgeDevices) {
            val dMac = device.macAddress.uppercase()
            if (dMac in usedMacs) continue

            val partnerMac = prefs.getString("$KEY_PAIR_MAP$dMac", null)?.uppercase()
            if (!partnerMac.isNullOrBlank() && partnerMac != dMac) {
                val partnerDevice = accessPoints.firstOrNull { it.macAddress.uppercase() == partnerMac }
                if (partnerDevice != null && partnerMac !in usedMacs) {
                    val pairId = "pair_${minOf(dMac, partnerMac)}"
                    val customTitle = prefs.getString("$KEY_PAIR_TITLE$pairId", null)
                        ?: "${device.name} ↔ ${partnerDevice.name}"

                    val isDeviceBase = isBaseStation(device)
                    val base = if (isDeviceBase) device else partnerDevice
                    val cpe = if (isDeviceBase) partnerDevice else device

                    pairs.add(
                        AirMetroBridgePair(
                            pairId = pairId,
                            pairTitle = customTitle,
                            baseDevice = base,
                            cpeDevice = cpe,
                            isAutoDetected = false,
                            matchConfidence = 100,
                            detectionReason = "User Paired & Verified"
                        )
                    )
                    usedMacs.add(dMac)
                    usedMacs.add(partnerMac)
                }
            }
        }

        // 2. Intelligent Enhanced Auto-Pairing by Multi-Factor Scoring
        val remaining = bridgeDevices.filter { it.macAddress.uppercase() !in usedMacs }
        if (remaining.isNotEmpty()) {
            data class PairCandidate(
                val devA: AccessPointDevice,
                val devB: AccessPointDevice,
                val score: Int,
                val confidence: Int,
                val reason: String
            )

            val candidateScores = mutableListOf<PairCandidate>()

            for (i in 0 until remaining.size) {
                for (j in i + 1 until remaining.size) {
                    val a = remaining[i]
                    val b = remaining[j]
                    val (score, confidence, reason) = evaluatePairing(a, b)
                    if (score >= 20) {
                        candidateScores.add(PairCandidate(a, b, score, confidence, reason))
                    }
                }
            }

            // Sort by highest score / confidence first
            candidateScores.sortByDescending { it.score }

            for (candidate in candidateScores) {
                val macA = candidate.devA.macAddress.uppercase()
                val macB = candidate.devB.macAddress.uppercase()
                if (macA in usedMacs || macB in usedMacs) continue

                val isABase = isBaseStation(candidate.devA)
                val base = if (isABase) candidate.devA else candidate.devB
                val cpe = if (isABase) candidate.devB else candidate.devA

                val pairId = "auto_${minOf(macA, macB)}"
                val title = "${base.name} ↔ ${cpe.name}"

                pairs.add(
                    AirMetroBridgePair(
                        pairId = pairId,
                        pairTitle = title,
                        baseDevice = base,
                        cpeDevice = cpe,
                        isAutoDetected = true,
                        matchConfidence = candidate.confidence,
                        detectionReason = candidate.reason
                    )
                )
                usedMacs.add(macA)
                usedMacs.add(macB)
            }
        }

        // 3. Any remaining single bridge devices
        val leftovers = bridgeDevices.filter { it.macAddress.uppercase() !in usedMacs }
        leftovers.forEach { device ->
            val pairId = "single_${device.macAddress.uppercase()}"
            val role = if (isBaseStation(device)) "Base Station" else if (isCpeStation(device)) "Bridge CPE" else "Wireless Bridge"
            pairs.add(
                AirMetroBridgePair(
                    pairId = pairId,
                    pairTitle = "${device.name} ($role)",
                    baseDevice = device,
                    cpeDevice = null,
                    isAutoDetected = true,
                    matchConfidence = 50,
                    detectionReason = "Awaiting Partner Bridge"
                )
            )
        }

        return pairs
    }

    private data class PairEvaluation(val score: Int, val confidence: Int, val reason: String)

    private fun evaluatePairing(a: AccessPointDevice, b: AccessPointDevice): PairEvaluation {
        var score = 0
        var confidence = 50
        val reasons = mutableListOf<String>()

        val macA = a.macAddress.uppercase()
        val macB = b.macAddress.uppercase()

        // 1. Factory MAC proximity (pre-paired factory kits differ by 1..4 hex units)
        val macDelta = calculateMacDelta(macA, macB)
        if (macDelta in 1..2) {
            score += 65
            confidence = maxOf(confidence, 98)
            reasons.add("Factory Kit Sequence (MAC Δ$macDelta)")
        } else if (macDelta in 3..4) {
            score += 50
            confidence = maxOf(confidence, 92)
            reasons.add("Sequential Factory MACs (Δ$macDelta)")
        } else if (macDelta in 5..16) {
            score += 30
            confidence = maxOf(confidence, 82)
            reasons.add("Same Production Batch MACs")
        } else if (macA.take(8) == macB.take(8)) {
            score += 15
            confidence = maxOf(confidence, 70)
            reasons.add("Matching Ruijie OUI")
        }

        // 2. Base vs CPE role complementarity
        val aBase = isBaseStation(a)
        val bBase = isBaseStation(b)
        val aCpe = isCpeStation(a)
        val bCpe = isCpeStation(b)

        if ((aBase && bCpe) || (bBase && aCpe)) {
            score += 45
            confidence = maxOf(confidence, 95)
            reasons.add("Base ↔ CPE Roles")
        } else if (aBase != bBase) {
            score += 35
            confidence = maxOf(confidence, 85)
            reasons.add("Transmitter ↔ Receiver Roles")
        }

        // 3. Ruijie AirMetro official pair architecture (550G-B Base + 460G/460F CPE)
        val modelA = a.model.uppercase()
        val modelB = b.model.uppercase()
        val is550A = modelA.contains("550G") || a.name.uppercase().contains("550G")
        val is550B = modelB.contains("550G") || b.name.uppercase().contains("550G")
        val is460A = modelA.contains("460") || a.name.uppercase().contains("460")
        val is460B = modelB.contains("460") || b.name.uppercase().contains("460")

        val isEw3000A = modelA.contains("3000") || modelA.contains("EW3000") || a.name.uppercase().contains("3000GX") || a.name.uppercase().contains("EW3000")
        val isEw3000B = modelB.contains("3000") || modelB.contains("EW3000") || b.name.uppercase().contains("3000GX") || b.name.uppercase().contains("EW3000")

        if ((is550A && is460B) || (is550B && is460A)) {
            score += 45
            confidence = maxOf(confidence, 99)
            reasons.add("AirMetro 550G-B + 460G PtMP Pair")
        } else if ((is460A && isEw3000B) || (is460B && isEw3000A)) {
            score += 55
            confidence = maxOf(confidence, 98)
            reasons.add("AirMetro 460G ↔ EW 3000GX PtP AP Link")
        } else if (is460A && is460B) {
            score += 50
            confidence = maxOf(confidence, 96)
            reasons.add("AirMetro 460G ↔ 460G PtP Backbone Link")
        } else if (modelA.contains("AIRMETRO") && modelB.contains("AIRMETRO")) {
            score += 30
            reasons.add("AirMetro Family")
        } else if (modelA.contains("EST350") && modelB.contains("EST350")) {
            score += 35
            reasons.add("EST350 V2 PtP Kit")
        } else if (modelA.contains("EST310") && modelB.contains("EST310")) {
            score += 35
            reasons.add("EST310 V2 PtP Kit")
        } else if (modelA.contains("EST") && modelB.contains("EST")) {
            score += 25
            reasons.add("EST Bridge Series")
        }

        // 4. Name prefix & token similarity (e.g. "SiteA-Tower-Base" and "SiteA-CPE")
        val nameA = a.name.trim().lowercase()
        val nameB = b.name.trim().lowercase()
        val cleanA = nameA.replace(Regex("(?i)(base|station|master|ap|main|tower|cpe|slave|remote|client|sub|receiver|tx|rx|ptp|ptmp|[0-9_-])"), "").trim()
        val cleanB = nameB.replace(Regex("(?i)(base|station|master|ap|main|tower|cpe|slave|remote|client|sub|receiver|tx|rx|ptp|ptmp|[0-9_-])"), "").trim()

        if (cleanA.length >= 3 && cleanB.length >= 3 && (cleanA == cleanB || cleanA.contains(cleanB) || cleanB.contains(cleanA))) {
            score += 40
            confidence = maxOf(confidence, 90)
            reasons.add("Shared Name Token ('$cleanA')")
        }

        // 5. Interface correlation (connected to same router bridge or interface)
        if (a.interfaceName.isNotBlank() && a.interfaceName == b.interfaceName) {
            score += 15
            reasons.add("Same Router Interface (${a.interfaceName})")
        }

        // 6. IP Subnet proximity (e.g. 10.10.10.20 and 10.10.10.21, or default 10.44.77.x)
        if (a.ipAddress.isNotBlank() && b.ipAddress.isNotBlank()) {
            val subnetA = a.ipAddress.substringBeforeLast(".")
            val subnetB = b.ipAddress.substringBeforeLast(".")
            if (subnetA == subnetB) {
                score += 10
                val hostA = a.ipAddress.substringAfterLast(".").toIntOrNull()
                val hostB = b.ipAddress.substringAfterLast(".").toIntOrNull()
                if (hostA != null && hostB != null && Math.abs(hostA - hostB) <= 4) {
                    score += 20
                    reasons.add("Adjacent Subnet IPs (.${hostA} & .${hostB})")
                }
            }
        }

        val finalConfidence = confidence.coerceIn(50, 99)
        val finalReason = if (reasons.isNotEmpty()) reasons.joinToString(" • ") else "Heuristic Matching"
        return PairEvaluation(score, finalConfidence, finalReason)
    }

    private fun calculateMacDelta(macA: String, macB: String): Long {
        return try {
            val cleanA = macA.replace(":", "").replace("-", "")
            val cleanB = macB.replace(":", "").replace("-", "")
            if (cleanA.length != 12 || cleanB.length != 12) return Long.MAX_VALUE
            val subA = cleanA.substring(6).toLong(16)
            val subB = cleanB.substring(6).toLong(16)
            Math.abs(subA - subB)
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    fun isBaseStation(dev: AccessPointDevice): Boolean {
        val n = dev.name.lowercase()
        val m = dev.model.lowercase()
        return m.contains("550g") || m.contains("550g-b") || m.contains("460g") || m.contains("airmetro") || m.contains("base") ||
               n.contains("base") || n.contains("master") || n.contains("tower") || n.contains("main") ||
               n.contains("transmitter") || n.contains("tx") || n.endsWith("-b") || n.contains("point a") ||
               n.contains("ptp-a") || n.contains("ptmp-master") || n.contains("ap-mode")
    }

    fun isCpeStation(dev: AccessPointDevice): Boolean {
        val n = dev.name.lowercase()
        val m = dev.model.lowercase()
        return m.contains("3000gx") || m.contains("ew3000") || n.contains("3000gx") ||
               m.contains("460f") || m.contains("cpe") || m.contains("slave") ||
               n.contains("cpe") || n.contains("slave") || n.contains("remote") || n.contains("client") ||
               n.contains("sub") || n.contains("station") || n.contains("receiver") || n.contains("rx") ||
               n.endsWith("-c") || n.contains("point b") || n.contains("ptp-b") || n.contains("station-mode")
    }

    fun autoPairAll(context: Context, accessPoints: List<AccessPointDevice>): Int {
        val pairs = buildPairs(context, accessPoints)
        var autoPairedCount = 0
        pairs.forEach { pair ->
            if (pair.isAutoDetected && pair.cpeDevice != null && pair.matchConfidence >= 75) {
                savePairing(context, pair.baseDevice.macAddress, pair.cpeDevice.macAddress, pair.pairTitle)
                autoPairedCount++
            }
        }
        return autoPairedCount
    }

    fun swapBaseAndCpe(context: Context, macA: String, macB: String, customTitle: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val uA = macA.uppercase()
        val uB = macB.uppercase()
        // Save pairing with swapped base/cpe orientation
        prefs.putString("$KEY_PAIR_MAP$uB", uA)
        prefs.putString("$KEY_PAIR_MAP$uA", uB)
        val pairId = "pair_${minOf(uA, uB)}"
        if (!customTitle.isNullOrBlank()) {
            prefs.putString("$KEY_PAIR_TITLE$pairId", customTitle)
        }
        prefs.apply()
    }

    fun getPairedMac(context: Context, mac: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_PAIR_MAP${mac.uppercase()}", null)
    }

    fun savePairing(context: Context, macA: String, macB: String, customTitle: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val uA = macA.uppercase()
        val uB = macB.uppercase()
        prefs.putString("$KEY_PAIR_MAP$uA", uB)
        prefs.putString("$KEY_PAIR_MAP$uB", uA)
        val pairId = "pair_${minOf(uA, uB)}"
        if (!customTitle.isNullOrBlank()) {
            prefs.putString("$KEY_PAIR_TITLE$pairId", customTitle)
        }
        prefs.apply()
    }

    fun removePairing(context: Context, macA: String, macB: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val uA = macA.uppercase()
        val uB = macB.uppercase()
        prefs.remove("$KEY_PAIR_MAP$uA")
        prefs.remove("$KEY_PAIR_MAP$uB")
        val pairId = "pair_${minOf(uA, uB)}"
        prefs.remove("$KEY_PAIR_TITLE$pairId")
        prefs.apply()
    }
}

