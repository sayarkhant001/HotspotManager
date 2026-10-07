package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class CloudUser(
    val id: Int,
    val username: String,
    val role: String,
    val fullName: String
)

data class CloudRouter(
    val id: Int,
    val name: String,
    val wgIp: String,
    val remotePort: Int,
    val remoteAddress: String,
    val apiUser: String,
    val apiPass: String,
    val status: String
)

object CloudApiClient {
    // VPS Cloud API URL via Cloudflare Pages
    var serverUrl: String = "https://hotspot-admin.pages.dev"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val PREFS_NAME = "hotspot_cloud_prefs"
    private const val KEY_TOKEN = "cloud_token"
    private const val KEY_USERNAME = "cloud_username"
    private const val KEY_ROLE = "cloud_role"
    private const val KEY_FULL_NAME = "cloud_full_name"
    private const val KEY_SERVER_URL = "cloud_server_url"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(KEY_SERVER_URL, "https://hotspot-admin.pages.dev") ?: "https://hotspot-admin.pages.dev"
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getSavedUser(context: Context): CloudUser? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val u = prefs.getString(KEY_USERNAME, null) ?: return null
        val r = prefs.getString(KEY_ROLE, "customer") ?: "customer"
        val fn = prefs.getString(KEY_FULL_NAME, u) ?: u
        return CloudUser(id = 0, username = u, role = r, fullName = fn)
    }

    fun saveAuth(context: Context, token: String, user: CloudUser) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_ROLE, user.role)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_SERVER_URL, serverUrl)
            .apply()
    }

    fun clearAuth(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .remove(KEY_FULL_NAME)
            .apply()
    }

    suspend fun login(username: String, password: String): Result<Pair<String, CloudUser>> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/api/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = try { JSONObject(respStr).optString("detail", "Login failed") } catch (_: Exception) { "Login failed (${resp.code})" }
                    return@withContext Result.failure(Exception(detail))
                }
                val obj = JSONObject(respStr)
                val token = obj.getString("token")
                val uObj = obj.getJSONObject("user")
                val user = CloudUser(
                    id = uObj.getInt("id"),
                    username = uObj.getString("username"),
                    role = uObj.getString("role"),
                    fullName = uObj.optString("full_name", uObj.getString("username"))
                )
                Result.success(token to user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCustomerRouters(context: Context): Result<List<CloudRouter>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context) ?: return@withContext Result.failure(Exception("Not logged into cloud"))
            val request = Request.Builder()
                .url("$serverUrl/api/routers")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch routers (${resp.code})"))
                }
                val arr = JSONArray(respStr)
                val list = mutableListOf<CloudRouter>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val rId = o.getInt("id")
                    val rPort = o.optInt("remote_port", 8728 + rId)
                    list.add(
                        CloudRouter(
                            id = rId,
                            name = o.getString("name"),
                            wgIp = o.getString("wg_ip"),
                            remotePort = rPort,
                            remoteAddress = o.optString("remote_address", "3.84.81.152:$rPort"),
                            apiUser = o.optString("api_user", "admin"),
                            apiPass = o.optString("api_pass", "Khant1234@"),
                            status = o.optString("status", "offline")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerRouter(context: Context, name: String, location: String = ""): Result<Pair<CloudRouter, String>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context) ?: return@withContext Result.failure(Exception("Not logged into cloud"))
            val json = JSONObject().apply {
                put("name", name)
                put("location", location)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/api/routers")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = try { JSONObject(respStr).optString("detail", "Error creating router") } catch (_: Exception) { "Error ($resp.code)" }
                    return@withContext Result.failure(Exception(detail))
                }
                val o = JSONObject(respStr)
                val rId = o.getInt("router_id")
                val wgIp = o.getString("wg_ip")
                val script = o.getString("setup_script")
                val rPort = o.optInt("remote_port", 8728 + rId)
                val rAddr = o.optString("remote_address", "3.84.81.152:$rPort")
                val cr = CloudRouter(
                    id = rId,
                    name = name,
                    wgIp = wgIp,
                    remotePort = rPort,
                    remoteAddress = rAddr,
                    apiUser = "admin",
                    apiPass = "Khant1234@",
                    status = "offline"
                )
                Result.success(cr to script)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRouterScript(context: Context, routerId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context) ?: return@withContext Result.failure(Exception("Not logged into cloud"))
            val request = Request.Builder()
                .url("$serverUrl/api/routers/$routerId/script")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to get script (${resp.code})"))
                }
                val script = JSONObject(respStr).getString("script")
                Result.success(script)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper: Checks if local router 10.10.10.1:8728 is reachable on current Wi-Fi in < 1 second
    suspend fun isLocalRouterReachable(ip: String = "10.10.10.1", port: Int = 8728): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 1200)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
