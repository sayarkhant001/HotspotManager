package com.example.domain.models

/** A network service / app category that can be blocked via MikroTik Layer7 */
data class AppServiceCategory(
    val id: String,
    val name: String,
    val group: String,
    val icon: String = "🌐",
    val description: String = "",
    /** RouterOS Layer7 regex patterns */
    val layer7Patterns: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val cidrs: List<String> = emptyList()
)

/** All known service categories for the flow controller */
val APP_SERVICE_CATEGORIES: List<AppServiceCategory> = listOf(
    // ── Social Media ──
    AppServiceCategory("facebook","Facebook", "Social Media", "📘",
        "Blocks Facebook feed + Messenger + Instagram",
        listOf("^.+(facebook|fbcdn|fb\\.com|messenger|instagram).*$"),
        listOf("facebook.com","*.facebook.com","*.fbcdn.net","*.fb.com","*.messenger.com","*.instagram.com")),
    AppServiceCategory("youtube","YouTube", "Social Media", "▶️",
        "Blocks YouTube videos + Shorts",
        listOf("^.+(youtube|youtu\\.be|googlevideo|ytimg).*$"),
        listOf("*.youtube.com","*.youtu.be","*.googlevideo.com","*.ytimg.com")),
    AppServiceCategory("tiktok","TikTok", "Social Media", "🎵",
        "Blocks TikTok app + Web",
        listOf("^.+(tiktok|bytecdn|musical\\.ly).*$"),
        listOf("*.tiktok.com","*.tiktokcdn.com","*.bytecdn.com","*.musical.ly")),
    // ── Messaging ──
    AppServiceCategory("telegram","Telegram", "Messaging", "✈️",
        "Blocks Telegram MTProto + Web",
        emptyList(), emptyList(),
        listOf("149.154.160.0/20","91.108.56.0/22","91.108.4.0/22")),
    AppServiceCategory("whatsapp","WhatsApp", "Messaging", "💬",
        "Blocks WhatsApp messages + Voice",
        emptyList(),
        listOf("*.whatsapp.com","*.whatsapp.net","*.wa.me"),
        listOf("157.240.0.0/16","31.13.64.0/18")),
    // ── Video Streaming ──
    AppServiceCategory("netflix","Netflix", "Video Streaming", "🎬",
        "Blocks Netflix streaming",
        emptyList(),
        listOf("*.netflix.com","*.nflxvideo.net","*.nflxso.net")),
    // ── Gaming ──
    AppServiceCategory("pubg","PUBG Mobile", "Gaming", "🔫",
        "Blocks PUBG + BGMI",
        emptyList(), emptyList(),
        listOf("103.10.0.0/16","43.255.0.0/16","125.209.0.0/16")),
    AppServiceCategory("freefire","Free Fire", "Gaming", "🔥",
        "Blocks Free Fire game",
        emptyList(), emptyList(),
        listOf("103.10.0.0/16","103.229.0.0/16"))
)

val APP_SERVICE_GROUPS = APP_SERVICE_CATEGORIES.map { it.group }.distinct()