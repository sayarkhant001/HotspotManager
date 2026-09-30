package com.example.data.remote

import com.example.domain.models.ActiveUser
import com.example.domain.models.Voucher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class RouterStats(
    val cpuLoad: String,
    val freeMemory: String,
    val totalMemory: String,
    val uptime: String,
    val boardName: String,
    val version: String
)

data class InterfaceMetrics(
    val rxSpeedBps: Long,
    val txSpeedBps: Long,
    val totalRxBytes: Long,
    val totalTxBytes: Long
)

data class RouterProfileInfo(
    val id: String,
    val name: String,
    val rateLimit: String = "",
    val sharedUsers: String = "1",
    val onLogin: String = ""
)

data class RouterHotspotUser(
    val id: String,
    val name: String,
    val password: String,
    val profile: String,
    val comment: String,
    val uptime: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val limitBytesTotal: Long,
    val disabled: Boolean
)

/**
 * Pure, high-performance RouterOS API connection.
 * Fully compatible with RouterOS v6 and RouterOS v7 (!empty word support).
 * Eliminates 60-second hanging bugs in third-party libraries.
 */
class RawRouterOSConnection {
    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var tagCounter = 0

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect(host: String, port: Int = 8728, timeoutMs: Int = 4000) {
        close()
        val s = Socket()
        s.soTimeout = timeoutMs
        s.connect(InetSocketAddress(host, port), timeoutMs)
        socket = s
        inStream = s.getInputStream()
        outStream = s.getOutputStream()
    }

    fun login(user: String, pass: String) {
        val res = execute("/login", "name=$user", "password=$pass")
        val challenge = res.firstOrNull()?.get("ret")
        if (!challenge.isNullOrEmpty()) {
            val md5 = md5Challenge(pass, challenge)
            execute("/login", "name=$user", "response=00$md5")
        }
    }

    @Synchronized
    fun execute(command: String, vararg params: String): List<Map<String, String>> {
        val out = outStream ?: throw IllegalStateException("Not connected to router")
        val inS = inStream ?: throw IllegalStateException("Not connected to router")

        val tag = (++tagCounter).toString()
        writeWord(out, command)
        writeWord(out, ".tag=$tag")
        for (p in params) {
            val trimmed = p.trim()
            if (trimmed.startsWith("=")) {
                writeWord(out, trimmed)
            } else {
                writeWord(out, "=$trimmed")
            }
        }
        writeWord(out, "")
        out.flush()

        val results = mutableListOf<Map<String, String>>()
        var current: MutableMap<String, String>? = null

        while (true) {
            val word = readWord(inS) ?: throw IllegalStateException("Router closed connection")
            if (word.isEmpty()) {
                if (current != null) {
                    results.add(current)
                    current = null
                }
                continue
            }

            when {
                word == "!re" -> {
                    current = mutableMapOf()
                }
                word == "!done" -> {
                    while (true) {
                        val rest = readWord(inS)
                        if (rest.isNullOrEmpty()) break
                    }
                    return results
                }
                word == "!empty" -> {
                    // RouterOS v7 empty result set marker
                }
                word == "!trap" -> {
                    var errorMsg = "Router error: !trap"
                    while (true) {
                        val rest = readWord(inS)
                        if (rest.isNullOrEmpty()) break
                        if (rest.startsWith("=message=")) {
                            errorMsg = rest.substring(9)
                        }
                    }
                    // Drain the terminating !done sentence for this command so socket stream stays in sync
                    try {
                        while (true) {
                            val nextWord = readWord(inS) ?: break
                            if (nextWord == "!done") {
                                while (true) {
                                    val doneRest = readWord(inS)
                                    if (doneRest.isNullOrEmpty()) break
                                }
                                break
                            }
                            if (nextWord.isEmpty()) break
                        }
                    } catch (_: Exception) {}
                    throw RuntimeException(errorMsg)
                }
                word == "!fatal" -> {
                    close()
                    throw RuntimeException("Router connection fatal error: socket closed by router")
                }
                word.startsWith("=") -> {
                    if (current != null) {
                        val eq = word.indexOf('=', 1)
                        if (eq > 0) {
                            val k = word.substring(1, eq)
                            val v = word.substring(eq + 1)
                            current[k] = v
                        }
                    }
                }
            }
        }
    }

    private fun writeWord(out: OutputStream, word: String) {
        val bytes = word.toByteArray(StandardCharsets.UTF_8)
        val len = bytes.size
        when {
            len < 0x80 -> out.write(len)
            len < 0x4000 -> {
                out.write((len shr 8) or 0x80)
                out.write(len and 0xFF)
            }
            else -> {
                out.write((len shr 16) or 0xC0)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(bytes)
    }

    private fun readWord(inS: InputStream): String? {
        val b1 = inS.read()
        if (b1 == -1) return null
        val len = when {
            (b1 and 0x80) == 0 -> b1
            (b1 and 0xC0) == 0x80 -> {
                val b2 = inS.read()
                ((b1 and 0x3F) shl 8) or b2
            }
            (b1 and 0xE0) == 0xC0 -> {
                val b2 = inS.read()
                val b3 = inS.read()
                ((b1 and 0x1F) shl 16) or (b2 shl 8) or b3
            }
            else -> 0
        }
        if (len == 0) return ""
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val r = inS.read(buf, off, len - off)
            if (r == -1) break
            off += r
        }
        return String(buf, StandardCharsets.UTF_8)
    }

    private fun md5Challenge(pass: String, challengeHex: String): String {
        val challengeBytes = ByteArray(challengeHex.length / 2)
        for (i in challengeBytes.indices) {
            challengeBytes[i] = challengeHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val md = MessageDigest.getInstance("MD5")
        md.update(0.toByte())
        md.update(pass.toByteArray(StandardCharsets.UTF_8))
        md.update(challengeBytes)
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        inStream = null
        outStream = null
    }
}

class MikrotikClient {
    private var connection: RawRouterOSConnection? = null
    private val apiMutex = Mutex()
    private var lastIp: String = ""
    private var lastUser: String = ""
    private var lastPass: String = ""

    @Volatile
    private var cachedHotspotUsers: List<RouterHotspotUser> = emptyList()

    fun getCachedHotspotUsers(): List<RouterHotspotUser> = cachedHotspotUsers

    private var lastTrafficTime: Long = 0L
    private var lastWanRxBytes: Long = 0L
    private var lastWanTxBytes: Long = 0L
    private var currentRxBps: Long = 0L
    private var currentTxBps: Long = 0L

    suspend fun connect(ip: String, user: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                lastIp = ip
                lastUser = user
                lastPass = pass
                connection?.close()
                val conn = RawRouterOSConnection()
                conn.connect(ip, 8728, 4000)
                conn.login(user, pass)
                connection = conn
                Result.success(Unit)
            } catch (e: Exception) {
                connection?.close()
                connection = null
                e.printStackTrace()
                Result.failure(Exception("MikroTik Error: ${e.message}"))
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            connection?.close()
            connection = null
        }
    }

    fun isConnected(): Boolean = connection?.isConnected == true

    fun getCurrentIp(): String = lastIp
    fun getCurrentUser(): String = lastUser
    fun getCurrentPass(): String = lastPass

    suspend fun changeUserPassword(oldPass: String, newPass: String): Result<Unit> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                if (lastPass.isNotBlank() && oldPass != lastPass) {
                    return@withContext Result.failure(Exception("Current password is incorrect"))
                }
                if (newPass.isBlank()) {
                    return@withContext Result.failure(Exception("New password cannot be empty"))
                }
                val conn = ensureConnectedInternal()
                    ?: return@withContext Result.failure(Exception("Not connected to router"))

                val userList = conn.execute("/user/print", "=.proplist=.id,name")
                val targetUser = userList.firstOrNull { it["name"].equals(lastUser, ignoreCase = true) }
                val userId = targetUser?.get(".id")

                if (!userId.isNullOrBlank()) {
                    conn.execute("/user/set", ".id=$userId", "password=$newPass")
                } else {
                    conn.execute("/user/set", "numbers=$lastUser", "password=$newPass")
                }

                lastPass = newPass
                Result.success(Unit)
            } catch (e: Exception) {
                handleApiError(e)
                Result.failure(Exception(e.localizedMessage ?: "Failed to update router password"))
            }
        }
    }

    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            ensureConnectedInternal() != null && connection?.isConnected == true
        }
    }

    private fun ensureConnectedInternal(): RawRouterOSConnection? {
        if (connection != null && connection?.isConnected == true) {
            return connection
        }
        if (lastIp.isNotBlank() && lastUser.isNotBlank()) {
            try {
                connection?.close()
                val conn = RawRouterOSConnection()
                conn.connect(lastIp, 8728, 4000)
                conn.login(lastUser, lastPass)
                connection = conn
            } catch (e: Exception) {
                e.printStackTrace()
                connection = null
            }
        }
        return if (connection?.isConnected == true) connection else null
    }

    private var lastLeaseTime = 0L
    private var cachedLeasesMap = mutableMapOf<String, String>()

    private fun handleApiError(e: Exception) {
        e.printStackTrace()
        try {
            connection?.close()
        } catch (_: Exception) {}
        connection = null
    }

    suspend fun getRouterStats(): RouterStats? = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext null
                val res = conn.execute("/system/resource/print")
                if (res.isNotEmpty()) {
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
                handleApiError(e)
                null
            }
        }
    }

    suspend fun getInterfaceMetrics(): InterfaceMetrics = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            val conn = ensureConnectedInternal() ?: return@withContext InterfaceMetrics(currentRxBps, currentTxBps, 0L, 0L)
            try {
                val res = conn.execute("/interface/print")
                var rxBytes = 0L
                var txBytes = 0L
                var rxTotal = 0L
                var txTotal = 0L

                res.forEach {
                    val name = it["name"] ?: ""
                    val r = it["rx-byte"]?.toLongOrNull() ?: 0L
                    val t = it["tx-byte"]?.toLongOrNull() ?: 0L

                    if (name == "ether1") {
                        rxBytes = r
                        txBytes = t
                    }
                    if (name == "hotspot-bridge" || name == "ether1") {
                        if (r > rxTotal) rxTotal = r
                        if (t > txTotal) txTotal = t
                    }
                }

                if (rxBytes == 0L && txBytes == 0L) {
                    res.forEach {
                        val name = it["name"] ?: ""
                        if (name == "hotspot-bridge") {
                            rxBytes = it["tx-byte"]?.toLongOrNull() ?: 0L
                            txBytes = it["rx-byte"]?.toLongOrNull() ?: 0L
                        }
                    }
                }

                val now = System.currentTimeMillis()
                if (lastTrafficTime > 0L && now > lastTrafficTime) {
                    val dt = (now - lastTrafficTime) / 1000.0
                    if (dt >= 0.5) {
                        val dRx = if (rxBytes >= lastWanRxBytes) rxBytes - lastWanRxBytes else 0L
                        val dTx = if (txBytes >= lastWanTxBytes) txBytes - lastWanTxBytes else 0L
                        currentRxBps = (dRx * 8.0 / dt).toLong()
                        currentTxBps = (dTx * 8.0 / dt).toLong()
                        lastTrafficTime = now
                        lastWanRxBytes = rxBytes
                        lastWanTxBytes = txBytes
                    }
                } else {
                    lastTrafficTime = now
                    lastWanRxBytes = rxBytes
                    lastWanTxBytes = txBytes
                }

                InterfaceMetrics(currentRxBps, currentTxBps, rxTotal, txTotal)
            } catch (e: Exception) {
                handleApiError(e)
                InterfaceMetrics(currentRxBps, currentTxBps, 0L, 0L)
            }
        }
    }

    suspend fun getInterfaceTraffic(): Pair<Long, Long> {
        val m = getInterfaceMetrics()
        return Pair(m.rxSpeedBps, m.txSpeedBps)
    }

    suspend fun getRouterTotalDataBytes(): Pair<Long, Long> {
        val m = getInterfaceMetrics()
        return Pair(m.totalRxBytes, m.totalTxBytes)
    }

    suspend fun getActiveUsers(): List<ActiveUser> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext emptyList()
                val now = System.currentTimeMillis()
                if (now - lastLeaseTime > 30_000L || cachedLeasesMap.isEmpty()) {
                    try {
                        val leases = conn.execute("/ip/dhcp-server/lease/print", "=.proplist=address,mac-address,host-name")
                        val newMap = mutableMapOf<String, String>()
                        leases.forEach { l ->
                            val host = l["host-name"] ?: ""
                            if (host.isNotBlank()) {
                                val mac = l["mac-address"]?.uppercase() ?: ""
                                val ip = l["address"] ?: ""
                                if (mac.isNotBlank()) newMap[mac] = host
                                if (ip.isNotBlank()) newMap[ip] = host
                            }
                        }
                        cachedLeasesMap = newMap
                        lastLeaseTime = now
                    } catch (_: Exception) {}
                }

                val res = conn.execute("/ip/hotspot/active/print")
                res.map {
                    val mac = (it["mac-address"] ?: "").uppercase()
                    val ip = it["address"] ?: ""
                    val host = cachedLeasesMap[mac] ?: cachedLeasesMap[ip] ?: ""
                    val bIn = it["bytes-in"]?.toLongOrNull() ?: 0L
                    val bOut = it["bytes-out"]?.toLongOrNull() ?: 0L
                    val usedMb = (bIn + bOut) / (1024.0 * 1024.0)

                    ActiveUser(
                        id = it[".id"] ?: "",
                        server = it["server"] ?: "",
                        user = it["user"] ?: "",
                        address = it["address"] ?: "",
                        macAddress = it["mac-address"] ?: "",
                        uptime = it["uptime"] ?: "",
                        bytesIn = it["bytes-in"] ?: "0",
                        bytesOut = it["bytes-out"] ?: "0",
                        hostName = host,
                        quotaUsedMb = Math.round(usedMb * 10.0) / 10.0,
                        comment = it["comment"] ?: ""
                    )
                }
            } catch (e: Exception) {
                handleApiError(e)
                emptyList()
            }
        }
    }

    suspend fun removeActiveSession(id: String): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val activeList = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,user,address,mac-address")
                val target = activeList.firstOrNull { it[".id"] == id }
                conn.execute("/ip/hotspot/active/remove", ".id=$id")
                if (target != null) {
                    val user = target["user"] ?: ""
                    val ip = target["address"] ?: ""
                    val mac = target["mac-address"] ?: ""
                    // Clear cookie
                    if (user.isNotBlank()) {
                        try {
                            val cookies = conn.execute("/ip/hotspot/cookie/print", "=.proplist=.id,user")
                            cookies.filter { it["user"] == user }.forEach {
                                val cId = it[".id"]
                                if (!cId.isNullOrBlank()) {
                                    try { conn.execute("/ip/hotspot/cookie/remove", ".id=$cId") } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    // Drop host entry so client internet is instantly revoked without toggling Wi-Fi
                    try {
                        val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,address,mac-address")
                        hosts.filter { it["address"] == ip || it["mac-address"] == mac }.forEach {
                            val hId = it[".id"]
                            if (!hId.isNullOrBlank()) {
                                try { conn.execute("/ip/hotspot/host/remove", ".id=$hId") } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun banMacAddress(mac: String): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                try {
                    val active = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,mac-address")
                    active.forEach {
                        if (it["mac-address"].equals(mac, ignoreCase = true)) {
                            val id = it[".id"]
                            if (!id.isNullOrEmpty()) {
                                try { conn.execute("/ip/hotspot/active/remove", ".id=$id") } catch (_: Exception) {}
                            }
                        }
                    }
                    val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,mac-address")
                    hosts.filter { it["mac-address"].equals(mac, ignoreCase = true) }.forEach {
                        val hId = it[".id"]
                        if (!hId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/host/remove", ".id=$hId") } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}

                conn.execute("/ip/hotspot/ip-binding/add", "mac-address=$mac", "type=blocked", "comment=Banned via App")
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun unbanMacAddress(mac: String): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val bindings = conn.execute("/ip/hotspot/ip-binding/print", "=.proplist=.id,mac-address,type")
                bindings.forEach {
                    if (it["mac-address"].equals(mac, ignoreCase = true)) {
                        val id = it[".id"]
                        if (!id.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/ip-binding/remove", "=.id=$id") } catch (_: Exception) {}
                        }
                    }
                }
                try {
                    val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,mac-address")
                    hosts.filter { it["mac-address"].equals(mac, ignoreCase = true) }.forEach {
                        val hId = it[".id"]
                        if (!hId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/host/remove", "=.id=$hId") } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun unbanAllMacAddresses(): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val bindings = conn.execute("/ip/hotspot/ip-binding/print", "=.proplist=.id,type,comment")
                bindings.forEach {
                    val type = it["type"] ?: ""
                    val comment = it["comment"] ?: ""
                    val id = it[".id"]
                    if (type == "blocked" || comment.contains("Banned", ignoreCase = true)) {
                        if (!id.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/ip-binding/remove", "=.id=$id") } catch (_: Exception) {}
                        }
                    }
                }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun getRouterProfiles(): List<RouterProfileInfo> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext emptyList()
                val res = conn.execute("/ip/hotspot/user/profile/print")
                res.map {
                    RouterProfileInfo(
                        id = it[".id"] ?: "",
                        name = it["name"] ?: "",
                        rateLimit = it["rate-limit"] ?: "",
                        sharedUsers = it["shared-users"] ?: "1",
                        onLogin = it["on-login"] ?: ""
                    )
                }
            } catch (e: Exception) {
                handleApiError(e)
                emptyList()
            }
        }
    }

    suspend fun addRouterProfile(
        name: String,
        rateLimit: String,
        sharedUsers: Int = 1,
        sessionTimeout: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val onLoginScript = ":global hsUser \$user; /system script run voucher-activate;"
                val params = mutableListOf(
                    "name=$name",
                    "shared-users=$sharedUsers",
                    "keepalive-timeout=00:02:00",
                    "status-autorefresh=00:01:00",
                    "on-login=$onLoginScript"
                )
                if (sessionTimeout.isNotBlank()) {
                    params.add("session-timeout=$sessionTimeout")
                }
                if (rateLimit.isNotBlank()) {
                    val cleanRate = if (!rateLimit.contains("/")) "$rateLimit/$rateLimit" else rateLimit
                    params.add("rate-limit=$cleanRate")
                }
                conn.execute("/ip/hotspot/user/profile/add", *params.toTypedArray())
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun deleteRouterProfile(profileName: String): Boolean = withContext(Dispatchers.IO) {
        if (profileName.equals("default", ignoreCase = true)) return@withContext false
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val profs = conn.execute("/ip/hotspot/user/profile/print", "=.proplist=.id,name")
                val profId = profs.firstOrNull { it["name"] == profileName }?.get(".id")
                if (!profId.isNullOrBlank()) {
                    conn.execute("/ip/hotspot/user/profile/remove", ".id=$profId")
                }
                cachedHotspotUsers = cachedHotspotUsers.filter { it.profile != profileName }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun updateRouterProfile(
        oldName: String,
        newName: String,
        rateLimit: String,
        sharedUsers: Int = 1,
        sessionTimeout: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val profs = conn.execute("/ip/hotspot/user/profile/print", "=.proplist=.id,name")
                val profId = profs.firstOrNull { it["name"] == oldName }?.get(".id")
                if (profId.isNullOrBlank()) return@withContext false

                val onLoginScript = ":global hsUser \$user; /system script run voucher-activate;"
                val targetName = if (newName.isNotBlank()) newName else oldName
                val params = mutableListOf(
                    ".id=$profId",
                    "name=$targetName",
                    "shared-users=$sharedUsers",
                    "keepalive-timeout=00:02:00",
                    "status-autorefresh=00:01:00",
                    "on-login=$onLoginScript"
                )
                if (sessionTimeout.isNotBlank()) {
                    params.add("session-timeout=$sessionTimeout")
                }
                if (rateLimit.isNotBlank()) {
                    val cleanRate = if (!rateLimit.contains("/")) "$rateLimit/$rateLimit" else rateLimit
                    params.add("rate-limit=$cleanRate")
                }
                conn.execute("/ip/hotspot/user/profile/set", *params.toTypedArray())
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun addHotspotUser(
        name: String,
        password: String,
        profile: String,
        comment: String,
        limitBytesTotal: Long = 0L,
        limitUptime: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val code = name.trim()
                val pass = if (password.isNotBlank()) password.trim() else code
                val cleanComment = comment.replace("\"", "").trim()
                val params = mutableListOf(
                    "name=$code",
                    "password=$pass",
                    "profile=$profile"
                )
                if (cleanComment.isNotBlank()) {
                    params.add("comment=$cleanComment")
                }
                if (limitBytesTotal > 0L) {
                    params.add("limit-bytes-total=$limitBytesTotal")
                }
                if (limitUptime.isNotBlank()) {
                    params.add("limit-uptime=$limitUptime")
                }
                conn.execute("/ip/hotspot/user/add", *params.toTypedArray())
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun addHotspotUsersBatch(vouchers: List<Voucher>): Boolean = withContext(Dispatchers.IO) {
        if (vouchers.isEmpty()) return@withContext true
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                var successCount = 0
                for (v in vouchers) {
                    try {
                        val code = v.code.trim()
                        val pass = if (v.isAccount && v.password.isNotBlank()) v.password.trim() else code
                        val cleanComment = v.comment.replace("\"", "").trim()
                        val limitBytes = if (v.dataLimitMb > 0) v.dataLimitMb.toLong() * 1024L * 1024L else 0L

                        val params = mutableListOf(
                            "name=$code",
                            "password=$pass",
                            "profile=${v.profileName}"
                        )
                        if (cleanComment.isNotBlank()) {
                            params.add("comment=$cleanComment")
                        }
                        if (limitBytes > 0L) {
                            params.add("limit-bytes-total=$limitBytes")
                        }
                        // Enforce strict limit-uptime on RouterOS so client connection is forcibly ended when duration expires!
                        if (v.durationMinutes > 0) {
                            params.add("limit-uptime=${v.durationMinutes}m")
                        } else if (v.validityDays > 0) {
                            params.add("limit-uptime=${v.validityDays}d")
                        }

                        conn.execute("/ip/hotspot/user/add", *params.toTypedArray())
                        successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                successCount > 0
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun deleteHotspotUser(username: String): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val target = username.trim()
                val targetClean = target.replace("-", "").trim()
                var id = cachedHotspotUsers.firstOrNull { it.name == target || it.name == targetClean }?.id
                if (id.isNullOrBlank()) {
                    val res = conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name")
                    id = res.firstOrNull { it["name"] == target || it["name"] == targetClean }?.get(".id")
                }
                if (!id.isNullOrBlank()) {
                    conn.execute("/ip/hotspot/user/remove", ".id=$id")
                }
                // Drop any active session for this user and drop host so connection cuts off immediately!
                try {
                    val activeRes = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,user,address,mac-address")
                    val matching = activeRes.filter { it["user"] == target || it["user"] == targetClean }
                    val ips = matching.mapNotNull { it["address"] }
                    val macs = matching.mapNotNull { it["mac-address"] }
                    matching.forEach {
                        val activeId = it[".id"]
                        if (!activeId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/active/remove", ".id=$activeId") } catch (_: Exception) {}
                        }
                    }
                    val cookieRes = conn.execute("/ip/hotspot/cookie/print", "=.proplist=.id,user")
                    cookieRes.filter { it["user"] == target || it["user"] == targetClean }.forEach {
                        val cId = it[".id"]
                        if (!cId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/cookie/remove", ".id=$cId") } catch (_: Exception) {}
                        }
                    }
                    val hostRes = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,user,address,mac-address")
                    hostRes.filter { it["user"] == target || it["user"] == targetClean || it["address"] in ips || it["mac-address"] in macs }.forEach {
                        val hId = it[".id"]
                        if (!hId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/host/remove", ".id=$hId") } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}

                cachedHotspotUsers = cachedHotspotUsers.filter { it.name != target && it.name != targetClean }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun deleteHotspotUsersBatch(codes: Collection<String>): Boolean = withContext(Dispatchers.IO) {
        if (codes.isEmpty()) return@withContext true
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val allCodes = codes.flatMap {
                    val clean = it.replace("-", "").trim()
                    if (clean != it.trim()) listOf(it.trim(), clean) else listOf(it.trim())
                }.filter { it.isNotBlank() }.toSet()

                if (allCodes.isEmpty()) return@withContext true

                // 1. Fetch only .id and name for all users on router in ONE single fast query (~40ms)
                val allRouterUsers = conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name")
                val matchedIds = allRouterUsers.filter { userMap ->
                    val name = userMap["name"] ?: ""
                    name in allCodes
                }.mapNotNull { it[".id"] }.filter { it.isNotBlank() }

                // 2. Remove in batches of 50 IDs using RouterOS native .id parameter: .id=*A,*B,*C
                for (chunk in matchedIds.chunked(50)) {
                    try {
                        conn.execute("/ip/hotspot/user/remove", ".id=" + chunk.joinToString(","))
                    } catch (_: Exception) {
                        try {
                            conn.execute("/ip/hotspot/user/remove", "numbers=" + chunk.joinToString(","))
                        } catch (_: Exception) {
                            for (id in chunk) {
                                try { conn.execute("/ip/hotspot/user/remove", ".id=$id") } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // 3. Drop active sessions and collect client IPs/MACs
                val activeIps = mutableSetOf<String>()
                val activeMacs = mutableSetOf<String>()
                try {
                    val activeRes = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,user,address,mac-address")
                    val matching = activeRes.filter { it["user"] in allCodes }
                    matching.forEach {
                        it["address"]?.let { ip -> activeIps.add(ip) }
                        it["mac-address"]?.let { mac -> activeMacs.add(mac) }
                    }
                    val activeIds = matching.mapNotNull { it[".id"] }
                    for (chunk in activeIds.chunked(50)) {
                        try {
                            conn.execute("/ip/hotspot/active/remove", ".id=" + chunk.joinToString(","))
                        } catch (_: Exception) {
                            for (id in chunk) {
                                try { conn.execute("/ip/hotspot/active/remove", ".id=$id") } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 4. Drop cookies for deleted users
                try {
                    val cookieRes = conn.execute("/ip/hotspot/cookie/print", "=.proplist=.id,user")
                    val cookieIds = cookieRes.filter { it["user"] in allCodes }.mapNotNull { it[".id"] }
                    for (chunk in cookieIds.chunked(50)) {
                        try {
                            conn.execute("/ip/hotspot/cookie/remove", ".id=" + chunk.joinToString(","))
                        } catch (_: Exception) {
                            for (id in chunk) {
                                try { conn.execute("/ip/hotspot/cookie/remove", ".id=$id") } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 5. Drop host entries immediately to sever active connections without needing to toggle phone Wi-Fi
                try {
                    val hostRes = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,user,address,mac-address")
                    val hostIds = hostRes.filter { it["user"] in allCodes || it["address"] in activeIps || it["mac-address"] in activeMacs }.mapNotNull { it[".id"] }
                    for (chunk in hostIds.chunked(50)) {
                        try {
                            conn.execute("/ip/hotspot/host/remove", ".id=" + chunk.joinToString(","))
                        } catch (_: Exception) {
                            for (id in chunk) {
                                try { conn.execute("/ip/hotspot/host/remove", ".id=$id") } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}

                cachedHotspotUsers = cachedHotspotUsers.filter { it.name !in allCodes }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun getAllHotspotUsers(): List<RouterHotspotUser> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext emptyList()
                val res = conn.execute("/ip/hotspot/user/print")
                val users = res.mapNotNull {
                    val name = it["name"] ?: return@mapNotNull null
                    if (name.isBlank() || name == "default-trial") return@mapNotNull null
                    RouterHotspotUser(
                        id = it[".id"] ?: "",
                        name = name,
                        password = it["password"] ?: "",
                        profile = it["profile"] ?: "default",
                        comment = it["comment"] ?: "",
                        uptime = it["uptime"] ?: "0s",
                        bytesIn = it["bytes-in"]?.toLongOrNull() ?: 0L,
                        bytesOut = it["bytes-out"]?.toLongOrNull() ?: 0L,
                        limitBytesTotal = it["limit-bytes-total"]?.toLongOrNull() ?: 0L,
                        disabled = it["disabled"]?.toBooleanStrictOrNull() ?: false
                    )
                }
                cachedHotspotUsers = users
                users
            } catch (e: Exception) {
                handleApiError(e)
                emptyList()
            }
        }
    }

    suspend fun resetUserCounters(username: String): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val id = cachedHotspotUsers.firstOrNull { it.name == username }?.id
                    ?: conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name")
                        .firstOrNull { it["name"] == username }?.get(".id")
                if (!id.isNullOrBlank()) {
                    conn.execute("/ip/hotspot/user/reset-counters", ".id=$id")
                }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun markUsersAsPrintedOnRouter(codes: List<String>): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val codeSet = codes.toSet()
                val routerUsers = conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name")
                for (u in routerUsers) {
                    val name = u["name"] ?: continue
                    if (name in codeSet) {
                        val id = u[".id"] ?: continue
                        try {
                            conn.execute("/ip/hotspot/user/set", ".id=$id", "comment=PRINTED")
                        } catch (_: Exception) {}
                    }
                }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun renewHotspotUser(username: String): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val id = cachedHotspotUsers.firstOrNull { it.name == username }?.id
                    ?: conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name")
                        .firstOrNull { it["name"] == username }?.get(".id")
                if (!id.isNullOrBlank()) {
                    try { conn.execute("/ip/hotspot/user/reset-counters", ".id=$id") } catch (_: Exception) {}
                    try { conn.execute("/ip/hotspot/user/set", ".id=$id", "comment=") } catch (_: Exception) {}
                }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }

    suspend fun resetAllStatisticsOnRouter(): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val scriptName = "tmp_rst_${System.currentTimeMillis() % 10000}"
                val addRes = conn.execute(
                    "/system/script/add",
                    "name=$scriptName",
                    "source=/interface reset-counters [find]; /ip hotspot user reset-counters [find]"
                )
                val scriptId = addRes.firstOrNull()?.get("ret")
                try {
                    conn.execute("/system/script/run", "number=$scriptName")
                } catch (_: Exception) {}
                if (!scriptId.isNullOrBlank()) {
                    try { conn.execute("/system/script/remove", ".id=$scriptId") } catch (_: Exception) {}
                } else {
                    try {
                        val scripts = conn.execute("/system/script/print", "=.proplist=.id,name")
                        val match = scripts.firstOrNull { it["name"] == scriptName }?.get(".id")
                        if (!match.isNullOrBlank()) conn.execute("/system/script/remove", ".id=$match")
                    } catch (_: Exception) {}
                }
                true
            } catch (e: Exception) {
                handleApiError(e)
                false
            }
        }
    }
}
