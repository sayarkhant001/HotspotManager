package com.example.data.remote

import com.example.domain.models.ActiveUser
import com.example.domain.models.AccessPointDevice
import com.example.domain.models.IpBinding
import com.example.domain.models.NetworkTopologyData
import com.example.domain.models.Voucher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class RouterApiTrapException(val errorMessage: String) : Exception(errorMessage)

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

    fun connect(host: String, port: Int = 8728, timeoutMs: Int = 15000) {
        close()
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = timeoutMs
        s.connect(InetSocketAddress(host, port), 6000)
        socket = s
        inStream = BufferedInputStream(s.getInputStream(), 32768)
        outStream = BufferedOutputStream(s.getOutputStream(), 16384)
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
                        val rest = readWord(inS) ?: throw IllegalStateException("Router closed connection during trap")
                        if (rest.isEmpty()) break
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
                                    val doneRest = readWord(inS) ?: break
                                    if (doneRest.isEmpty()) break
                                }
                                break
                            }
                            if (nextWord.isEmpty()) continue
                        }
                    } catch (drainErr: Exception) {
                        close()
                        throw drainErr
                    }
                    throw RouterApiTrapException(errorMsg)
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

    private data class ApTrafficSample(
        val rxBytes: Long,
        val txBytes: Long,
        val timestamp: Long
    )
    private val apTrafficHistory = java.util.concurrent.ConcurrentHashMap<String, ApTrafficSample>()

    suspend fun connect(ip: String, user: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                lastIp = ip
                lastUser = user
                lastPass = pass
                connection?.close()
                val conn = RawRouterOSConnection()
                conn.connect(ip, 8728, 15000)
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

    suspend fun getHotspotServerDnsName(): String? = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext null
                val res = conn.execute("/ip/hotspot/profile/print")
                for (row in res) {
                    val dns = row["dns-name"]?.trim() ?: ""
                    if (dns.isNotBlank()) return@withContext dns
                }
                for (row in res) {
                    val addr = row["hotspot-address"]?.trim() ?: ""
                    if (addr.isNotBlank()) return@withContext addr
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getRouterSsid(): String? = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext null
                // Try v7 wifiwave2 / wifi interface first
                try {
                    val wList = conn.execute("/interface/wifi/print")
                    for (row in wList) {
                        val ssid = row["configuration.ssid"] ?: row["ssid"] ?: ""
                        if (ssid.isNotBlank()) return@withContext ssid
                    }
                } catch (_: Exception) {}
                // Fallback to legacy wireless interface
                try {
                    val wList = conn.execute("/interface/wireless/print")
                    for (row in wList) {
                        val ssid = row["ssid"] ?: ""
                        if (ssid.isNotBlank()) return@withContext ssid
                    }
                } catch (_: Exception) {}
                null
            } catch (e: Exception) {
                null
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
                conn.connect(lastIp, 8728, 15000)
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
        if (e is RouterApiTrapException) {
            // Logical rejection from RouterOS (e.g. duplicate user/profile).
            // Socket stream is intact and synchronized. Do NOT destroy the connection!
            return
        }
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
                val cleanMac = mac.trim().uppercase()

                // 1. Collect client IPs from active sessions & hosts to purge open connections
                val clientIps = mutableSetOf<String>()

                // 2. Remove from active hotspot sessions immediately
                try {
                    val active = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,mac-address,address,user")
                    active.forEach {
                        if (it["mac-address"].equals(cleanMac, ignoreCase = true)) {
                            it["address"]?.let { ip -> if (ip.isNotBlank()) clientIps.add(ip) }
                            val id = it[".id"]
                            if (!id.isNullOrBlank()) {
                                try { conn.execute("/ip/hotspot/active/remove", ".id=$id") } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 3. Remove from host ARP/DHCP tracking table immediately
                try {
                    val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,mac-address,address")
                    hosts.filter { it["mac-address"].equals(cleanMac, ignoreCase = true) }.forEach {
                        it["address"]?.let { ip -> if (ip.isNotBlank()) clientIps.add(ip) }
                        val hId = it[".id"]
                        if (!hId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/host/remove", ".id=$hId") } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}

                // 4. Remove any login cookies to prevent instant re-login
                try {
                    val cookies = conn.execute("/ip/hotspot/cookie/print", "=.proplist=.id,mac-address")
                    cookies.filter { it["mac-address"].equals(cleanMac, ignoreCase = true) }.forEach {
                        val cId = it[".id"]
                        if (!cId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/cookie/remove", ".id=$cId") } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}

                // 5. Instantly kill any active TCP/UDP connection tracking sessions for this client
                try {
                    if (clientIps.isNotEmpty()) {
                        val conns = conn.execute("/ip/firewall/connection/print", "=.proplist=.id,src-address")
                        clientIps.forEach { clientIp ->
                            conns.filter { it["src-address"]?.startsWith(clientIp) == true }.forEach {
                                val cId = it[".id"]
                                if (!cId.isNullOrBlank()) {
                                    try { conn.execute("/ip/firewall/connection/remove", ".id=$cId") } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 6. Check existing IP bindings - either set or add type=blocked
                val bindings = conn.execute("/ip/hotspot/ip-binding/print", "=.proplist=.id,mac-address")
                val matched = bindings.firstOrNull { it["mac-address"].equals(cleanMac, ignoreCase = true) }
                if (matched != null) {
                    val bId = matched[".id"]
                    conn.execute("/ip/hotspot/ip-binding/set", ".id=$bId", "type=blocked", "comment=Banned via App")
                } else {
                    conn.execute("/ip/hotspot/ip-binding/add", "mac-address=$cleanMac", "type=blocked", "comment=Banned via App")
                }
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
                val cleanMac = mac.trim().uppercase()
                val bindings = conn.execute("/ip/hotspot/ip-binding/print", "=.proplist=.id,mac-address,type")
                bindings.forEach {
                    if (it["mac-address"].equals(cleanMac, ignoreCase = true)) {
                        val id = it[".id"]
                        if (!id.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/ip-binding/remove", ".id=$id") } catch (_: Exception) {}
                        }
                    }
                }
                try {
                    val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,mac-address")
                    hosts.filter { it["mac-address"].equals(cleanMac, ignoreCase = true) }.forEach {
                        val hId = it[".id"]
                        if (!hId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/host/remove", ".id=$hId") } catch (_: Exception) {}
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

    suspend fun getIpBindings(): List<IpBinding> = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext emptyList()
                val res = conn.execute("/ip/hotspot/ip-binding/print")
                res.map {
                    IpBinding(
                        id = it[".id"] ?: "",
                        macAddress = (it["mac-address"] ?: "").uppercase(),
                        address = it["address"] ?: "",
                        toAddress = it["to-address"] ?: "",
                        type = it["type"] ?: "regular",
                        comment = it["comment"] ?: "",
                        disabled = it["disabled"] == "true"
                    )
                }
            } catch (e: Exception) {
                handleApiError(e)
                emptyList()
            }
        }
    }

    suspend fun whitelistDevice(mac: String, ip: String = "", comment: String = "Whitelisted Device"): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val cleanMac = mac.trim().uppercase()
                if (cleanMac.isBlank()) return@withContext false

                val bindings = conn.execute("/ip/hotspot/ip-binding/print", "=.proplist=.id,mac-address")
                val matched = bindings.firstOrNull { it["mac-address"].equals(cleanMac, ignoreCase = true) }

                val params = mutableListOf<String>()
                if (matched != null) {
                    val bId = matched[".id"]
                    params.add(".id=$bId")
                    params.add("type=bypassed")
                    params.add("comment=$comment")
                    if (ip.isNotBlank()) params.add("address=$ip")
                    params.add("disabled=no")
                    conn.execute("/ip/hotspot/ip-binding/set", *params.toTypedArray())
                } else {
                    params.add("mac-address=$cleanMac")
                    params.add("type=bypassed")
                    params.add("comment=$comment")
                    if (ip.isNotBlank()) params.add("address=$ip")
                    conn.execute("/ip/hotspot/ip-binding/add", *params.toTypedArray())
                }

                // Drop hotspot host entry so the client or AP immediately gains bypassed state without Wi-Fi toggle
                try {
                    val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,mac-address")
                    hosts.filter { it["mac-address"].equals(cleanMac, ignoreCase = true) }.forEach {
                        val hId = it[".id"]
                        if (!hId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/host/remove", ".id=$hId") } catch (_: Exception) {}
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

    suspend fun removeIpBinding(id: String, mac: String = ""): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                if (id.isNotBlank()) {
                    conn.execute("/ip/hotspot/ip-binding/remove", ".id=$id")
                } else if (mac.isNotBlank()) {
                    val cleanMac = mac.trim().uppercase()
                    val bindings = conn.execute("/ip/hotspot/ip-binding/print", "=.proplist=.id,mac-address")
                    bindings.filter { it["mac-address"].equals(cleanMac, ignoreCase = true) }.forEach {
                        val bId = it[".id"]
                        if (!bId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/ip-binding/remove", ".id=$bId") } catch (_: Exception) {}
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

    suspend fun getNetworkTopology(): NetworkTopologyData = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            val defaultRouter = "RB4011iGS+"
            val defaultManageIp = if (lastIp.isNotBlank()) lastIp else "192.168.88.1"
            var hotspotIp = "192.168.100.1"

            val conn = ensureConnectedInternal() ?: return@withContext NetworkTopologyData(
                routerModel = defaultRouter,
                manageIp = defaultManageIp,
                hotspotIp = hotspotIp,
                accessPoints = emptyList()
            )

            try {
                var detectedBoardName = defaultRouter
                try {
                    val res = conn.execute("/system/resource/print")
                    if (res.isNotEmpty()) {
                        detectedBoardName = res[0]["board-name"] ?: res[0]["platform"] ?: defaultRouter
                    }
                } catch (_: Exception) {}

                var detectedManageIp = defaultManageIp
                try {
                    val addresses = conn.execute("/ip/address/print")
                    for (a in addresses) {
                        val iface = a["interface"] ?: ""
                        val addr = (a["address"] ?: "").substringBefore("/")
                        if (addr.isNotBlank()) {
                            if (iface.contains("hotspot", ignoreCase = true) || iface.contains("bridge", ignoreCase = true)) {
                                hotspotIp = addr
                            } else if (iface.contains("ether", ignoreCase = true) && detectedManageIp == defaultManageIp) {
                                detectedManageIp = addr
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Get IP Bindings (to check whitelisted status)
                val bindingsMap = mutableMapOf<String, IpBinding>()
                try {
                    val bList = conn.execute("/ip/hotspot/ip-binding/print")
                    bList.forEach {
                        val mac = (it["mac-address"] ?: "").uppercase()
                        if (mac.isNotBlank()) {
                            bindingsMap[mac] = IpBinding(
                                id = it[".id"] ?: "",
                                macAddress = mac,
                                address = it["address"] ?: "",
                                toAddress = it["to-address"] ?: "",
                                type = it["type"] ?: "regular",
                                comment = it["comment"] ?: "",
                                disabled = it["disabled"] == "true"
                            )
                        }
                    }
                } catch (_: Exception) {}

                // Query bridge hosts to map MACs to on-interface
                val macToInterface = mutableMapOf<String, String>()
                val interfaceToMacs = mutableMapOf<String, MutableList<String>>()
                try {
                    val bHosts = conn.execute("/interface/bridge/host/print", "=.proplist=mac-address,on-interface")
                    bHosts.forEach { bh ->
                        val m = (bh["mac-address"] ?: "").uppercase()
                        val iface = bh["on-interface"] ?: ""
                        if (m.isNotBlank() && iface.isNotBlank()) {
                            macToInterface[m] = iface
                            interfaceToMacs.getOrPut(iface) { mutableListOf() }.add(m)
                        }
                    }
                } catch (_: Exception) {}

                // Query interface byte counters for throughput
                val ifaceBytesMap = mutableMapOf<String, Pair<Long, Long>>()
                try {
                    val ifaces = conn.execute("/interface/print", "=.proplist=name,rx-byte,tx-byte")
                    ifaces.forEach { iface ->
                        val name = iface["name"] ?: ""
                        val rx = iface["rx-byte"]?.toLongOrNull() ?: 0L
                        val tx = iface["tx-byte"]?.toLongOrNull() ?: 0L
                        if (name.isNotBlank()) {
                            ifaceBytesMap[name] = Pair(rx, tx)
                        }
                    }
                } catch (_: Exception) {}

                fun createApDevice(
                    mac: String,
                    displayName: String,
                    modelName: String,
                    ip: String,
                    isWhitelisted: Boolean,
                    isOnline: Boolean,
                    bindingId: String?,
                    brand: String,
                    interfaceHint: String = ""
                ): AccessPointDevice {
                    val apIface = interfaceHint.ifBlank { macToInterface[mac] ?: "" }
                    val connectedMacs = if (apIface.isNotBlank()) {
                        interfaceToMacs[apIface]?.filter { it != mac } ?: emptyList()
                    } else emptyList()

                    val (rxRaw, txRaw) = if (apIface.isNotBlank()) {
                        ifaceBytesMap[apIface] ?: Pair(0L, 0L)
                    } else Pair(0L, 0L)

                    val now = System.currentTimeMillis()
                    val prev = apTrafficHistory[mac]
                    var rxBps = 0L
                    var txBps = 0L
                    if (prev != null && now > prev.timestamp) {
                        val dt = (now - prev.timestamp) / 1000.0
                        if (dt >= 0.5) {
                            val dRx = if (rxRaw >= prev.rxBytes) rxRaw - prev.rxBytes else 0L
                            val dTx = if (txRaw >= prev.txBytes) txRaw - prev.txBytes else 0L
                            rxBps = (dRx * 8.0 / dt).toLong()
                            txBps = (dTx * 8.0 / dt).toLong()
                        }
                    }
                    apTrafficHistory[mac] = ApTrafficSample(rxRaw, txRaw, now)

                    return AccessPointDevice(
                        name = displayName,
                        model = modelName,
                        ipAddress = ip,
                        macAddress = mac,
                        isWhitelisted = isWhitelisted,
                        isOnline = isOnline,
                        bindingId = bindingId,
                        vendor = brand,
                        interfaceName = apIface,
                        connectedClientMacs = connectedMacs,
                        currentRxBps = rxBps,
                        currentTxBps = txBps,
                        dailyBytesIn = rxRaw,
                        dailyBytesOut = txRaw
                    )
                }

                val apMap = mutableMapOf<String, AccessPointDevice>()

                // 1. Check DHCP leases for Ruijie / Reyee and TP-Link APs & Bridges
                try {
                    val leases = conn.execute("/ip/dhcp-server/lease/print")
                    leases.forEach { l ->
                        val host = l["host-name"] ?: ""
                        val mac = (l["mac-address"] ?: "").uppercase()
                        val ip = l["address"] ?: ""
                        val status = l["status"] ?: ""
                        val comment = l["comment"] ?: ""

                        val detected = com.example.utils.DeviceModelDetector.detectApOrClient(
                            name = host.ifBlank { comment },
                            hostName = host,
                            comment = comment,
                            macAddress = mac
                        )

                        if (detected.isApOrBridge && mac.isNotBlank()) {
                            val binding = bindingsMap[mac]
                            val isWhitelisted = binding?.type == "bypassed" && !binding.disabled
                            val isOnline = status == "bound" || status == "active" || status.isNotBlank()

                            val displayName = when {
                                host.isNotBlank() -> host
                                comment.isNotBlank() -> comment
                                else -> "${detected.brand}-${mac.takeLast(5).replace(":", "")}"
                            }

                            apMap[mac] = createApDevice(
                                mac = mac,
                                displayName = displayName,
                                modelName = detected.modelName,
                                ip = ip,
                                isWhitelisted = isWhitelisted,
                                isOnline = isOnline,
                                bindingId = binding?.id,
                                brand = detected.brand
                            )
                        }
                    }
                } catch (_: Exception) {}

                // 2. Check CDP / LLDP / MNDP neighbors
                try {
                    val neighbors = conn.execute("/ip/neighbor/print")
                    neighbors.forEach { n ->
                        val identity = n["identity"] ?: ""
                        val platform = n["platform"] ?: ""
                        val mac = (n["mac-address"] ?: "").uppercase()
                        val ip = n["address"] ?: ""
                        val ifaceHint = n["interface"] ?: ""

                        val detected = com.example.utils.DeviceModelDetector.detectApOrClient(
                            name = identity,
                            hostName = identity,
                            comment = platform,
                            macAddress = mac
                        )

                        if (detected.isApOrBridge && mac.isNotBlank()) {
                            val binding = bindingsMap[mac]
                            val isWhitelisted = binding?.type == "bypassed" && !binding.disabled
                            val displayName = identity.ifBlank { "${detected.brand}-${mac.takeLast(5)}" }

                            apMap[mac] = createApDevice(
                                mac = mac,
                                displayName = displayName,
                                modelName = detected.modelName,
                                ip = ip.ifBlank { apMap[mac]?.ipAddress ?: "" },
                                isWhitelisted = isWhitelisted,
                                isOnline = true,
                                bindingId = binding?.id,
                                brand = detected.brand,
                                interfaceHint = ifaceHint
                            )
                        }
                    }
                } catch (_: Exception) {}

                // 3. Check Hotspot hosts for any active AP or bridge
                try {
                    val hosts = conn.execute("/ip/hotspot/host/print", "=.proplist=.id,mac-address,address,comment")
                    hosts.forEach { h ->
                        val mac = (h["mac-address"] ?: "").uppercase()
                        val ip = h["address"] ?: ""
                        val comment = h["comment"] ?: ""
                        if (mac.isNotBlank() && !apMap.containsKey(mac)) {
                            val detected = com.example.utils.DeviceModelDetector.detectApOrClient(
                                name = comment,
                                hostName = "",
                                comment = comment,
                                macAddress = mac
                            )
                            if (detected.isApOrBridge) {
                                val binding = bindingsMap[mac]
                                val isWhitelisted = binding?.type == "bypassed" && !binding.disabled
                                apMap[mac] = createApDevice(
                                    mac = mac,
                                    displayName = comment.ifBlank { "${detected.brand}-${mac.takeLast(5).replace(":", "")}" },
                                    modelName = detected.modelName,
                                    ip = ip,
                                    isWhitelisted = isWhitelisted,
                                    isOnline = true,
                                    bindingId = binding?.id,
                                    brand = detected.brand
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 4. Check existing IP bindings marked as AP
                bindingsMap.values.forEach { b ->
                    val c = b.comment.lowercase()
                    if ((c.contains("ap") || c.contains("ruijie") || c.contains("reyee") || c.contains("est") || c.contains("tp-link") || c.contains("tplink") || c.contains("cpe")) && !apMap.containsKey(b.macAddress)) {
                        val detected = com.example.utils.DeviceModelDetector.detectApOrClient(
                            name = b.comment,
                            hostName = "",
                            comment = b.comment,
                            macAddress = b.macAddress
                        )
                        apMap[b.macAddress] = createApDevice(
                            mac = b.macAddress,
                            displayName = b.comment.ifBlank { "${detected.brand}-${b.macAddress.takeLast(5)}" },
                            modelName = detected.modelName,
                            ip = b.address,
                            isWhitelisted = b.type == "bypassed" && !b.disabled,
                            isOnline = true,
                            bindingId = b.id,
                            brand = detected.brand
                        )
                    }
                }

                NetworkTopologyData(
                    routerModel = detectedBoardName,
                    manageIp = detectedManageIp,
                    hotspotIp = hotspotIp,
                    accessPoints = apMap.values.toList()
                )
            } catch (e: Exception) {
                handleApiError(e)
                NetworkTopologyData(
                    routerModel = defaultRouter,
                    manageIp = defaultManageIp,
                    hotspotIp = hotspotIp,
                    accessPoints = emptyList()
                )
            }
        }
    }

    suspend fun setRouterAdvanceMode(): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                try {
                    conn.execute("/system/device-mode/update", "mode=enterprise")
                } catch (_: Exception) {
                    try {
                        conn.execute("/system/device-mode/update", "mode=advanced")
                    } catch (_: Exception) {}
                }
                try {
                    conn.execute(
                        "/system/device-mode/update",
                        "hotspot=yes",
                        "scheduler=yes",
                        "fetch=yes",
                        "romon=yes",
                        "traffic-flow=yes",
                        "bandwidth-test=yes"
                    )
                } catch (_: Exception) {}
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
                val params = mutableListOf(
                    "shared-users=$sharedUsers",
                    "keepalive-timeout=none",
                    "status-autorefresh=00:01:00",
                    "on-login="
                )
                if (sessionTimeout.isNotBlank()) {
                    params.add("session-timeout=$sessionTimeout")
                }
                if (rateLimit.isNotBlank()) {
                    val cleanRate = if (!rateLimit.contains("/")) "$rateLimit/$rateLimit" else rateLimit
                    params.add("rate-limit=$cleanRate")
                }
                val profs = conn.execute("/ip/hotspot/user/profile/print", "=.proplist=.id,name")
                val existing = profs.firstOrNull { it["name"].equals(name, ignoreCase = true) }
                if (existing != null) {
                    val profId = existing[".id"]
                    if (!profId.isNullOrBlank()) {
                        conn.execute("/ip/hotspot/user/profile/set", ".id=$profId", "name=$name", *params.toTypedArray())
                    }
                } else {
                    conn.execute("/ip/hotspot/user/profile/add", "name=$name", *params.toTypedArray())
                }
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
                if (profId.isNullOrBlank()) {
                    return@withContext true
                }
                // First reassign any users using this profile to default so RouterOS does not reject with 'profile is in use by user'
                try {
                    val usersWithProfile = conn.execute("/ip/hotspot/user/print", "=.proplist=.id", "?profile=$profileName")
                    for (u in usersWithProfile) {
                        val uid = u[".id"]
                        if (!uid.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/user/set", ".id=$uid", "profile=default") } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}

                conn.execute("/ip/hotspot/user/profile/remove", ".id=$profId")
                cachedHotspotUsers = cachedHotspotUsers.map {
                    if (it.profile == profileName) it.copy(profile = "default") else it
                }
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
        sessionTimeout: String = "",
        limitBytesTotal: Long = 0L,
        limitUptime: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        apiMutex.withLock {
            try {
                val conn = ensureConnectedInternal() ?: return@withContext false
                val profs = conn.execute("/ip/hotspot/user/profile/print", "=.proplist=.id,name")
                val profId = profs.firstOrNull { it["name"] == oldName }?.get(".id")
                if (profId.isNullOrBlank()) return@withContext false

                val targetName = if (newName.isNotBlank()) newName else oldName
                val cleanRate = if (rateLimit.isNotBlank()) {
                    if (!rateLimit.contains("/")) "$rateLimit/$rateLimit" else rateLimit
                } else ""

                val params = mutableListOf(
                    ".id=$profId",
                    "name=$targetName",
                    "shared-users=$sharedUsers",
                    "keepalive-timeout=none",
                    "idle-timeout=none",
                    "status-autorefresh=00:01:00",
                    "on-login="
                )
                if (sessionTimeout.isNotBlank()) {
                    params.add("session-timeout=$sessionTimeout")
                }
                if (cleanRate.isNotBlank()) {
                    params.add("rate-limit=$cleanRate")
                }
                conn.execute("/ip/hotspot/user/profile/set", *params.toTypedArray())

                // 2. Query all users belonging to this profile (oldName) to align them
                val allUsers = conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name,profile")
                val matchingUsers = allUsers.filter { it["profile"] == oldName }
                val matchingUserNames = matchingUsers.mapNotNull { it["name"] }.toSet()

                // 3. Align remaining users in RouterOS
                val userUpdates = mutableListOf<String>()
                if (targetName != oldName) {
                    userUpdates.add("profile=$targetName")
                }
                userUpdates.add(if (limitBytesTotal > 0L) "limit-bytes-total=$limitBytesTotal" else "limit-bytes-total=0")
                userUpdates.add(if (limitUptime.isNotBlank()) "limit-uptime=$limitUptime" else "limit-uptime=0s")

                if (userUpdates.isNotEmpty() && matchingUsers.isNotEmpty()) {
                    for (u in matchingUsers) {
                        val uid = u[".id"]
                        if (!uid.isNullOrBlank()) {
                            try {
                                conn.execute("/ip/hotspot/user/set", ".id=$uid", *userUpdates.toTypedArray())
                            } catch (_: Exception) {}
                        }
                    }
                }

                // 4. Align ACTIVE using users currently connected
                try {
                    val activeRes = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,user,address")
                    val activeMatching = activeRes.filter { it["user"] in matchingUserNames }

                    // If rateLimit changed, update simple queues for active users immediately
                    if (cleanRate.isNotBlank()) {
                        try {
                            val queues = conn.execute("/queue/simple/print", "=.proplist=.id,name,target")
                            activeMatching.forEach { active ->
                                val u = active["user"] ?: ""
                                val ip = active["address"] ?: ""
                                val q = queues.firstOrNull { 
                                    (u.isNotBlank() && it["name"]?.contains(u) == true) || 
                                    (ip.isNotBlank() && it["target"]?.contains(ip) == true) 
                                }
                                q?.get(".id")?.let { qId ->
                                    try {
                                        conn.execute("/queue/simple/set", ".id=$qId", "max-limit=$cleanRate")
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Refresh active sessions so client devices immediately pick up new profile, limits & queue
                    val activeIds = activeMatching.mapNotNull { it[".id"] }
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

                // Update cached users in memory
                cachedHotspotUsers = cachedHotspotUsers.map { u ->
                    if (u.profile == oldName) {
                        u.copy(
                            profile = targetName,
                            limitBytesTotal = if (limitBytesTotal > 0L) limitBytesTotal else u.limitBytesTotal
                        )
                    } else u
                }

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
                val targetProfile = if (profile.isNotBlank()) profile else "default"
                val params = mutableListOf(
                    "name=$code",
                    "password=$pass",
                    "profile=$targetProfile"
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
                try {
                    conn.execute("/ip/hotspot/user/add", *params.toTypedArray())
                } catch (e: Exception) {
                    if (e.message?.contains("already have", ignoreCase = true) == true) {
                        val existing = conn.execute("/ip/hotspot/user/print", "=.proplist=.id", "?name=$code")
                        val id = existing.firstOrNull()?.get(".id")
                        if (!id.isNullOrBlank()) {
                            conn.execute("/ip/hotspot/user/set", ".id=$id", *params.toTypedArray())
                        }
                    } else if (e.message?.contains("profile", ignoreCase = true) == true && targetProfile != "default") {
                        val fallbackParams = params.map { if (it.startsWith("profile=")) "profile=default" else it }
                        conn.execute("/ip/hotspot/user/add", *fallbackParams.toTypedArray())
                    } else {
                        throw e
                    }
                }
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
                        val targetProfile = if (v.profileName.isNotBlank()) v.profileName else "default"

                        val params = mutableListOf(
                            "name=$code",
                            "password=$pass",
                            "profile=$targetProfile"
                        )
                        if (cleanComment.isNotBlank()) {
                            params.add("comment=$cleanComment")
                        }
                        if (limitBytes > 0L) {
                            params.add("limit-bytes-total=$limitBytes")
                        }
                        // Enforce strict limit-uptime on RouterOS so client connection is forcibly ended when duration expires!
                        val uptimeStr = formatMikrotikUptime(v.durationMinutes, v.validityDays)
                        if (uptimeStr.isNotBlank()) {
                            params.add("limit-uptime=$uptimeStr")
                        }

                        try {
                            conn.execute("/ip/hotspot/user/add", *params.toTypedArray())
                            successCount++
                        } catch (e: Exception) {
                            if (e.message?.contains("already have", ignoreCase = true) == true) {
                                successCount++
                            } else if (e.message?.contains("profile", ignoreCase = true) == true && targetProfile != "default") {
                                val fallbackParams = params.map { if (it.startsWith("profile=")) "profile=default" else it }
                                conn.execute("/ip/hotspot/user/add", *fallbackParams.toTypedArray())
                                successCount++
                            } else {
                                e.printStackTrace()
                            }
                        }
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

                // Fast targeted query (?name=...) in 1ms instead of loading all 4,500+ users
                var id: String? = null
                val userRes = conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name", "?name=$target")
                id = userRes.firstOrNull()?.get(".id")
                if (id.isNullOrBlank() && targetClean != target) {
                    val cleanRes = conn.execute("/ip/hotspot/user/print", "=.proplist=.id,name", "?name=$targetClean")
                    id = cleanRes.firstOrNull()?.get(".id")
                }
                if (id.isNullOrBlank()) {
                    id = cachedHotspotUsers.firstOrNull { it.name == target || it.name == targetClean }?.id
                }

                if (!id.isNullOrBlank()) {
                    conn.execute("/ip/hotspot/user/remove", ".id=$id")
                }

                // Drop any active session for this user and drop host so connection cuts off immediately!
                try {
                    val activeRes = conn.execute("/ip/hotspot/active/print", "=.proplist=.id,address,mac-address", "?user=$target")
                    val ips = activeRes.mapNotNull { it["address"] }
                    val macs = activeRes.mapNotNull { it["mac-address"] }
                    activeRes.forEach {
                        val activeId = it[".id"]
                        if (!activeId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/active/remove", ".id=$activeId") } catch (_: Exception) {}
                        }
                    }
                    val cookieRes = conn.execute("/ip/hotspot/cookie/print", "=.proplist=.id", "?user=$target")
                    cookieRes.forEach {
                        val cId = it[".id"]
                        if (!cId.isNullOrBlank()) {
                            try { conn.execute("/ip/hotspot/cookie/remove", ".id=$cId") } catch (_: Exception) {}
                        }
                    }
                    for (ip in ips) {
                        try {
                            val hostRes = conn.execute("/ip/hotspot/host/print", "=.proplist=.id", "?address=$ip")
                            hostRes.forEach {
                                val hId = it[".id"]
                                if (!hId.isNullOrBlank()) conn.execute("/ip/hotspot/host/remove", ".id=$hId")
                            }
                        } catch (_: Exception) {}
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
                val res = conn.execute(
                    "/ip/hotspot/user/print",
                    "=.proplist=.id,name,password,profile,limit-bytes-total,uptime,bytes-in,bytes-out,comment,disabled"
                )
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

    companion object {
        /**
         * Formats uptime / session-timeout string for MikroTik RouterOS.
         * RouterOS accepts formats like: 15m, 30m, 1h, 2h, 1d, 7d.
         */
        fun formatMikrotikUptime(durationMinutes: Int, validityDays: Int): String {
            return when {
                durationMinutes in 1..59 -> "${durationMinutes}m"
                durationMinutes in 60..1439 && durationMinutes % 60 == 0 -> "${durationMinutes / 60}h"
                durationMinutes in 60..1439 -> "${durationMinutes}m"
                durationMinutes >= 1440 && durationMinutes % 1440 == 0 -> "${durationMinutes / 1440}d"
                durationMinutes >= 1440 -> "${durationMinutes}m"
                validityDays > 0 -> "${validityDays}d"
                else -> ""
            }
        }
    }
}
