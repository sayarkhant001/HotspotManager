package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.legrange.mikrotik.ApiConnection
import javax.net.SocketFactory

data class RouterStats(
    val cpuLoad: String,
    val freeMemory: String,
    val totalMemory: String,
    val uptime: String,
    val boardName: String,
    val version: String
)

data class ActiveUser(
    val id: String,
    val server: String,
    val user: String,
    val address: String,
    val macAddress: String,
    val uptime: String,
    val bytesIn: String,
    val bytesOut: String
)

data class RouterProfileInfo(
    val id: String,
    val name: String,
    val rateLimit: String = "",
    val sharedUsers: String = "1",
    val onLogin: String = ""
)

class MikrotikClient {
    private var connection: ApiConnection? = null

    suspend fun connect(ip: String, user: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            connection = ApiConnection.connect(SocketFactory.getDefault(), ip, ApiConnection.DEFAULT_PORT, 5000)
            connection?.login(user, pass)
            Result.success(Unit)
        } catch (e: me.legrange.mikrotik.MikrotikApiException) {
            e.printStackTrace()
            Result.failure(Exception("MikroTik API Error: ${e.message}"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("Connection Refused. Is API service enabled on port 8728?"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("Connection Timeout. Check IP and firewall."))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Error: ${e.localizedMessage}"))
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            connection?.close()
            connection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isConnected(): Boolean = connection != null && connection?.isConnected == true

    suspend fun getRouterStats(): RouterStats? = withContext(Dispatchers.IO) {
        try {
            val res = connection?.execute("/system/resource/print")
            if (!res.isNullOrEmpty()) {
                val data = res[0]
                RouterStats(
                    cpuLoad = data["cpu-load"] ?: "0",
                    freeMemory = data["free-memory"] ?: "0",
                    totalMemory = data["total-memory"] ?: "0",
                    uptime = data["uptime"] ?: "0",
                    boardName = data["board-name"] ?: "Unknown",
                    version = data["version"] ?: "Unknown"
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getInterfaceTraffic(interfaceName: String = "hotspot-bridge"): Pair<Long, Long> = withContext(Dispatchers.IO) {
        try {
            // /interface/monitor-traffic interface=<name> once=
            val res = connection?.execute("/interface/monitor-traffic =interface=$interfaceName =once=")
            if (!res.isNullOrEmpty()) {
                val data = res[0]
                val rx = data["rx-bits-per-second"]?.toLongOrNull() ?: 0L
                val tx = data["tx-bits-per-second"]?.toLongOrNull() ?: 0L
                Pair(rx, tx)
            } else Pair(0L, 0L)
        } catch (e: Exception) {
            // Fallback: try ether1 if hotspot-bridge is not directly named
            try {
                val resFallback = connection?.execute("/interface/monitor-traffic =interface=ether1 =once=")
                if (!resFallback.isNullOrEmpty()) {
                    val data = resFallback[0]
                    val rx = data["rx-bits-per-second"]?.toLongOrNull() ?: 0L
                    val tx = data["tx-bits-per-second"]?.toLongOrNull() ?: 0L
                    Pair(rx, tx)
                } else Pair(0L, 0L)
            } catch (ex: Exception) {
                Pair(0L, 0L)
            }
        }
    }

    suspend fun getActiveUsers(): List<ActiveUser> = withContext(Dispatchers.IO) {
        try {
            val res = connection?.execute("/ip/hotspot/active/print")
            res?.map {
                ActiveUser(
                    id = it[".id"] ?: "",
                    server = it["server"] ?: "",
                    user = it["user"] ?: "",
                    address = it["address"] ?: "",
                    macAddress = it["mac-address"] ?: "",
                    uptime = it["uptime"] ?: "",
                    bytesIn = it["bytes-in"] ?: "0",
                    bytesOut = it["bytes-out"] ?: "0"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun removeActiveSession(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            connection?.execute("/ip/hotspot/active/remove =.id=$id")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun banMacAddress(mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Drop active session if any
            val active = connection?.execute("/ip/hotspot/active/print")
            active?.forEach {
                if (it["mac-address"].equals(mac, ignoreCase = true)) {
                    val id = it[".id"]
                    if (!id.isNullOrEmpty()) {
                        connection?.execute("/ip/hotspot/active/remove =.id=$id")
                    }
                }
            }
            // Add to IP binding as blocked
            connection?.execute("/ip/hotspot/ip-binding/add =mac-address=$mac =type=blocked =comment=Banned via App")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getRouterProfiles(): List<RouterProfileInfo> = withContext(Dispatchers.IO) {
        try {
            val res = connection?.execute("/ip/hotspot/user/profile/print")
            res?.map {
                RouterProfileInfo(
                    id = it[".id"] ?: "",
                    name = it["name"] ?: "",
                    rateLimit = it["rate-limit"] ?: "",
                    sharedUsers = it["shared-users"] ?: "1",
                    onLogin = it["on-login"] ?: ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addRouterProfile(name: String, rateLimit: String, sharedUsers: Int = 1): Boolean = withContext(Dispatchers.IO) {
        try {
            var cmd = "/ip/hotspot/user/profile/add =name=$name =shared-users=$sharedUsers"
            if (rateLimit.isNotBlank()) {
                cmd += " =rate-limit=$rateLimit"
            }
            connection?.execute(cmd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun addHotspotUser(
        name: String,
        password: String,
        profile: String,
        comment: String,
        limitBytesTotal: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var cmd = "/ip/hotspot/user/add =name=$name =password=$password =profile=$profile"
            if (comment.isNotBlank()) {
                cmd += " =comment=$comment"
            }
            if (limitBytesTotal > 0L) {
                cmd += " =limit-bytes-total=$limitBytesTotal"
            }
            connection?.execute(cmd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

