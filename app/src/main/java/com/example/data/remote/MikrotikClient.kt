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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    suspend fun banMacAddress(mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Drop from active
            val active = connection?.execute("/ip/hotspot/active/print where mac-address=$mac")
            if (!active.isNullOrEmpty()) {
                val id = active[0][".id"]
                connection?.execute("/ip/hotspot/active/remove .id=$id")
            }
            // Add to IP binding as blocked
            connection?.execute("/ip/hotspot/ip-binding/add mac-address=$mac type=blocked comment=\"Banned via App\"")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
