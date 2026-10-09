package com.example.utils

import com.example.R

data class DeviceModelInfo(
    val brand: String,
    val modelName: String,
    val deviceType: String,
    val imageResId: Int,
    val isApOrBridge: Boolean,
    val modelCode: String = "",
    val displayName: String = modelName
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

    // Phone / Client Vendor OUIs
    private val APPLE_OUIS = listOf(
        "00:1C:B3", "00:25:00", "00:26:BB", "04:0C:CE", "08:66:98", "10:93:E9", "14:20:5E",
        "24:F0:94", "28:6A:BA", "34:08:BC", "38:CA:DA", "40:A6:D9", "44:4C:0C", "48:D7:05",
        "4C:32:75", "50:BC:96", "58:55:CA", "60:F8:1D", "68:96:7B", "70:11:24", "78:7B:8A",
        "80:49:71", "88:66:A5", "90:FD:61", "98:01:A7", "A4:C3:61", "AC:63:BE", "B4:18:D1",
        "BC:54:36", "C0:84:7D", "C8:69:CD", "D0:03:4B", "D8:96:95", "DC:A9:04", "E4:CE:8F",
        "EC:35:86", "F0:18:98", "F8:27:93"
    )

    private val SAMSUNG_OUIS = listOf(
        "00:07:AB", "00:12:47", "00:15:99", "00:1A:8A", "00:21:19", "08:08:C2", "14:49:E0",
        "18:3A:2D", "24:4B:81", "28:9A:4B", "34:14:5F", "38:01:46", "44:91:60", "48:44:F7",
        "50:32:75", "58:C3:8B", "60:AF:6D", "68:EB:AE", "70:2C:1F", "78:4B:87", "84:25:19",
        "90:97:F3", "98:52:B1", "A4:30:7A", "AC:5F:3E", "B4:79:A7", "BC:44:86", "C4:73:1E",
        "CC:07:AB", "D0:B1:28", "E8:50:8B", "F4:7B:5E"
    )

    private val XIAOMI_OUIS = listOf(
        "00:EC:0A", "04:CF:8C", "14:F6:5A", "18:59:36", "28:6C:07", "34:80:B3", "3C:A6:2F",
        "50:64:2B", "58:44:98", "64:CC:2E", "74:23:44", "78:02:F8", "84:F3:EB", "88:C3:97",
        "98:FA:E3", "A4:44:D0", "B0:38:54", "C4:0B:CB", "D4:97:0B", "E4:AA:EC", "F0:B4:29", "F4:60:E2"
    )

    private val TRANSSION_OUIS = listOf(
        "00:0E:64", "08:E8:4F", "24:EC:99", "3C:22:FB", "50:80:C1", "64:A2:00",
        "80:4E:81", "A0:93:47", "C0:7C:D1", "D0:17:6A", "DC:F5:05", "F8:35:DD"
    )

    private val HUAWEI_OUIS = listOf(
        "00:18:82", "00:1E:10", "00:25:9E", "08:19:A6", "10:1B:54", "1C:1D:67", "20:08:89",
        "28:31:52", "38:BC:01", "48:46:FB", "54:89:98", "60:DE:44", "70:72:3C", "88:53:D4",
        "9C:28:40", "AC:85:3D", "BC:25:E0", "CC:96:A0", "E0:24:7F", "F4:55:9C"
    )

    private val VIVO_OUIS = listOf(
        "00:61:71", "08:D4:6A", "14:7D:C5", "38:91:D5", "40:4E:36", "4C:63:EB",
        "58:D9:C3", "70:8A:09", "78:D6:DC", "A8:54:B2", "B0:D5:9D", "C8:7B:5B", "E8:8D:28"
    )

    private val OPPO_OUIS = listOf(
        "04:79:70", "08:3A:88", "1C:77:F6", "28:6D:97", "3C:7A:8A", "4C:82:CF",
        "68:3E:34", "78:11:DC", "84:DB:AC", "94:65:2D", "A0:86:C6", "B8:37:65", "D4:F5:13", "EC:3D:FD"
    )

    private val RUIJIE_OUIS = listOf(
        "C0:A4:76", "10:5F:02", "00:D0:F8", "70:70:8B", "74:05:A5",
        "BC:B2:D6", "24:72:60", "94:07:9A", "F4:CB:52", "80:05:88",
        "B8:F8:83", "14:75:90", "00:1A:A9", "54:FA:3E", "48:57:02",
        "38:4F:F0", "4C:49:68", "28:2C:B2", "E0:97:96", "E0:5D:54"
    )

    private val TPLINK_OUIS = listOf(
        "E8:48:B8", "50:D4:F7", "60:32:B1", "D8:0D:17", "00:0A:EB",
        "00:1D:0F", "00:27:19", "14:CC:20", "1C:3B:F3", "30:DE:4B",
        "54:AF:97", "98:DA:C4", "AC:84:C6", "B0:4E:26", "C0:25:E9",
        "C4:6E:1F", "CC:32:E5", "D4:6E:0E", "E4:C3:2A", "F4:EC:38",
        "F4:F2:6D", "0C:80:63", "70:4F:57", "84:D8:1B", "A4:2B:B0",
        "B4:B0:24", "8C:90:2D"
    )

    // Predefined hardware catalog for quick selection in Topology & Allowlist
    val HARDWARE_PRESETS: List<HardwarePreset> = listOf(
        // --- 1. RG-EW Series: Wi-Fi 7, Wi-Fi 6 Gaming & Mesh ---
        HardwarePreset(
            id = "ew7200be_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW7200BE PRO",
            shortName = "EW7200BE PRO",
            deviceType = "Wi-Fi 7 BE7200 Gaming Router",
            imageResId = R.drawable.img_ruijie_ew7200be,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew6000gx",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW6000GX",
            shortName = "EW6000GX",
            deviceType = "6000M Wi-Fi 6 Mesh 2.5G Router",
            imageResId = R.drawable.img_ruijie_ew7200be,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew3200gx_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW3200GX PRO",
            shortName = "EW3200GX PRO",
            deviceType = "3200M Wi-Fi 6 Gigabit Mesh Router",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew3000gx_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW3000GX PRO",
            shortName = "EW3000GX PRO",
            deviceType = "3000M Wi-Fi 6 Gaming Router AP",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew3000gx",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW3000GX",
            shortName = "EW3000GX",
            deviceType = "3000M Wi-Fi 6 Dual-WAN Router",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "4C:49:68:"
        ),
        HardwarePreset(
            id = "ew1800gx_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1800GX PRO",
            shortName = "EW1800GX PRO",
            deviceType = "1800M Wi-Fi 6 Gigabit Mesh Router",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1300g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1300G",
            shortName = "EW1300G",
            deviceType = "1300M Dual-band Gigabit Router",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1200g_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1200G PRO",
            shortName = "EW1200G PRO",
            deviceType = "1300M Dual-band Gigabit Router",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1200",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1200",
            shortName = "EW1200",
            deviceType = "1200M Dual-band Home Router",
            imageResId = R.drawable.img_ruijie_ew1200,
            defaultPrefix = "4C:49:68:"
        ),
        HardwarePreset(
            id = "ew300_pro",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW300 PRO",
            shortName = "EW300 PRO",
            deviceType = "300Mbps Wireless Smart Router",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew300t",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW300T",
            shortName = "EW300T",
            deviceType = "N300 Wireless 4G LTE Router",
            imageResId = R.drawable.img_ruijie_ew1200g_pro,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "m32_mesh",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-M32",
            shortName = "RG-M32",
            deviceType = "3200M Wi-Fi 6 Mesh Unit",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "m18_mesh",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-M18",
            shortName = "RG-M18",
            deviceType = "1800M Wi-Fi 6 Mesh Unit",
            imageResId = R.drawable.img_ruijie_ew3000gx,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "ew1200r",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EW1200R",
            shortName = "EW1200R",
            deviceType = "1200M Dual-band Mesh Extender",
            imageResId = R.drawable.img_ruijie_ew1200,
            defaultPrefix = "4C:49:68:"
        ),

        // --- 2. RAP Series: Ceiling-Mount APs ---
        HardwarePreset(
            id = "rap73hd",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP73HD",
            shortName = "RAP73HD",
            deviceType = "Wi-Fi 7 Tri-Radio BE19000 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap72",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP72",
            shortName = "RAP72",
            deviceType = "Wi-Fi 7 BE3600 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap2260h",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2260(H)",
            shortName = "RAP2260(H)",
            deviceType = "Wi-Fi 6 AX6000 Multi-G Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap2260e",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2260(E)",
            shortName = "RAP2260(E)",
            deviceType = "Wi-Fi 6 AX3200 Multi-G Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap2260",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2260",
            shortName = "RAP2260",
            deviceType = "Wi-Fi 6 AX3000 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap62",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP62",
            shortName = "RAP62",
            deviceType = "Wi-Fi 6 AX1800 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap2260g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2260(G)",
            shortName = "RAP2260(G)",
            deviceType = "Wi-Fi 6 AX1800 Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap2200e",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP2200(E)",
            shortName = "RAP2200(E)",
            deviceType = "Wi-Fi 5 1267Mbps Ceiling AP",
            imageResId = R.drawable.img_ruijie_rap,
            defaultPrefix = "E0:5D:54:"
        ),

        // --- 2B. RAP Series: Wall-Plate APs ---
        HardwarePreset(
            id = "rap1261",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP1261",
            shortName = "RAP1261",
            deviceType = "Wi-Fi 6 AX3000 Wall Plate AP",
            imageResId = R.drawable.img_ruijie_rap_wall,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap1260",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP1260",
            shortName = "RAP1260",
            deviceType = "Wi-Fi 6 AX3000 Wall Plate AP",
            imageResId = R.drawable.img_ruijie_rap_wall,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap1201",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP1201",
            shortName = "RAP1201",
            deviceType = "Wi-Fi 5 1267Mbps Wall-mounted AP",
            imageResId = R.drawable.img_ruijie_rap_wall,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap1200f",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP1200(F)",
            shortName = "RAP1200(F)",
            deviceType = "Wi-Fi 5 1267Mbps Wall-mounted AP",
            imageResId = R.drawable.img_ruijie_rap_wall,
            defaultPrefix = "C0:A4:76:"
        ),

        // --- 2C. Outdoor & OD Series APs ---
        HardwarePreset(
            id = "rap72pro_od",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP72Pro-OD",
            shortName = "RAP72Pro-OD",
            deviceType = "BE5040 Wi-Fi 7 Outdoor AP",
            imageResId = R.drawable.img_ruijie_rap_od,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap62_od",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP62-OD",
            shortName = "RAP62-OD",
            deviceType = "AX3000 Wi-Fi 6 Outdoor AP",
            imageResId = R.drawable.img_ruijie_rap_od,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap52_od",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP52-OD",
            shortName = "RAP52-OD",
            deviceType = "AC1300 Dual-Band Outdoor AP",
            imageResId = R.drawable.img_ruijie_rap_od,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap6260h",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP6260(H)",
            shortName = "RAP6260(H)",
            deviceType = "AX6000 Outdoor Omni AP",
            imageResId = R.drawable.img_ruijie_rap6260,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap6260h_d",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP6260(H)-D",
            shortName = "RAP6260(H)-D",
            deviceType = "AX6000 Outdoor Directional AP",
            imageResId = R.drawable.img_ruijie_rap6260,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap6262",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP6262",
            shortName = "RAP6262",
            deviceType = "AX3000 Outdoor Omni AP",
            imageResId = R.drawable.img_ruijie_rap6262,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "rap6202g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-RAP6202(G)",
            shortName = "RAP6202(G)",
            deviceType = "AC1300 Outdoor Omni AP",
            imageResId = R.drawable.img_ruijie_rap6202,
            defaultPrefix = "C0:A4:76:"
        ),

        // --- 3. AirMetro Series (Long-Range Bridges) ---
        HardwarePreset(
            id = "airmetro550g_b",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-AirMetro550G-B",
            shortName = "AirMetro550G-B",
            deviceType = "Long-Range Base Station Bridge",
            imageResId = R.drawable.img_ruijie_airmetro550,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "airmetro460g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-AirMetro460G",
            shortName = "AirMetro460G",
            deviceType = "Gigabit CPE Bridge (Up to 15km)",
            imageResId = R.drawable.img_ruijie_airmetro460,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "airmetro460f",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-AirMetro460F",
            shortName = "AirMetro460F",
            deviceType = "Fast Ethernet CPE Bridge",
            imageResId = R.drawable.img_ruijie_airmetro460,
            defaultPrefix = "C0:A4:76:"
        ),

        // --- 4. EST Series (Short-to-Mid Range Bridges) ---
        HardwarePreset(
            id = "est450g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST450G",
            shortName = "EST450G",
            deviceType = "15 dBi 120° Built-in Antenna Bridge",
            imageResId = R.drawable.img_ruijie_est350,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est350g",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST350G",
            shortName = "EST350G",
            deviceType = "5km 16dBi Gigabit Bridge (3x GE)",
            imageResId = R.drawable.img_ruijie_est350,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est350",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST350 V2",
            shortName = "EST350 V2",
            deviceType = "5GHz 5km Gigabit Bridge (Pair)",
            imageResId = R.drawable.img_ruijie_est350,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est330f_p",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST330F-P",
            shortName = "EST330F-P",
            deviceType = "3km 13dBi Bridge (Dual PoE-Out)",
            imageResId = R.drawable.img_ruijie_est310,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est310",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST310 V2",
            shortName = "EST310 V2",
            deviceType = "5GHz 1km Wireless Bridge (Pair)",
            imageResId = R.drawable.img_ruijie_est310,
            defaultPrefix = "C0:A4:76:"
        ),
        HardwarePreset(
            id = "est100_e",
            brand = "Ruijie / Reyee",
            modelName = "Reyee RG-EST100-E",
            shortName = "EST100-E",
            deviceType = "2.4GHz 500m Wireless Bridge (Pair)",
            imageResId = R.drawable.img_ruijie_est310,
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

        // TP-Link Archer Routers
        HardwarePreset(
            id = "archer_c54",
            brand = "TP-Link",
            modelName = "TP-Link Archer C54",
            shortName = "Archer C54",
            deviceType = "AC1200 Dual-Band Router AP",
            imageResId = R.drawable.img_tplink_archer,
            defaultPrefix = "8C:90:2D:"
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

    fun isClientDevice(name: String, hostName: String, comment: String, macAddress: String): Boolean {
        val raw = "$name $hostName $comment".lowercase()
        // If it was explicitly marked as whitelisted client
        if (raw.contains("whitelisted:")) return true
        // If it was whitelisted as a client under an AP: "AP: EW3000GX - realme-C11"
        if (comment.startsWith("ap:", ignoreCase = true) && comment.contains(" - ")) return true
        // If it contains voucher / import / profile keywords
        if (raw.contains("import") || raw.contains("voucher") || raw.contains("|") || raw.contains("profile") || raw.contains("ks") || raw.contains("mbps")) return true
        // Known mobile phone/tablet/PC brands & keywords
        val clientKeywords = listOf(
            "realme", "redmi", "iphone", "ipad", "android", "samsung", "oppo", "vivo",
            "xiaomi", "huawei", "infinix", "tecno", "poco", "honor", "motorola",
            "galaxy", "pixel", "oneplus", "phone", "tab", "pad", "desktop", "laptop", "pc"
        )
        if (clientKeywords.any { raw.contains(it) }) return true
        return false
    }

    fun isAirMetro(name: String, hostName: String = "", comment: String = "", macAddress: String = ""): Boolean {
        val raw = "$name $hostName $comment".uppercase()
        val norm = raw.replace(Regex("[^A-Z0-9]"), "")
        return norm.contains("AIRMETRO") || norm.contains("550G") || norm.contains("460G") || norm.contains("460F")
    }

    fun isAirMetroOrBridge(name: String, hostName: String = "", comment: String = "", macAddress: String = ""): Boolean {
        if (isAirMetro(name, hostName, comment, macAddress)) return true
        val raw = "$name $hostName $comment".uppercase()
        val norm = raw.replace(Regex("[^A-Z0-9]"), "")
        return norm.contains("EST350") || norm.contains("EST310") || norm.contains("EST450") ||
               norm.contains("EST330") || norm.contains("EST100") || norm.contains("EST302") ||
               (norm.contains("BRIDGE") && !norm.contains("CLIENT")) ||
               norm.contains("CPE210") || norm.contains("CPE510") || norm.contains("CPE710")
    }

    fun detectApOrClient(
        name: String,
        hostName: String = "",
        comment: String = "",
        macAddress: String = ""
    ): DeviceModelInfo {
        // First check if this is definitely a client (phone, tablet, voucher, or whitelisted client)
        if (isClientDevice(name, hostName, comment, macAddress)) {
            return detectClientModel(name, hostName, comment, macAddress)
        }

        val rawCombined = "$name $hostName $comment".uppercase()
        // Normalized alphanumeric string for flexible regex-free keyword matching
        val norm = rawCombined.replace(Regex("[^A-Z0-9]"), "")
        val mac = macAddress.trim().uppercase()
        val isTpLink = isTpLinkMac(mac) || norm.contains("TPLINK") || norm.contains("ARCHER")
        val isRuijie = !isTpLink && (isRuijieMac(mac) || norm.contains("RUIJIE") || norm.contains("REYEE"))

        // 1. Ruijie Reyee AirMetro & EST Series Wireless Bridges
        if (norm.contains("AIRMETRO") || norm.contains("550G") || norm.contains("460G") || norm.contains("460F") ||
            norm.contains("EST") || (isRuijie && norm.contains("BRIDGE"))
        ) {
            val isAirMetro550 = norm.contains("550G")
            val isAirMetro460 = norm.contains("460G") || norm.contains("460F")
            val isAirMetro = norm.contains("AIRMETRO") || isAirMetro550 || isAirMetro460
            val model = when {
                norm.contains("550G") -> "Reyee RG-AirMetro550G-B"
                norm.contains("460F") -> "Reyee RG-AirMetro460F"
                norm.contains("460G") || isAirMetro -> "Reyee RG-AirMetro460G"
                norm.contains("450G") -> "Reyee RG-EST450G"
                norm.contains("350G") -> "Reyee RG-EST350G"
                norm.contains("350") -> "Reyee RG-EST350 V2"
                norm.contains("330F") -> "Reyee RG-EST330F-P"
                norm.contains("100") -> "Reyee RG-EST100-E"
                norm.contains("302") -> "Reyee RG-EST302"
                else -> "Reyee RG-EST310 V2"
            }
            val img = when {
                isAirMetro550 -> R.drawable.img_ruijie_airmetro550
                isAirMetro460 || isAirMetro -> R.drawable.img_ruijie_airmetro460
                norm.contains("450G") || norm.contains("350G") || norm.contains("350") -> R.drawable.img_ruijie_est350
                else -> R.drawable.img_ruijie_est310
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = if (isAirMetro) "PtMP/PtP Long-Range Bridge" else "Wireless Bridge",
                imageResId = img,
                isApOrBridge = true
            )
        }

        // 2. Ruijie Reyee Wi-Fi 7 / Ultra Gaming EW Routers (EW7200BE PRO, EW6000GX)
        if (norm.contains("7200") || norm.contains("EW7200") || norm.contains("6000GX") || norm.contains("EW6000")) {
            val is6000 = norm.contains("6000")
            val model = if (is6000) "Reyee RG-EW6000GX" else "Reyee RG-EW7200BE PRO"
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = if (is6000) "6000M Wi-Fi 6 Mesh Router (2.5G)" else "Wi-Fi 7 BE7200 Gaming Router",
                imageResId = R.drawable.img_ruijie_ew7200be,
                isApOrBridge = true
            )
        }

        // 3. Ruijie Reyee Wi-Fi 6 Gaming Routers (EW3000GX, EW3000GX PRO, EW3200GX, EW1800GX, M32, M18)
        if (norm.contains("3000GX") || norm.contains("EW3000") || norm.contains("3200GX") || norm.contains("1800GX") ||
            norm.contains("M32") || norm.contains("M18") || (isRuijie && norm.contains("3000"))
        ) {
            val model = when {
                norm.contains("M32") -> "Reyee RG-M32"
                norm.contains("M18") -> "Reyee RG-M18"
                norm.contains("3200") -> "Reyee RG-EW3200GX PRO"
                norm.contains("1800") -> "Reyee RG-EW1800GX PRO"
                norm.contains("3000GXPRO") || (norm.contains("3000GX") && norm.contains("PRO")) -> "Reyee RG-EW3000GX PRO"
                else -> "Reyee RG-EW3000GX"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = if (model.contains("M32") || model.contains("M18")) "Wi-Fi 6 Mesh Unit" else "Wi-Fi 6 Gaming Router AP",
                imageResId = R.drawable.img_ruijie_ew3000gx,
                isApOrBridge = true
            )
        }

        // 4. Ruijie Reyee Gigabit Multi-Antenna Routers (EW1200G PRO, EW1300G, EW300 PRO, EW300T)
        if (norm.contains("1200G") || norm.contains("1300G") || norm.contains("EW1300") || norm.contains("EW300") || norm.contains("300PRO") || norm.contains("300T")) {
            val model = when {
                norm.contains("300T") -> "Reyee RG-EW300T"
                norm.contains("300") -> "Reyee RG-EW300 PRO"
                norm.contains("1300") -> "Reyee RG-EW1300G"
                else -> "Reyee RG-EW1200G PRO"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = if (model.contains("300T")) "4G LTE Wireless Router" else "Gigabit Router AP",
                imageResId = R.drawable.img_ruijie_ew1200g_pro,
                isApOrBridge = true
            )
        }

        // 5. Ruijie Reyee Standard EW Routers & Mesh Extenders (EW1200, EW1200R, EW series)
        if (norm.contains("EW1200") || norm.contains("RGEW") || (isRuijie && (norm.contains("EW") || norm.contains("ROUTER")))) {
            val isExtender = norm.contains("1200R") || norm.contains("300R")
            val model = when {
                norm.contains("1200R") -> "Reyee RG-EW1200R"
                norm.contains("300R") -> "Reyee RG-EW300R"
                else -> "Reyee RG-EW1200"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = if (isExtender) "Mesh Wi-Fi Extender" else "Home Router AP",
                imageResId = R.drawable.img_ruijie_ew1200,
                isApOrBridge = true
            )
        }

        // 6. Ruijie Reyee Outdoor OD Series APs (RAP72Pro-OD, RAP62-OD, RAP52-OD, OD Series)
        if (norm.contains("72PROOD") || norm.contains("62OD") || norm.contains("52OD") || (norm.contains("RAP") && norm.contains("OD"))) {
            val model = when {
                norm.contains("72") -> "Reyee RG-RAP72Pro-OD"
                norm.contains("52") -> "Reyee RG-RAP52-OD"
                else -> "Reyee RG-RAP62-OD"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Outdoor High-Power AP",
                imageResId = R.drawable.img_ruijie_rap_od,
                isApOrBridge = true
            )
        }

        // 7. Ruijie Reyee Heavy-Duty Outdoor APs (RAP6260, RAP6262, RAP6202)
        if (norm.contains("6260") || norm.contains("6262") || norm.contains("6202") || (norm.contains("RAP") && norm.contains("OUTDOOR"))) {
            val isOmni = norm.contains("6202")
            val model = when {
                isOmni -> "Reyee RG-RAP6202(G)"
                norm.contains("6262") -> "Reyee RG-RAP6262(H)"
                else -> "Reyee RG-RAP6260(H)"
            }
            val img = when {
                isOmni -> R.drawable.img_ruijie_rap6202
                norm.contains("6262") -> R.drawable.img_ruijie_rap6262
                else -> R.drawable.img_ruijie_rap6260
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = if (isOmni) "Outdoor Omnidirectional AP" else "Outdoor High-Power AP",
                imageResId = img,
                isApOrBridge = true
            )
        }

        // 8. Ruijie Reyee Wall-Plate APs (RAP1260, RAP1261, RAP1201, RAP1200)
        if (norm.contains("1261") || norm.contains("1260") || norm.contains("1201") || (norm.contains("RAP") && norm.contains("WALL"))) {
            val model = when {
                norm.contains("1261") -> "Reyee RG-RAP1261"
                norm.contains("1260") -> "Reyee RG-RAP1260"
                norm.contains("1201") -> "Reyee RG-RAP1201"
                else -> "Reyee RG-RAP1200(F)"
            }
            return DeviceModelInfo(
                brand = "Ruijie / Reyee",
                modelName = model,
                deviceType = "Wall-Plate Access Point",
                imageResId = R.drawable.img_ruijie_rap_wall,
                isApOrBridge = true
            )
        }

        // 9. Ruijie / Reyee Ceiling-Mount APs (RAP73HD, RAP72, RAP2260, RAP2200, RAP62)
        if (!isTpLink && (norm.contains("RAP") || (isRuijie && (norm.contains("AP") || norm.contains("CEILING"))))) {
            val model = when {
                norm.contains("73HD") || norm.contains("73") -> "Reyee RG-RAP73HD"
                norm.contains("72") -> "Reyee RG-RAP72"
                norm.contains("2260H") -> "Reyee RG-RAP2260(H)"
                norm.contains("2260") -> "Reyee RG-RAP2260(E)"
                norm.contains("62") -> "Reyee RG-RAP62"
                norm.contains("1200") -> "Reyee RG-RAP1200(F)"
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

        // 7. TP-Link Deco Mesh System
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

        // 8. TP-Link Pharos CPE Wireless Bridge (CPE210, CPE220, CPE510, CPE610, CPE710)
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

        // 9. TP-Link Omada Ceiling & Outdoor AP (EAP225, EAP245, EAP610, EAP620, EAP650, EAP660)
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

        // 10. TP-Link Archer / TL-WR Home Routers
        if (norm.contains("ARCHER") || norm.contains("TLWR") || norm.contains("WR840") || norm.contains("WR841") || norm.contains("AX73") || norm.contains("AX53") || norm.contains("AX23") || norm.contains("AX12") || norm.contains("AX10") || norm.contains("C80") || norm.contains("C54") || isTpLink) {
            val model = when {
                norm.contains("C54") || norm.contains("54") -> "TP-Link Archer C54"
                norm.contains("AX73") || norm.contains("AX72") -> "TP-Link Archer AX73"
                norm.contains("AX53") || norm.contains("AX50") -> "TP-Link Archer AX53"
                norm.contains("AX23") || norm.contains("AX20") -> "TP-Link Archer AX23"
                norm.contains("AX12") -> "TP-Link Archer AX12"
                norm.contains("AX10") -> "TP-Link Archer AX10"
                norm.contains("C80") -> "TP-Link Archer C80"
                norm.contains("C50") || norm.contains("C20") -> "TP-Link Archer C50"
                norm.contains("WR840") || norm.contains("WR841") -> "TP-Link TL-WR840N"
                isTpLinkMac(mac) -> "TP-Link Archer C54"
                else -> "TP-Link Archer C54"
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
        return detectClientModel(name, hostName, comment, macAddress)
    }

    fun detectClientModel(
        name: String,
        hostName: String = "",
        comment: String = "",
        macAddress: String = ""
    ): DeviceModelInfo {
        val cleanHost = hostName.trim().replace('_', '-')
        val cleanComment = comment.trim()
        val cleanMac = macAddress.trim().uppercase()

        // 1. Identify raw candidate text for device identification
        val candidate = when {
            cleanHost.isNotBlank() && !cleanHost.startsWith("android-", ignoreCase = true) -> cleanHost
            cleanComment.startsWith("Whitelisted: ", ignoreCase = true) -> cleanComment.removePrefix("Whitelisted: ").trim()
            cleanComment.contains(" - ") -> cleanComment.substringAfter(" - ").trim()
            cleanHost.isNotBlank() -> cleanHost
            else -> ""
        }

        val upperCand = candidate.uppercase()
        val upperHost = cleanHost.uppercase()

        // 2. Check by hostname, candidate, or MAC OUI
        // A. SAMSUNG
        val isSamsungOUI = SAMSUNG_OUIS.any { cleanMac.startsWith(it) }
        val isSamsungName = upperCand.contains("SAMSUNG") || upperCand.contains("GALAXY") || upperCand.contains("SM-") || upperHost.startsWith("SM-")
        if (isSamsungName || isSamsungOUI) {
            val smMatch = Regex("SM-[A-Z0-9]+").find(upperCand)?.value ?: Regex("SM-[A-Z0-9]+").find(upperHost)?.value ?: ""
            val cleanName = candidate
                .replace("(?i)samsung-?".toRegex(), "")
                .replace("(?i)galaxy-?".toRegex(), "Galaxy ")
                .replace("-", " ")
                .trim()
            val finalModel = when {
                cleanName.isNotBlank() && !cleanName.startsWith("SM-", ignoreCase = true) -> if (cleanName.startsWith("Galaxy", ignoreCase = true)) cleanName else "Galaxy $cleanName"
                smMatch.isNotBlank() -> "Galaxy ($smMatch)"
                else -> "Samsung Galaxy"
            }
            val display = if (smMatch.isNotBlank() && !finalModel.contains(smMatch)) "Samsung $finalModel ($smMatch)" else "Samsung $finalModel"
            return DeviceModelInfo(
                brand = "Samsung",
                modelName = finalModel,
                deviceType = if (upperCand.contains("TAB")) "Tablet" else "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = smMatch,
                displayName = display
            )
        }

        // B. APPLE (iPhone, iPad, Mac)
        val isAppleOUI = APPLE_OUIS.any { cleanMac.startsWith(it) }
        val isAppleName = upperCand.contains("IPHONE") || upperCand.contains("IPAD") || upperCand.contains("MACBOOK") || upperCand.contains("APPLE")
        if (isAppleName || isAppleOUI) {
            val isIpad = upperCand.contains("IPAD")
            val isMac = upperCand.contains("MACBOOK") || upperCand.contains("MAC")
            val cleanName = candidate.replace("-", " ").trim()
            val finalModel = when {
                cleanName.isNotBlank() && (cleanName.contains("iPhone", ignoreCase = true) || cleanName.contains("iPad", ignoreCase = true)) -> cleanName
                isIpad -> "iPad"
                isMac -> "MacBook"
                else -> "iPhone"
            }
            return DeviceModelInfo(
                brand = "Apple",
                modelName = finalModel,
                deviceType = if (isIpad) "Tablet" else if (isMac) "Laptop" else "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = "",
                displayName = "Apple $finalModel"
            )
        }

        // C. XIAOMI / REDMI / POCO
        val isXiaomiOUI = XIAOMI_OUIS.any { cleanMac.startsWith(it) }
        val isXiaomiName = upperCand.contains("REDMI") || upperCand.contains("POCO") || upperCand.contains("XIAOMI") || upperCand.contains("MI-")
        val miCodeMatch = Regex("\\b(2[0-9]{3}[0-9A-Z]{3,7}[A-Z]|M2[0-9]{3}[A-Z0-9]+)\\b").find(upperCand)?.value ?: ""
        if (isXiaomiName || isXiaomiOUI || miCodeMatch.isNotBlank()) {
            val isPoco = upperCand.contains("POCO")
            val isRedmi = upperCand.contains("REDMI")
            val brand = if (isPoco) "POCO" else if (isRedmi) "Redmi" else "Xiaomi"
            val cleanName = candidate
                .replace("(?i)xiaomi-?".toRegex(), "")
                .replace("(?i)redmi-?".toRegex(), "")
                .replace("(?i)poco-?".toRegex(), "")
                .replace("-", " ")
                .trim()
            val model = when {
                cleanName.isNotBlank() && cleanName != miCodeMatch -> "$brand $cleanName"
                miCodeMatch.isNotBlank() -> "$brand ($miCodeMatch)"
                else -> "$brand Phone"
            }
            val display = if (miCodeMatch.isNotBlank() && !model.contains(miCodeMatch)) "$model ($miCodeMatch)" else model
            return DeviceModelInfo(
                brand = brand,
                modelName = model,
                deviceType = "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = miCodeMatch,
                displayName = display
            )
        }

        // D. OPPO & REALME
        val isOppoOUI = OPPO_OUIS.any { cleanMac.startsWith(it) }
        val cphMatch = Regex("CPH[0-9]{4}").find(upperCand)?.value ?: ""
        val rmxMatch = Regex("RMX[0-9]{4}").find(upperCand)?.value ?: ""
        val isOppoName = upperCand.contains("OPPO") || cphMatch.isNotBlank()
        val isRealmeName = upperCand.contains("REALME") || rmxMatch.isNotBlank()
        if (isOppoName || isRealmeName || isOppoOUI) {
            val isRealme = isRealmeName || rmxMatch.isNotBlank()
            val brand = if (isRealme) "Realme" else "OPPO"
            val code = if (isRealme) rmxMatch else cphMatch
            val cleanName = candidate
                .replace("(?i)oppo-?".toRegex(), "")
                .replace("(?i)realme-?".toRegex(), "")
                .replace("-", " ")
                .trim()
            val model = when {
                cleanName.isNotBlank() && cleanName != code -> "$brand $cleanName"
                code.isNotBlank() -> "$brand ($code)"
                else -> "$brand Phone"
            }
            val display = if (code.isNotBlank() && !model.contains(code)) "$model ($code)" else model
            return DeviceModelInfo(
                brand = brand,
                modelName = model,
                deviceType = "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = code,
                displayName = display
            )
        }

        // E. VIVO & iQOO
        val isVivoOUI = VIVO_OUIS.any { cleanMac.startsWith(it) }
        val vivoCodeMatch = Regex("V2[0-9]{3}[A-Z]?").find(upperCand)?.value ?: ""
        val isVivoName = upperCand.contains("VIVO") || upperCand.contains("IQOO") || vivoCodeMatch.isNotBlank()
        if (isVivoName || isVivoOUI) {
            val isIqoo = upperCand.contains("IQOO")
            val brand = if (isIqoo) "iQOO" else "vivo"
            val cleanName = candidate
                .replace("(?i)vivo-?".toRegex(), "")
                .replace("(?i)iqoo-?".toRegex(), "")
                .replace("-", " ")
                .trim()
            val model = when {
                cleanName.isNotBlank() && cleanName != vivoCodeMatch -> "$brand $cleanName"
                vivoCodeMatch.isNotBlank() -> "$brand ($vivoCodeMatch)"
                else -> "$brand Phone"
            }
            val display = if (vivoCodeMatch.isNotBlank() && !model.contains(vivoCodeMatch)) "$model ($vivoCodeMatch)" else model
            return DeviceModelInfo(
                brand = brand,
                modelName = model,
                deviceType = "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = vivoCodeMatch,
                displayName = display
            )
        }

        // F. TRANSSION (Infinix, Tecno, itel)
        val isTranssionOUI = TRANSSION_OUIS.any { cleanMac.startsWith(it) }
        val infinixMatch = Regex("X[0-9]{4}[A-Z]?").find(upperCand)?.value ?: ""
        val tecnoMatch = Regex("(?:KI|CK|KG|BF|CH|LH)[0-9][a-z0-9]?").find(upperCand)?.value ?: ""
        val isInf = upperCand.contains("INFINIX") || infinixMatch.isNotBlank()
        val isTecno = upperCand.contains("TECNO") || tecnoMatch.isNotBlank()
        val isItel = upperCand.contains("ITEL")
        if (isInf || isTecno || isItel || isTranssionOUI) {
            val brand = when {
                isInf -> "Infinix"
                isTecno -> "Tecno"
                isItel -> "itel"
                else -> "Transsion"
            }
            val code = when {
                isInf -> infinixMatch
                isTecno -> tecnoMatch
                else -> ""
            }
            val cleanName = candidate
                .replace("(?i)infinix-?".toRegex(), "")
                .replace("(?i)tecno-?".toRegex(), "")
                .replace("(?i)itel-?".toRegex(), "")
                .replace("-", " ")
                .trim()
            val model = when {
                cleanName.isNotBlank() && cleanName != code -> "$brand $cleanName"
                code.isNotBlank() -> "$brand ($code)"
                else -> "$brand Phone"
            }
            val display = if (code.isNotBlank() && !model.contains(code)) "$model ($code)" else model
            return DeviceModelInfo(
                brand = brand,
                modelName = model,
                deviceType = "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = code,
                displayName = display
            )
        }

        // G. HUAWEI & HONOR
        val isHuaweiOUI = HUAWEI_OUIS.any { cleanMac.startsWith(it) }
        val isHuaweiName = upperCand.contains("HUAWEI") || upperCand.contains("HONOR")
        if (isHuaweiName || isHuaweiOUI) {
            val isHonor = upperCand.contains("HONOR")
            val brand = if (isHonor) "Honor" else "Huawei"
            val cleanName = candidate
                .replace("(?i)huawei-?".toRegex(), "")
                .replace("(?i)honor-?".toRegex(), "")
                .replace("-", " ")
                .trim()
            val model = if (cleanName.isNotBlank()) "$brand $cleanName" else "$brand Phone"
            return DeviceModelInfo(
                brand = brand,
                modelName = model,
                deviceType = "Smartphone",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = "",
                displayName = model
            )
        }

        // H. WINDOWS PC / LAPTOP
        if (upperCand.startsWith("DESKTOP-") || upperCand.startsWith("LAPTOP-") || upperCand.contains("SURFACE")) {
            val code = candidate.substringAfter("-").take(7)
            return DeviceModelInfo(
                brand = "Windows",
                modelName = if (upperCand.startsWith("LAPTOP-")) "Laptop" else "Desktop PC",
                deviceType = "Computer",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = code,
                displayName = "Windows PC ($candidate)"
            )
        }

        // I. Generic Android with Hostname (e.g. android-abcd123)
        if (cleanHost.isNotBlank()) {
            val code = if (cleanHost.startsWith("android-", ignoreCase = true)) cleanHost.removePrefix("android-").take(8) else ""
            return DeviceModelInfo(
                brand = "Android",
                modelName = cleanHost.replace("-", " "),
                deviceType = "Mobile Device",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = code,
                displayName = if (code.isNotBlank()) "Android Device ($code)" else cleanHost.replace("-", " ")
            )
        }

        // J. Randomized / Private MAC check
        val isLAA = cleanMac.length >= 2 && listOf('2', '6', 'A', 'E').contains(cleanMac[1])
        if (isLAA) {
            val shortCode = if (cleanMac.length >= 8) cleanMac.substring(cleanMac.length - 8) else cleanMac
            return DeviceModelInfo(
                brand = "Mobile",
                modelName = "Private MAC Device",
                deviceType = "Client Device",
                imageResId = 0,
                isApOrBridge = false,
                modelCode = shortCode,
                displayName = "Private Wi-Fi ($shortCode)"
            )
        }

        return DeviceModelInfo(
            brand = "Client",
            modelName = name.ifBlank { "Connected Device" },
            deviceType = "Connected Client",
            imageResId = 0,
            isApOrBridge = false,
            modelCode = "",
            displayName = name.ifBlank { "Connected Client" }
        )
    }
}
