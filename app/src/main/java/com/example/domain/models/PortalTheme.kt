package com.example.domain.models

/** A captive portal theme with preview info and deployment assets */
data class PortalTheme(
    val id: String,                      // unique key, e.g. "emerald-glass"
    val name: String,                    // "Emerald Glass"
    val description: String,             // short tagline
    val author: String = "HotspotManager",
    val primaryColor: Long = 0xFF00E676, // emerald green
    val secondaryColor: Long = 0xFF10B981, // lighter green
    val accentColor: Long = 0xFF059669,   // dark green
    val surfaceColor: Long = 0xFF1B5E20,  // deep forest
    val textColor: Long = 0xFFFFFFFF,      // white
    val isFeatured: Boolean = false,
    /** URL or inline HTML template content for the portal */
    val portalHtmlUrl: String = "",
    /** CSS variable overrides for the theme */
    val pricePosition: String = "bottom-right", // where price cards show on portal
    val showProfiles: Boolean = true,    // auto-show data plans
    val showSsid: Boolean = true,       // show SSID greeting
    val showLogo: Boolean = true
)