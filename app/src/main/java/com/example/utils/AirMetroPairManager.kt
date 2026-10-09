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
    val locationNotes: String = ""
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
            DeviceModelDetector.isAirMetroOrBridge(it.name, it.name, it.model, it.macAddress)
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
                val partnerDevice = bridgeDevices.firstOrNull { it.macAddress.uppercase() == partnerMac }
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
                            cpeDevice = cpe
                        )
                    )
                    usedMacs.add(dMac)
                    usedMacs.add(partnerMac)
                }
            }
        }

        // 2. Auto-pair remaining bridge devices by name matching or Base ↔ CPE grouping
        val remaining = bridgeDevices.filter { it.macAddress.uppercase() !in usedMacs }
        val bases = remaining.filter { isBaseStation(it) }.toMutableList()
        val cpes = remaining.filter { !isBaseStation(it) }.toMutableList()

        while (bases.isNotEmpty() && cpes.isNotEmpty()) {
            val base = bases.removeAt(0)
            val matchedCpe = cpes.firstOrNull { c ->
                val bNorm = base.name.replace(Regex("(?i)(base|station|ap|master)"), "").trim().lowercase()
                val cNorm = c.name.replace(Regex("(?i)(cpe|slave|client|station)"), "").trim().lowercase()
                bNorm.isNotBlank() && cNorm.isNotBlank() && (bNorm == cNorm || cNorm.contains(bNorm) || bNorm.contains(cNorm))
            } ?: cpes.removeAt(0)

            cpes.remove(matchedCpe)
            val pairId = "auto_${base.macAddress}_${matchedCpe.macAddress}"
            val title = "${base.name} ↔ ${matchedCpe.name}"
            pairs.add(
                AirMetroBridgePair(
                    pairId = pairId,
                    pairTitle = title,
                    baseDevice = base,
                    cpeDevice = matchedCpe
                )
            )
            usedMacs.add(base.macAddress.uppercase())
            usedMacs.add(matchedCpe.macAddress.uppercase())
        }

        // 3. Any remaining bridge devices
        val leftovers = remaining.filter { it.macAddress.uppercase() !in usedMacs }
        leftovers.chunked(2).forEach { chunk ->
            if (chunk.size == 2) {
                val pairId = "chunk_${chunk[0].macAddress}_${chunk[1].macAddress}"
                pairs.add(
                    AirMetroBridgePair(
                        pairId = pairId,
                        pairTitle = "${chunk[0].name} ↔ ${chunk[1].name}",
                        baseDevice = chunk[0],
                        cpeDevice = chunk[1]
                    )
                )
            } else {
                val pairId = "single_${chunk[0].macAddress}"
                pairs.add(
                    AirMetroBridgePair(
                        pairId = pairId,
                        pairTitle = "${chunk[0].name} (Single Bridge)",
                        baseDevice = chunk[0],
                        cpeDevice = null
                    )
                )
            }
        }

        return pairs
    }

    fun isBaseStation(dev: AccessPointDevice): Boolean {
        val n = dev.name.lowercase()
        val m = dev.model.lowercase()
        return m.contains("550g") || n.contains("base") || n.contains("master") || n.contains("ap") || m.contains("base")
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
