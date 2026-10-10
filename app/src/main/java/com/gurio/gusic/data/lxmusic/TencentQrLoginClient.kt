package com.gurio.gusic.data.lxmusic

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Tencent QQ/WeChat QR login, independent of the optional account-write extension. */
internal class TencentQrLoginClient(client: OkHttpClient) {
    private val redirectClient = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val longPollClient = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val sessions = ConcurrentHashMap<String, Session>()

    suspend fun create(type: LxTxQrType): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        try {
            purgeExpiredSessions()
            when (type) {
                LxTxQrType.QQ -> createQq()
                LxTxQrType.WECHAT -> createWeChat()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun poll(type: LxTxQrType, sessionId: String): LxTxQrPoll = withContext(Dispatchers.IO) {
        val session = sessions[sessionId] ?: return@withContext errorResult()
        if (session.type != type) return@withContext errorResult()
        if (session.expiresAt < System.currentTimeMillis()) {
            sessions.remove(sessionId, session)
            return@withContext LxTxQrPoll(LxTxQrPoll.Status.EXPIRED)
        }
        if (!session.pollMutex.tryLock()) return@withContext pendingResult()
        try {
            val response = when (type) {
                LxTxQrType.QQ -> pollQq(session)
                LxTxQrType.WECHAT -> pollWeChat(session)
            }
            if (response.status == LxTxQrPoll.Status.SUCCESS ||
                response.status == LxTxQrPoll.Status.EXPIRED ||
                response.status == LxTxQrPoll.Status.ERROR
            ) {
                sessions.remove(sessionId, session)
            }
            response
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.InterruptedIOException) {
            if (type == LxTxQrType.WECHAT) pendingResult() else errorResult()
        } catch (_: IOException) {
            errorResult()
        } catch (_: Exception) {
            errorResult()
        } finally {
            session.pollMutex.unlock()
        }
    }

    private fun createQq(): Pair<ByteArray, String>? {
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow".toHttpUrl().newBuilder()
            .addQueryParameter("appid", QQ_APP_ID)
            .addQueryParameter("e", "2")
            .addQueryParameter("l", "M")
            .addQueryParameter("s", "3")
            .addQueryParameter("d", "72")
            .addQueryParameter("v", "4")
            .addQueryParameter("t", "0.${Random.nextLong(1_000_000_000L)}")
            .addQueryParameter("daid", QQ_DAID)
            .addQueryParameter("pt_3rd_aid", QQ_CONNECT_APP_ID)
            .build()
        val jar = SessionCookies()
        val response = execute(
            redirectClient,
            Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", QQ_REFERER).build(),
            jar,
        ) ?: return null
        if (response.code !in 200..299) return null
        val qrsig = jar.value("qrsig")?.takeIf(String::isNotBlank) ?: return null
        val id = UUID.randomUUID().toString()
        sessions[id] = Session(LxTxQrType.QQ, qrsig, jar, System.currentTimeMillis() + SESSION_TTL_MS)
        return response.body to id
    }

    private fun createWeChat(): Pair<ByteArray, String>? {
        val state = UUID.randomUUID().toString().replace("-", "")
        val redirectUri = "https://y.qq.com/portal/wx_redirect.html?login_type=2&skey=&code=&state="
        val connectUrl = "https://open.weixin.qq.com/connect/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("appid", WECHAT_APP_ID)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", "snsapi_login")
            .addQueryParameter("state", state)
            .addQueryParameter("self_redirect", "false")
            .build()
        val connect = execute(
            redirectClient,
            Request.Builder().url(connectUrl).header("User-Agent", USER_AGENT).build(),
        ) ?: return null
        if (connect.code !in 200..399) return null
        val qrcodeUrl = connect.location?.let(connect.finalUrl::resolve)
        val uuid = listOfNotNull(
            qrcodeUrl?.takeIf { it.isHttps && it.host == WECHAT_HOST }?.encodedPath,
            connect.finalUrl.encodedPath.takeIf { connect.finalUrl.host == WECHAT_HOST },
            connect.text,
        ).asSequence().mapNotNull { QRCODE_PATH.find(it)?.groupValues?.getOrNull(1) }.firstOrNull()
            ?: return null
        val imageUrl = "https://open.weixin.qq.com/connect/qrcode/$uuid".toHttpUrl()
        val image = execute(
            redirectClient,
            Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT).build(),
        ) ?: return null
        if (image.code !in 200..299 || image.body.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        sessions[id] = Session(
            type = LxTxQrType.WECHAT,
            credential = uuid,
            cookies = SessionCookies(),
            expiresAt = System.currentTimeMillis() + SESSION_TTL_MS,
        )
        return image.body to id
    }

    private fun pollQq(session: Session): LxTxQrPoll {
        val token = qqPtqrToken(session.credential)
        val url = "https://ssl.ptlogin2.qq.com/ptqrlogin".toHttpUrl().newBuilder()
            .addQueryParameter("u1", "https://graph.qq.com/oauth2.0/login_jump")
            .addQueryParameter("ptqrtoken", token.toString())
            .addQueryParameter("ptredirect", "0")
            .addQueryParameter("h", "1")
            .addQueryParameter("t", "1")
            .addQueryParameter("g", "1")
            .addQueryParameter("from_ui", "1")
            .addQueryParameter("ptlang", "2052")
            .addQueryParameter("action", "0-0-${System.currentTimeMillis()}")
            .addQueryParameter("js_ver", "22051214")
            .addQueryParameter("js_type", "1")
            .addQueryParameter("login_sig", "")
            .addQueryParameter("pt_uistyle", "40")
            .addQueryParameter("aid", QQ_APP_ID)
            .addQueryParameter("daid", QQ_DAID)
            .addQueryParameter("pt_3rd_aid", QQ_CONNECT_APP_ID)
            .addQueryParameter("has_onekey", "1")
            .build()
        val response = execute(
            redirectClient,
            Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", QQ_REFERER).build(),
            session.cookies,
        ) ?: return errorResult()
        if (response.code !in 200..299) return errorResult()
        val callback = parseQqCallback(response.text) ?: return errorResult()
        return when (callback.code) {
            "66" -> pendingResult()
            "67" -> LxTxQrPoll(LxTxQrPoll.Status.SCANNED)
            "65" -> LxTxQrPoll(LxTxQrPoll.Status.EXPIRED)
            "0" -> {
                val code = callback.redirectUrl
                    ?.let { followQqLoginRedirect(it, session.cookies) }
                    ?: authorizeQq(session.cookies)
                if (code.isNullOrBlank()) return errorResult()
                val cookie = exchangeTencentLogin(code, session.cookies, QQ_EXCHANGE)
                    ?: return errorResult()
                if (!hasTencentLoginCookie(cookie)) errorResult()
                else LxTxQrPoll(LxTxQrPoll.Status.SUCCESS, cookie)
            }
            else -> errorResult()
        }
    }

    private fun pollWeChat(session: Session): LxTxQrPoll {
        val url = "https://long.open.weixin.qq.com/connect/l/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("uuid", session.credential)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val response = execute(
            longPollClient,
            Request.Builder().url(url).header("User-Agent", USER_AGENT).build(),
        ) ?: return errorResult()
        if (response.code !in 200..299) return errorResult()
        val body = response.text
        return when (parseWeChatStatus(body)) {
            LxTxQrPoll.Status.PENDING -> pendingResult()
            LxTxQrPoll.Status.SCANNED -> LxTxQrPoll(LxTxQrPoll.Status.SCANNED)
            LxTxQrPoll.Status.EXPIRED -> LxTxQrPoll(LxTxQrPoll.Status.EXPIRED)
            LxTxQrPoll.Status.ERROR -> errorResult()
            LxTxQrPoll.Status.SUCCESS -> {
                val authCode = WX_AUTH_CODE.find(body)?.groupValues?.getOrNull(1)
                    ?.takeIf(String::isNotBlank) ?: return errorResult()
                val cookie = exchangeTencentLogin(authCode, session.cookies, WECHAT_EXCHANGE)
                    ?: return errorResult()
                if (!hasTencentLoginCookie(cookie)) errorResult()
                else LxTxQrPoll(LxTxQrPoll.Status.SUCCESS, cookie)
            }
        }
    }

    private fun qqGtk(cookies: SessionCookies): Int =
        qqGtk(listOf("qqmusic_key", "p_skey", "skey", "p_lskey", "lskey")
            .firstNotNullOfOrNull(cookies::value)
            .orEmpty())

    private fun followQqLoginRedirect(initial: String, cookies: SessionCookies): String? {
        var current = initial.toHttpUrlOrNull() ?: return null
        repeat(MAX_REDIRECTS) {
            if (current.scheme != "https" || !isTencentHost(current.host)) return null
            extractOAuthCode(current.toString())?.let { return it }
            val response = execute(
                redirectClient,
                Request.Builder().url(current).header("User-Agent", USER_AGENT).header("Referer", QQ_REFERER).build(),
                cookies,
            ) ?: return null
            val location = response.location
            if (location != null) {
                current = response.finalUrl.resolve(location) ?: return null
                return@repeat
            }
            val scriptUrl = JS_REDIRECT.find(response.text)?.groupValues?.getOrNull(1)
            if (scriptUrl != null) {
                current = response.finalUrl.resolve(scriptUrl) ?: return null
                return@repeat
            }
            return extractOAuthCode(response.text)
        }
        return extractOAuthCode(current.toString())
    }

    private fun authorizeQq(cookies: SessionCookies): String? {
        val gtkCookie = cookies.value("p_skey").orEmpty()
        val form = FormBody.Builder()
            .add("response_type", "code")
            .add("client_id", QQ_CONNECT_APP_ID)
            .add("redirect_uri", QQ_REDIRECT_URI)
            .add("scope", "get_user_info")
            .add("state", "state")
            .add("switch", "")
            .add("from_ptlogin", "1")
            .add("src", "1")
            .add("update_auth", "1")
            .add("openapi", "1010")
            .add("g_tk", qqGtk(gtkCookie).toString())
            .add("auth_time", System.currentTimeMillis().toString())
            .add("ui", UUID.randomUUID().toString())
            .build()
        val response = execute(
            redirectClient,
            Request.Builder()
                .url("https://graph.qq.com/oauth2.0/authorize")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://graph.qq.com/")
                .post(form)
                .build(),
            cookies,
        ) ?: return null
        response.location?.let { extractOAuthCode(response.finalUrl.resolve(it)?.toString().orEmpty()) }
            ?.let { return it }
        if (response.code != 200) return null
        return extractOAuthCodeFromCallbackBody(response.text)
    }

    private fun exchangeTencentLogin(
        code: String,
        cookies: SessionCookies,
        exchange: Exchange,
    ): String? {
        for (host in EXCHANGE_HOSTS) {
            val comm = JsonObject().apply {
                if (exchange.wechat) {
                    addProperty("tmeAppID", "qqmusic")
                    addProperty("tmeLoginType", "1")
                    addProperty("g_tk", 5381)
                } else {
                    addProperty("g_tk", qqGtk(cookies))
                }
                addProperty("platform", "yqq")
                addProperty("ct", 24)
                addProperty("cv", 0)
            }
            val param = JsonObject().apply {
                if (exchange.wechat) addProperty("strAppid", WECHAT_APP_ID)
                addProperty("code", code)
            }
            val req = JsonObject().apply {
                addProperty("module", exchange.module)
                addProperty("method", exchange.method)
                add("param", param)
            }
            val payload = JsonObject().apply {
                add("comm", comm)
                add("req", req)
            }
            val request = Request.Builder()
                .url("https://$host/cgi-bin/musicu.fcg")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = try {
                execute(redirectClient, request, cookies)
            } catch (_: IOException) {
                continue
            } ?: continue
            if (response.code !in 200..299) continue
            val success = runCatching {
                val root = JsonParser.parseString(response.text).asJsonObject
                root.getAsJsonObject("req")?.getAsJsonObject("data")?.get("code")?.asInt == 0
            }.getOrDefault(false)
            if (success) return cookies.cookieHeader("https://$host".toHttpUrl())
        }
        return null
    }

    private fun execute(
        client: OkHttpClient,
        request: Request,
        cookies: SessionCookies? = null,
    ): HttpResult? {
        val actualRequest = cookies?.applyTo(request) ?: request
        return try {
            client.newCall(actualRequest).execute().use { response ->
                cookies?.capture(response.request.url, response.headers.values("Set-Cookie"))
                HttpResult(
                    code = response.code,
                    body = response.body?.bytes() ?: ByteArray(0),
                    location = response.header("Location"),
                    finalUrl = response.request.url,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            throw failure
        }
    }

    private fun purgeExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expiresAt < now }
    }

    private data class Session(
        val type: LxTxQrType,
        val credential: String,
        val cookies: SessionCookies,
        val expiresAt: Long,
        val pollMutex: Mutex = Mutex(),
    )

    private data class HttpResult(
        val code: Int,
        val body: ByteArray,
        val location: String?,
        val finalUrl: HttpUrl,
    ) {
        val text: String get() = String(body, StandardCharsets.UTF_8)
    }

    private class SessionCookies {
        private val lock = Any()
        private val values = LinkedHashMap<String, Cookie>()

        fun capture(url: HttpUrl, setCookies: List<String>) = synchronized(lock) {
            setCookies.forEach { raw ->
                Cookie.parse(url, raw)?.let { cookie ->
                    val key = "${cookie.name}\u0000${cookie.domain}\u0000${cookie.path}"
                    if (cookie.expiresAt <= System.currentTimeMillis()) values.remove(key)
                    else values[key] = cookie
                }
            }
        }

        fun applyTo(request: Request): Request = synchronized(lock) {
            val matching = values.values.filter { it.matches(request.url) }
            if (matching.isEmpty()) request
            else request.newBuilder().header(
                "Cookie",
                matching.joinToString("; ") { "${it.name}=${it.value}" },
            ).build()
        }

        fun value(name: String): String? = synchronized(lock) {
            values.values.lastOrNull { it.name == name }?.value
        }

        fun cookieHeader(url: HttpUrl): String? = synchronized(lock) {
            values.values.filter { it.matches(url) }
                .takeIf(List<Cookie>::isNotEmpty)
                ?.joinToString("; ") { "${it.name}=${it.value}" }
        }
    }

    internal data class QqCallback(val code: String, val redirectUrl: String?)

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        private const val QQ_APP_ID = "716027609"
        private const val QQ_DAID = "383"
        private const val QQ_CONNECT_APP_ID = "100497308"
        private const val WECHAT_APP_ID = "wx48db31d50e334801"
        private const val WECHAT_HOST = "open.weixin.qq.com"
        private const val QQ_REFERER = "https://xui.ptlogin2.qq.com/"
        private const val QQ_REDIRECT_URI =
            "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https%3A%2F%2Fy.qq.com%2F"
        private const val SESSION_TTL_MS = 10 * 60 * 1000L
        private const val MAX_REDIRECTS = 8
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EXCHANGE_HOSTS = listOf("u.y.qq.com", "szu.y.qq.com", "shu.y.qq.com")
        private val QQ_ARGUMENT = Regex("'((?:\\\\.|[^'])*)'")
        private val QRCODE_PATH = Regex("/connect/qrcode/([\\w-]+)")
        private val WX_ERROR_CODE = Regex("wx_errcode=(\\d+)")
        private val WX_AUTH_CODE = Regex("wx_code='([^']*)'")
        private val JS_REDIRECT = Regex(
            "(?:location\\.replace\\(\\s*|(?:window|self|top|document)?\\.?location(?:\\.href)?\\s*=\\s*)[\\\"']([^\\\"']+)[\\\"']",
        )
        private val OAUTH_CODE = Regex("(?:[?&#]|^)code=([^&#'\\\"\\s]+)")

        private val QQ_EXCHANGE = Exchange(
            wechat = false,
            module = "QQConnectLogin.LoginServer",
            method = "QQLogin",
        )
        private val WECHAT_EXCHANGE = Exchange(
            wechat = true,
            module = "music.login.LoginServer",
            method = "Login",
        )

        internal fun qqPtqrToken(qrsig: String): Int {
            var hash = 0
            qrsig.forEach { hash += (hash shl 5) + it.code }
            return hash and 0x7fffffff
        }

        internal fun qqGtk(pSkey: String): Int {
            var hash = 5381
            pSkey.forEach { hash += (hash shl 5) + it.code }
            return hash and 0x7fffffff
        }

        internal fun parseQqCallback(body: String): QqCallback? {
            val args = QQ_ARGUMENT.findAll(body).map { it.groupValues[1] }.toList()
            if (args.size < 2 || !body.contains("ptuiCB")) return null
            return QqCallback(args[0], args.getOrNull(2))
        }

        internal fun extractOAuthCodeFromCallbackBody(body: String): String? = runCatching {
            JsonParser.parseString(body).asJsonObject
                .get("callback")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.let(::extractOAuthCode)
        }.getOrNull()

        internal fun extractOAuthCode(value: String): String? {
            val url = value.toHttpUrlOrNull()
            url?.queryParameter("code")?.takeIf(String::isNotBlank)?.let { return it }
            return OAUTH_CODE.find(value)?.groupValues?.getOrNull(1)
        }

        internal fun parseWeChatStatus(body: String): LxTxQrPoll.Status {
            val code = WX_ERROR_CODE.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return LxTxQrPoll.Status.ERROR
            return when (code) {
                408 -> LxTxQrPoll.Status.PENDING
                403 -> LxTxQrPoll.Status.ERROR
                404 -> LxTxQrPoll.Status.SCANNED
                402 -> LxTxQrPoll.Status.EXPIRED
                405 -> if (WX_AUTH_CODE.find(body)?.groupValues?.getOrNull(1).isNullOrBlank()) {
                    LxTxQrPoll.Status.ERROR
                } else {
                    LxTxQrPoll.Status.SUCCESS
                }
                else -> LxTxQrPoll.Status.ERROR
            }
        }

        internal fun hasTencentLoginCookie(cookie: String): Boolean =
            cookie.split(';').any { part ->
                val name = part.substringBefore('=').trim()
                name == "qqmusic_key" || name == "qm_keyst"
            }

        internal fun isTencentHost(host: String): Boolean =
            host == "qq.com" || host.endsWith(".qq.com")

        private fun pendingResult() = LxTxQrPoll(LxTxQrPoll.Status.PENDING)
        private fun errorResult() = LxTxQrPoll(LxTxQrPoll.Status.ERROR)
    }

    private data class Exchange(val wechat: Boolean, val module: String, val method: String)
}
