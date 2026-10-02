package com.example.utils

import com.example.R

data class DeviceModelInfo(
    val brand: String,
    val modelName: String,
    val deviceType: String,
    val imageResId: Int,
    val isApOrBridge: Boolean
)

object DeviceModelDetector {

    private val RUIJIE_OUIS = listOf(
        "C0:A4:76", "10:5F:02", "00:D0:F8", "70:70:8B", "74:05:A5",
        "BC:B2:D6", "24:72:60", "94:07:9A", "F4:CB:52", "80:05:88",
        "B8:F8:83", "14:75:90", "00:1A:A9", "54:FA:3E", "48:57:02",
        "38:4F:F0", "4C:49:68", "28:2C:B2", "E0:97:96"
    )

    private val TPLINK_OUIS = listOf(
        "E8:48:B8", "50:D4:F7", "60:32:B1", "D8:0D:17", "00:0A:EB",
        "00:1D:0F", "00:27:19", "14:CC:20", "1C:3B:F3", "30:DE:4B",
        "54:AF:97", "98:DA:C4", "AC:84:C6", "B0:4E:26", "C0:25:E9",
        "C4:6E:1F", "CC:32:E5", "D4:6E:0E", "E4:C3:2A", "F4:EC:38",
        "F4:F2:6D", "0C:80:63", "70:4F:57", "84:D8:1B", "A4:2B:B0",
        "B4:B0:24"
    )

    fun isRuijieMac(mac: String): Boolean {
        val clean = mac.trim().uppercase()
        return RUIJIE_OUIS.any { clean.startsWith(it) }
    }

    fun isTpLinkMac(mac: String): Boolean {
        val clean = mac.trim().uppercase()
        return TPLINK_OUIS.any { clean.startsWith(it) }
    }

    fun detectRouterGateway(boardName: String): DeviceModelInfo {
        val upper = boardName.uppercase()
        return when {
            upper.contains("4011") || upper.contains("CCR") || upper.contains("1100") -> {
                DeviceModelInfo(
                    brand = "MikroTik",
                    modelName = if (boardName.isNotBlank()) boardName else "RB4011iGS+",
                    deviceType = "Gateway Router",
                    imageResId = R.drawable.img_mikrotik_rb4011,
                    isApOrBridge = false
                )
            }
            else -> {
                DeviceModelInfo(
                    brand = "MikroTik",
                    modelName = if (boardName.isNotBlank()) boardName else "L009UiGS-2HaxD",
                    deviceType = "Gateway Router",
                    imageResId = R.drawable.img_mikrotik_l009,
                    isApOrBridge = false
                )
            }
        }
    }

    fun detectApOrClient(
        name: String,
        hostName: String = "",
        comment: String = "",
        macAddress: String = ""
    ): DeviceModelInfo {
        val combined = "$name $hostName $comment".uppercase()
        val mac = macAddress.trim().uppercase()
        val isRuijie = isRuijieMac(mac) || combined.contains("RUIJIE") || combined.contains("REYEE")
        val isTpLink = isTpLinkMac(mac) || combined.contains("TP-LINK") || combined.contains("TPLINK")

        // 1. Ruijie Reyee EST Bridge (EST310, EST350)
        if (combined.contains("EST310") || combined.contains("EST350") || combined.contains("EST-") || (isRuijie && combined.contains("EST"))) {
            val is350 = combined.contains("350")
            val model = if (is350) "Reyee RG-EST350" else "Reyee RG-EST310"
            val img = if (is350) R.drawable.img_ruijie_est350 else R.drawable.img_ruijie_est310
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Wireless Bridge",
                imageResId = img,
                isApOrBridge = true
            )
        }

        // 2. Ruijie Reyee EW Router AP (EW1200, EW1800, EW3200)
        if (combined.contains("EW1200") || combined.contains("EW-") || combined.contains("EW1800") || combined.contains("EW3200") || (isRuijie && combined.contains("EW"))) {
            val model = when {
                combined.contains("3200") -> "Reyee RG-EW3200GX"
                combined.contains("1800") -> "Reyee RG-EW1800GX"
                else -> "Reyee RG-EW1200"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Access Point",
                imageResId = R.drawable.img_ruijie_ew1200,
                isApOrBridge = true
            )
        }

        // 3. Other Ruijie / Reyee APs (RAP2200, RAP1200, RAP6260)
        if (combined.contains("RAP") || combined.contains("REYEE") || isRuijie) {
            val isBridge = combined.contains("BRIDGE") || combined.contains("DISH")
            val img = if (isBridge) R.drawable.img_ruijie_est310 else R.drawable.img_ruijie_ew1200
            val type = if (isBridge) "Wireless Bridge" else "Access Point"
            val model = when {
                combined.contains("RAP2200") -> "Reyee RG-RAP2200"
                combined.contains("RAP1200") -> "Reyee RG-RAP1200"
                combined.contains("RAP6260") -> "Reyee RG-RAP6260"
                else -> "Ruijie Reyee AP"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = type,
                imageResId = img,
                isApOrBridge = true
            )
        }

        // 4. TP-Link Pharos CPE Wireless Bridge (CPE210, CPE510, CPE610, CPE710)
        if (combined.contains("CPE210") || combined.contains("CPE510") || combined.contains("CPE610") || combined.contains("CPE710") || combined.contains("CPE-") || combined.contains("PHAROS")) {
            val model = when {
                combined.contains("510") -> "TP-Link Pharos CPE510"
                combined.contains("610") -> "TP-Link Pharos CPE610"
                combined.contains("710") -> "TP-Link Pharos CPE710"
                else -> "TP-Link Pharos CPE210"
            }
            return DeviceModelInfo(
                brand = "TP-Link",
                modelName = model,
                deviceType = "Wireless Bridge",
                imageResId = R.drawable.img_tplink_cpe,
                isApOrBridge = true
            )
        }

        // 5. TP-Link Omada Ceiling AP (EAP225, EAP245, EAP610, EAP650)
        if (combined.contains("EAP225") || combined.contains("EAP245") || combined.contains("EAP610") || combined.contains("EAP650") || combined.contains("EAP-") || combined.contains("OMADA")) {
            val model = when {
                combined.contains("245") -> "TP-Link Omada EAP245"
                combined.contains("610") -> "TP-Link Omada EAP610"
                combined.contains("650") -> "TP-Link Omada EAP650"
                else -> "TP-Link Omada EAP225"
            }
            return DeviceModelInfo(
                brand = "TP-Link",
                modelName = model,
                deviceType = "Access Point",
                imageResId = R.drawable.img_tplink_eap,
                isApOrBridge = true
            )
        }

        // 6. TP-Link Archer / Wi-Fi Router as AP (Archer C6, C80, AX12, TL-WR)
        if (combined.contains("ARCHER") || combined.contains("TL-WR") || combined.contains("WR840") || combined.contains("WR841") || combined.contains("DECO") || isTpLink) {
            val model = when {
                combined.contains("C80") -> "TP-Link Archer C80"
                combined.contains("AX12") -> "TP-Link Archer AX12"
                combined.contains("AX10") -> "TP-Link Archer AX10"
                combined.contains("DECO") -> "TP-Link Deco Mesh"
                else -> "TP-Link Archer C6"
            }
            return DeviceModelInfo(
                brand = "TP-Link",
                modelName = model,
                deviceType = "Home Router AP",
                imageResId = R.drawable.img_tplink_archer,
                isApOrBridge = true
            )
        }

        // 7. Generic AP detected via comment / identity
        if (combined.contains("AP") || combined.contains("ACCESS POINT") || combined.contains("BRIDGE")) {
            return DeviceModelInfo(
                brand = "Access Point",
                modelName = name.ifBlank { "Network AP" },
                deviceType = "Access Point",
                imageResId = R.drawable.img_ruijie_ew1200,
                isApOrBridge = true
            )
        }

        // Standard client (phone/laptop/etc.)
        return DeviceModelInfo(
            brand = "Client",
            modelName = hostName.ifBlank { name },
            deviceType = "Connected Client",
            imageResId = 0,
            isApOrBridge = false
        )
    }
}
