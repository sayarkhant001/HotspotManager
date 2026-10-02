package com.example.utils

import com.example.R

data class DeviceModelInfo(
    val brand: String,
    val modelName: String,
    val deviceType: String,
    val imageResId: Int,
    val isApOrBridge: Boolean
)

data class HardwarePreset(
    val id: String,
    val brand: String,
    val modelName: String,
    val shortName: String,
    val deviceType: String,
    val imageResId: Int,
    val defaultPrefix: String = "C0:A4:76:"
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

    // Predefined hardware catalog for quick selection in Topology & Allowlist
    val HARDWARE_PRESETS: List<HardwarePreset> = listOf(
        // Ruijie Reyee Wi-Fi 6 Gaming & Mesh
        HardwarePreset(
            id = "ew3000gx_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW3000GX PRO",
            shortName = "EW3000GX PRO",
            deviceType = "Wi-Fi 6 Gaming Router AP",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew3000gx",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW3000GX",
            shortName = "EW3000GX",
            deviceType = "Wi-Fi 6 Gaming Router AP",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew3200gx",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW3200GX",
            shortName = "EW3200GX",
            deviceType = "Wi-Fi 6 Mesh Router AP",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1800gx",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1800GX",
            shortName = "EW1800GX",
            deviceType = "Wi-Fi 6 Mesh Router AP",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),

        // Ruijie Reyee Gigabit Multi-Antenna
        HardwarePreset(
            id = "ew1200g_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1200G PRO",
            shortName = "EW1200G PRO",
            deviceType = "Gigabit Router AP",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1300g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1300G",
            shortName = "EW1300G",
            deviceType = "Gigabit Router AP",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1300g_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1300G PRO",
            shortName = "EW1300G PRO",
            deviceType = "Gigabit Router AP",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1200",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1200",
            shortName = "EW1200",
            deviceType = "Home Router AP",
            imageResId = R.drawable.img_ruijie_ew1200,
            defaultPrefix = "C0:A4:76:"
        ),

        // Ruijie Reyee Wireless Bridges
        HardwarePreset(
            id = "est350",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST350 V2",
            shortName = "EST350 V2",
            deviceType = "5GHz 5km Gigabit Bridge",
            imageResId = R.drawable.img_ruijie_est350,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est310",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST310 V2",
            shortName = "EST310 V2",
            deviceType = "5GHz 1km Wireless Bridge",
            imageResId = R.drawable.img_ruijie_est310,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est302",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST302",
            shortName = "EST302",
            deviceType = "2.4GHz Wireless Bridge",
            imageResId = R.drawable.img_ruijie_est310,
            defaultPrefix = "C0:A4:76:"
        ),

        // Ruijie Reyee Ceiling & Outdoor APs
        HardwarePreset(
            id = "rap2200",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2200(E)",
            shortName = "RAP2200(E)",
            deviceType = "AC1300 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap2260",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2260(E)",
            shortName = "RAP2260(E)",
            deviceType = "Wi-Fi 6 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap1200",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP1200(F)",
            shortName = "RAP1200(F)",
            deviceType = "Wall-Plate Gigabit AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap6260",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP6260(H)",
            shortName = "RAP6260(H)",
            deviceType = "Outdoor Omnidirectional AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),

        // TP-Link Archer Routers
        HardwarePreset(
            id = "archer_ax73",
            brand = "TP-Link",
            modelName = "TP-Link Archer AX73",
            shortName = "Archer AX73",
            deviceType = "Wi-Fi 6 Gigabit Router AP",
            imageResId = R.drawable.img_tplink_archer,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "archer_ax53",
            brand = "TP-Link",
            modelName = "TP-Link Archer AX53",
            shortName = "Archer AX53",
            deviceType = "Wi-Fi 6 Gigabit Router AP",
            imageResId = R.drawable.img_tplink_archer,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "archer_ax23",
            brand = "TP-Link",
            modelName = "TP-Link Archer AX23",
            shortName = "Archer AX23",
            deviceType = "Wi-Fi 6 Gigabit Router AP",
            imageResId = R.drawable.img_tplink_archer,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "archer_c80",
            brand = "TP-Link",
            modelName = "TP-Link Archer C80",
            shortName = "Archer C80",
            deviceType = "AC1900 MU-MIMO Router AP",
            imageResId = R.drawable.img_tplink_archer,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "archer_c6",
            brand = "TP-Link",
            modelName = "TP-Link Archer C6",
            shortName = "Archer C6",
            deviceType = "AC1200 Gigabit Router AP",
            imageResId = R.drawable.img_tplink_archer,
            defaultPrefix = "E8:48:B8:"
        ),

        // TP-Link Deco Mesh
        HardwarePreset(
            id = "deco_x20",
            brand = "TP-Link",
            modelName = "TP-Link Deco X20",
            shortName = "Deco X20",
            deviceType = "Wi-Fi 6 Whole Home Mesh",
            imageResId = R.drawable.img_tplink_deco,
            defaultPrefix = "E8:48:B8:"
        ),

        // TP-Link Pharos CPE Bridges
        HardwarePreset(
            id = "cpe210",
            brand = "TP-Link",
            modelName = "TP-Link Pharos CPE210",
            shortName = "CPE210",
            deviceType = "2.4GHz Outdoor Bridge",
            imageResId = R.drawable.img_tplink_cpe,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "cpe510",
            brand = "TP-Link",
            modelName = "TP-Link Pharos CPE510",
            shortName = "CPE510",
            deviceType = "5GHz Outdoor Bridge",
            imageResId = R.drawable.img_tplink_cpe,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "cpe710",
            brand = "TP-Link",
            modelName = "TP-Link Pharos CPE710",
            shortName = "CPE710",
            deviceType = "5GHz 23dBi Outdoor Dish",
            imageResId = R.drawable.img_tplink_cpe,
            defaultPrefix = "E8:48:B8:"
        ),

        // TP-Link Omada APs
        HardwarePreset(
            id = "eap225",
            brand = "TP-Link",
            modelName = "TP-Link Omada EAP225",
            shortName = "EAP225",
            deviceType = "AC1200 Ceiling/Outdoor AP",
            imageResId = R.drawable.img_tplink_eap,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "eap610",
            brand = "TP-Link",
            modelName = "TP-Link Omada EAP610",
            shortName = "EAP610",
            deviceType = "AX1800 Wi-Fi 6 Ceiling AP",
            imageResId = R.drawable.img_tplink_eap,
            defaultPrefix = "E8:48:B8:"
        ),
        HardwarePreset(
            id = "eap660",
            brand = "TP-Link",
            modelName = "TP-Link Omada EAP660 HD",
            shortName = "EAP660 HD",
            deviceType = "AX3600 Wi-Fi 6 Ceiling AP",
            imageResId = R.drawable.img_tplink_eap,
            defaultPrefix = "E8:48:B8:"
        )
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
        val rawCombined = "$name $hostName $comment".uppercase()
        // Normalized alphanumeric string for flexible regex-free keyword matching
        val norm = rawCombined.replace(Regex("[^A-Z0-9]"), "")
        val mac = macAddress.trim().uppercase()
        val isRuijie = isRuijieMac(mac) || norm.contains("RUIJIE") || norm.contains("REYEE")
        val isTpLink = isTpLinkMac(mac) || norm.contains("TPLINK")

        // 1. Ruijie Reyee Wireless Bridges (EST350, EST310, EST302)
        if (norm.contains("EST350") || norm.contains("EST310") || norm.contains("EST302") || (isRuijie && norm.contains("EST"))) {
            val is350 = norm.contains("350")
            val is302 = norm.contains("302")
            val model = when {
                is350 -> "Reyee RG-EST350 V2"
                is302 -> "Reyee RG-EST302"
                else -> "Reyee RG-EST310 V2"
            }
            val img = if (is350) R.drawable.img_ruijie_est350 else R.drawable.img_ruijie_est310
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Wireless Bridge",
                imageResId = img,
                isApOrBridge = true
            )
        }

        // 2. Ruijie Reyee Wi-Fi 6 Gaming Routers (EW3000GX, EW3000GX PRO, EW3200GX, EW1800GX)
        if (norm.contains("3000GX") || norm.contains("EW3000") || norm.contains("3200GX") || norm.contains("1800GX") || (isRuijie && norm.contains("3000"))) {
            val isPro = norm.contains("3000GXPRO") || (norm.contains("3000GX") && norm.contains("PRO")) || norm.contains("PRO")
            val model = when {
                isPro -> "Reyee RG-EW3000GX PRO"
                norm.contains("3200GX") || norm.contains("3200") -> "Reyee RG-EW3200GX"
                norm.contains("1800GX") || norm.contains("1800") -> "Reyee RG-EW1800GX"
                else -> "Reyee RG-EW3000GX"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Wi-Fi 6 Gaming Router AP",
                imageResId = R.drawable.img_ruijie_ew3000gx,
                isApOrBridge = true
            )
        }

        // 3. Ruijie Reyee Gigabit Multi-Antenna Routers (EW1200G PRO, EW1300G, EW1300G PRO)
        if (norm.contains("1200G") || norm.contains("1200GPRO") || norm.contains("1300G") || norm.contains("1300") || norm.contains("EW1300")) {
            val model = when {
                norm.contains("1300GPRO") || (norm.contains("1300") && norm.contains("PRO")) -> "Reyee RG-EW1300G PRO"
                norm.contains("1300G") || norm.contains("1300") -> "Reyee RG-EW1300G"
                norm.contains("1200GPRO") || (norm.contains("1200G") && norm.contains("PRO")) -> "Reyee RG-EW1200G PRO"
                else -> "Reyee RG-EW1200G PRO"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Gigabit Router AP",
                imageResId = R.drawable.img_ruijie_ew1200g_pro,
                isApOrBridge = true
            )
        }

        // 4. Ruijie Reyee Standard EW Routers (EW1200, EW-1200, EW series)
        if (norm.contains("EW1200") || norm.contains("RGEW") || (isRuijie && (norm.contains("EW") || norm.contains("ROUTER")))) {
            val isPro = norm.contains("PRO")
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = if (isPro) "Reyee RG-EW1200 PRO" else "Reyee RG-EW1200",
                deviceType = "Home Router AP",
                imageResId = R.drawable.img_ruijie_ew1200,
                isApOrBridge = true
            )
        }

        // 5. Ruijie / Reyee Ceiling & Wall APs (RAP2200, RAP2260, RAP1200, RAP6260, RAP6262)
        if (norm.contains("RAP") || (isRuijie && (norm.contains("AP") || norm.contains("CEILING")))) {
            val model = when {
                norm.contains("2260") -> "Reyee RG-RAP2260(E)"
                norm.contains("2200") -> "Reyee RG-RAP2200(E)"
                norm.contains("1200") -> "Reyee RG-RAP1200(F)"
                norm.contains("6262") -> "Reyee RG-RAP6262"
                norm.contains("6260") -> "Reyee RG-RAP6260(H)"
                else -> "Reyee RG-RAP2200(E)"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Ceiling Access Point",
                imageResId = R.drawable.img_ruijie_rap,
                isApOrBridge = true
            )
        }

        // 6. TP-Link Deco Mesh System
        if (norm.contains("DECO")) {
            val model = when {
                norm.contains("X60") -> "TP-Link Deco X60"
                norm.contains("X50") -> "TP-Link Deco X50"
                norm.contains("X20") -> "TP-Link Deco X20"
                norm.contains("M4") -> "TP-Link Deco M4"
                norm.contains("M5") -> "TP-Link Deco M5"
                else -> "TP-Link Deco Mesh"
            }
            return DeviceModelInfo(
                brand = "TP-Link",
                modelName = model,
                deviceType = "Whole Home Mesh",
                imageResId = R.drawable.img_tplink_deco,
                isApOrBridge = true
            )
        }

        // 7. TP-Link Pharos CPE Wireless Bridge (CPE210, CPE220, CPE510, CPE610, CPE710)
        if (norm.contains("CPE") || norm.contains("PHAROS")) {
            val model = when {
                norm.contains("710") -> "TP-Link Pharos CPE710"
                norm.contains("610") -> "TP-Link Pharos CPE610"
                norm.contains("510") -> "TP-Link Pharos CPE510"
                norm.contains("220") -> "TP-Link Pharos CPE220"
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

        // 8. TP-Link Omada Ceiling & Outdoor AP (EAP225, EAP245, EAP610, EAP620, EAP650, EAP660)
        if (norm.contains("EAP") || norm.contains("OMADA")) {
            val model = when {
                norm.contains("660") -> "TP-Link Omada EAP660 HD"
                norm.contains("650") -> "TP-Link Omada EAP650"
                norm.contains("620") -> "TP-Link Omada EAP620 HD"
                norm.contains("610") -> "TP-Link Omada EAP610"
                norm.contains("245") -> "TP-Link Omada EAP245"
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

        // 9. TP-Link Archer / TL-WR Home Routers
        if (norm.contains("ARCHER") || norm.contains("TLWR") || norm.contains("WR840") || norm.contains("WR841") || norm.contains("AX73") || norm.contains("AX53") || norm.contains("AX23") || norm.contains("AX12") || norm.contains("AX10") || norm.contains("C80") || isTpLink) {
            val model = when {
                norm.contains("AX73") || norm.contains("AX72") -> "TP-Link Archer AX73"
                norm.contains("AX53") || norm.contains("AX50") -> "TP-Link Archer AX53"
                norm.contains("AX23") || norm.contains("AX20") -> "TP-Link Archer AX23"
                norm.contains("AX12") -> "TP-Link Archer AX12"
                norm.contains("AX10") -> "TP-Link Archer AX10"
                norm.contains("C80") -> "TP-Link Archer C80"
                norm.contains("C50") || norm.contains("C20") -> "TP-Link Archer C50"
                norm.contains("WR840") || norm.contains("WR841") -> "TP-Link TL-WR840N"
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

        // 10. Fallback for Ruijie MAC
        if (isRuijie) {
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = "Reyee RG-EW1200",
                deviceType = "Access Point",
                imageResId = R.drawable.img_ruijie_ew1200,
                isApOrBridge = true
            )
        }

        // 11. Generic AP detected via comment / identity
        if (norm.contains("AP") || norm.contains("ACCESSPOINT") || norm.contains("BRIDGE")) {
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
