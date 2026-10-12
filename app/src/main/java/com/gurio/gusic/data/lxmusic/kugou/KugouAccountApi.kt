package com.gurio.gusic.data.lxmusic.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/** 发送验证码结果：成功时 msg 为服务端提示（可空）；失败时 msg 为服务端错误文案（可空，调用方回退通用文案）。 */
internal data class KugouSmsCodeResult(val ok: Boolean, val msg: String?)

/**
 * 酷狗账号接口：短信验证码登录（send_mobile_code / login_by_verifycode）与用户资料（get_my_info）。
 *
 * 协议参照 KuGouMusicApi（MakcRe）util/crypto.js + util/request.js 与 LX-Y-Music-IOS
 * kg/utils/crypto.ts（后者文件头声明"Fully matches KuGouMusicApi's crypto.js behavior"），
 * 并与 develop202/kgcheckin 的 util/crypto.js 三方交叉验证一致：
 *
 * - 签名（signatureAndroidParams）：MD5(salt + paramsString + bodyJsonString + salt)，
 *   paramsString = 全部 query 参数（不含 signature 自身）按 key 字典序排序后拼 "k=v"（无分隔符），
 *   GET 时 bodyJsonString 为空串。
 * - AES（cryptoAesEncrypt）：tempKey = 16 位随机小写字母数字；realKey = MD5(tempKey) 的 32 位 hex
 *   **字符串**；key = realKey 字符串本身的 32 个 ASCII 字节（→ AES-256-CBC）；
 *   iv = realKey 第 17-32 个字符这 16 个 ASCII 字符的字节（16 字节）。
 *   注意：key **不是** hex 解码后的 16 字节——CryptoJS `Utf8.parse(realKey)` 与
 *   node `createCipheriv('aes-256-cbc', realKey, realKey.slice(16))` 均按字符串字节取 32 字节 key；
 *   密文 hex 小写；padding PKCS7（JVM 的 PKCS5Padding 对 16 字节块与其等价）。
 *   解密 secu_params 用同一 tempKey 派生的 key/iv。
 * - RSA（cryptoRSAEncrypt → rsaRawEncrypt）：1024 位公钥裸加密（NO_PADDING），
 *   UTF8(明文 JSON) 右侧补 0x00 至 128 字节 → modPow(e, n) → 左补零至 128 字节 → hex 大写
 *   （JS 为 toString(16).padStart(256, '0') 再 toUpperCase()）。
 * - mid：MD5(随机32字符) 的 32 位 hex，持久化复用（见 [KugouCookieStore.getOrCreateMid]）；dfid 固定 "-"。
 * - 公共 header：dfid/mid/clienttime 三个独立 header（与 KuGouMusicApi、kgcheckin 的 request 层一致，
 *   而非真实客户端捕获里的单个 "dfid: -;mid=...;clienttime=..." 组合头）+ kg-rc/kg-thash/kg-rec/kg-rf
 *   + Cookie: mid=<mid>。
 */
internal class KugouAccountApi(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: KugouCookieStore,
) {
    companion object {
        private const val TAG = "KugouAccountApi"

        /** Android 端签名盐（KuGouMusicApi helper.js signatureAndroidParams 的 str 常量）。 */
        private const val SALT = "OIlwieks28dk2k092lksi2UIkp"
        private const val APPID = "1005"
        private const val CLIENTVER = "20489"

        private const val SEND_CODE_URL = "http://login.user.kugou.com/v7/send_mobile_code"
        private const val LOGIN_URL = "https://loginserviceretry.kugou.com/v7/login_by_verifycode"
        private const val PROFILE_URL = "https://gateway.kugou.com/v3/get_my_info"
        private const val PROFILE_FALLBACK_URL = "https://wwwapi.kugou.com/uc/userinfo"

        private const val UA_DEFAULT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
        private const val UA_LOGIN = "Android16-1070-11440-130-0-LOGIN-wifi"

        /** login_by_verifycode 非精简版的固定 t3（login_cellphone.js dataMap.t3）。 */
        private const val LOGIN_T3 = "MCwwLDAsMCwwLDAsMCwwLDA="

        /** 酷狗客户端内置 RSA 公钥（X.509 SPKI base64，crypto.js publicRasKey）。 */
        private const val RSA_SPKI_BASE64 =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr" +
                "9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkCh" +
                "rsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"

        private const val RSA_KEY_LENGTH = 128

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val rsaPublicKey: RSAPublicKey by lazy {
            val decoded = Base64.getDecoder().decode(RSA_SPKI_BASE64)
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded)) as RSAPublicKey
        }

        internal fun md5Hex(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /**
         * 通用签名：MD5(salt + 按 key 字典序的 "k=v" 无分隔拼接 + body + salt)。
         * 与 helper.js signatureAndroidParams 逐字等价（Object.keys(params).sort() 为字典序）。
         */
        internal fun signParams(params: Map<String, String>, body: String): String {
            val paramsString = params.keys.sorted().joinToString("") { key -> "$key=${params.getValue(key)}" }
            return md5Hex(SALT + paramsString + body + SALT)
        }

        /** 公共 query 参数（全部参与签名）；clienttime 为秒级时间戳，需与同名 header 一致。 */
        internal fun commonParams(mid: String, clienttimeSec: String): Map<String, String> = mapOf(
            "dfid" to "-",
            "mid" to mid,
            "uuid" to "-",
            "appid" to APPID,
            "clientver" to CLIENTVER,
            "clienttime" to clienttimeSec,
        )

        /** 公共 header；dfid/mid/clienttime 独立头 + kg-* 内部标识头 + Cookie: mid=<mid>。 */
        internal fun commonHeaders(mid: String, clienttimeSec: String): Headers = Headers.headersOf(
            "User-Agent", UA_DEFAULT,
            "dfid", "-",
            "mid", mid,
            "clienttime", clienttimeSec,
            "kg-rc", "1",
            "kg-thash", "5d816a0",
            "kg-rec", "1",
            "kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F",
            "Cookie", "mid=$mid",
        )

        /** key = MD5(appid + salt + clientver + data)，与 helper.js signParamsKey 一致（登录传毫秒、云歌单传秒）。 */
        internal fun signParamsKey(data: String): String =
            md5Hex(APPID + SALT + CLIENTVER + data)

        /**
         * AES key/iv 派生（JS 源等价转换点，重点对照处）：
         * realKey = MD5(tempKey) 的 32 位 hex 字符串；
         * key = realKey 的 32 个 ASCII 字符字节（CryptoJS Utf8.parse(realKey) → 32 字节 → AES-256）；
         * iv = realKey 第 17-32 位字符（16 个 ASCII 字符）的字节——是 hex 字符串本身，
         * 不是 hex 解码后的字节。
         */
        private fun aesCipher(tempKey: String, opmode: Int): Cipher {
            val realKey = md5Hex(tempKey)
            val keyBytes = realKey.toByteArray(Charsets.UTF_8)
            val ivBytes = realKey.substring(16, 32).toByteArray(Charsets.UTF_8)
            return aesCipherRaw(keyBytes, ivBytes, opmode)
        }

        /** 直接以 key/iv 字节构建 AES-CBC-PKCS7 cipher（云歌单 delete_list 用 16 字节 key）。 */
        internal fun aesCipherRaw(keyBytes: ByteArray, ivBytes: ByteArray, opmode: Int): Cipher {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(opmode, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            return cipher
        }

        internal fun aesEncryptHex(plaintext: String, tempKey: String): String =
            aesCipher(tempKey, Cipher.ENCRYPT_MODE)
                .doFinal(plaintext.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        internal fun aesDecryptHex(hex: String, tempKey: String): String =
            aesCipher(tempKey, Cipher.DECRYPT_MODE)
                .doFinal(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                .toString(Charsets.UTF_8)

        /** AES-CBC-PKCS7 加密并 Base64（云歌单 delete_list 的密文格式）。 */
        internal fun aesCbcEncryptToBase64(plaintext: String, key16: String, iv16: String): String {
            val cipher = aesCipherRaw(
                key16.toByteArray(Charsets.UTF_8),
                iv16.toByteArray(Charsets.UTF_8),
                Cipher.ENCRYPT_MODE,
            )
            return Base64.getEncoder().encodeToString(
                cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
            )
        }

        /** 解密 AES-CBC-PKCS7 Base64 密文（delete_list 响应可能是整段密文）。 */
        internal fun aesCbcDecryptBase64(base64: String, key16: String, iv16: String): String {
            val cipher = aesCipherRaw(
                key16.toByteArray(Charsets.UTF_8),
                iv16.toByteArray(Charsets.UTF_8),
                Cipher.DECRYPT_MODE,
            )
            return cipher.doFinal(Base64.getDecoder().decode(base64)).toString(Charsets.UTF_8)
        }

        /**
         * RSA 裸加密（NO_PADDING）：UTF8 明文右侧补 0x00 至 128 字节 → modPow(e, n) →
         * 左补零至 128 字节 → hex 大写。与 crypto.js rsaRawEncrypt（hex padStart(256,'0')）等价。
         */
        internal fun rsaRawEncryptHexUppercase(plaintext: String): String {
            val data = plaintext.toByteArray(Charsets.UTF_8)
            check(data.size <= RSA_KEY_LENGTH) { "rsa raw plaintext too long" }
            val padded = ByteArray(RSA_KEY_LENGTH)
            System.arraycopy(data, 0, padded, 0, data.size)
            val result = BigInteger(1, padded)
                .modPow(rsaPublicKey.publicExponent, rsaPublicKey.modulus)
            val raw = result.toByteArray()
            val srcLen = minOf(raw.size, RSA_KEY_LENGTH)
            val out = ByteArray(RSA_KEY_LENGTH)
            System.arraycopy(raw, raw.size - srcLen, out, RSA_KEY_LENGTH - srcLen, srcLen)
            return out.joinToString("") { "%02X".format(it) }
        }

        /** RSA PKCS1-V1_5 加密 → hex 大写（云歌单 delete_list 的 p 参数，crypto.js rsaEncrypt2）。 */
        internal fun rsaPkcs1EncryptHexUppercase(plaintext: String): String {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
            return cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }
        }

        /** 随机小写字母数字串（登录 tempKey 16 位；云歌单 delete_list tempKey 6 位）。 */
        internal fun randomLowerAlphaNum(length: Int): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val rnd = SecureRandom()
            return buildString { repeat(length) { append(chars[rnd.nextInt(chars.length)]) } }
        }

        /** 16 位随机小写字母数字 tempKey。 */
        private fun randomTempKey(): String = randomLowerAlphaNum(16)

        /** 脱敏手机号：前 2 位 + 五个星号 + 第 11 位（login_cellphone.js substring(0,2) 与 substring(10,11)）。 */
        private fun maskMobile(mobile: String): String =
            if (mobile.length >= 11) mobile.substring(0, 2) + "*****" + mobile.substring(10, 11) else mobile

        internal fun epochSeconds(): String = (System.currentTimeMillis() / 1000).toString()

        internal fun buildUrl(base: String, params: Map<String, String>, signature: String): String =
            base.toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
                addQueryParameter("signature", signature)
            }.build().toString()

        internal fun parseJsonObject(text: String): JsonObject? =
            runCatching { JsonParser.parseString(text) }.getOrNull() as? JsonObject
    }

    /**
     * 发送短信验证码：POST http://login.user.kugou.com/v7/send_mobile_code（该域名无有效 TLS，需明文），
     * body = {"businessid":5,"mobile":"<手机号>","plat":3}（body 字符串参与签名）。
     * 成功：status==1 || error_code==0；响应头含 ssa-code（风控）时按失败处理。
     */
    suspend fun sendSmsCode(mobile: String): KugouSmsCodeResult = withContext(Dispatchers.IO) {
        val mid = cookieStore.getOrCreateMid()
        val clienttimeSec = epochSeconds()
        val params = commonParams(mid, clienttimeSec)
        val body = JsonObject().apply {
            addProperty("businessid", 5)
            addProperty("mobile", mobile)
            addProperty("plat", 3)
        }.toString()
        val request = Request.Builder()
            .url(buildUrl(SEND_CODE_URL, params, signParams(params, body)))
            .headers(commonHeaders(mid, clienttimeSec))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.header("ssa-code") != null) {
                    Timber.w("$TAG: send sms blocked by risk control (ssa-code)")
                    return@use KugouSmsCodeResult(false, null)
                }
                val root = parseJsonObject(response.body?.string().orEmpty())
                    ?: return@use KugouSmsCodeResult(false, null)
                val ok = root.intOf("status") == 1 || root.intOf("error_code") == 0
                val msg = root.strOf("msg") ?: root.strOf("error")
                if (ok) {
                    KugouSmsCodeResult(true, msg)
                } else {
                    Timber.w("$TAG: send sms rejected status=%s error_code=%s msg=%s",
                        root.intOf("status"), root.intOf("error_code"), msg)
                    KugouSmsCodeResult(false, msg)
                }
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: send sms request failed")
            KugouSmsCodeResult(false, null)
        }
    }

    /**
     * 验证码登录：POST https://loginserviceretry.kugou.com/v7/login_by_verifycode。
     * body 的 mobile 为脱敏值，明文手机号+验证码经 AES(params)、tempKey 经 RSA(pk) 传输；
     * 成功后完成全部持久化（appToken/userid/vipType + web 风格 cookie + 昵称头像）。
     */
    suspend fun loginBySmsCode(mobile: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val mid = cookieStore.getOrCreateMid()
        val clienttimeMs = System.currentTimeMillis()
        val tempKey = randomTempKey()

        // pk：RSA 裸加密 {"clienttime_ms":<毫秒>,"key":"<tempKey>"}，大写 hex
        val pk = rsaRawEncryptHexUppercase(
            JsonObject().apply {
                addProperty("clienttime_ms", clienttimeMs)
                addProperty("key", tempKey)
            }.toString(),
        )
        // params：AES 加密 {"mobile":"<明文>","code":"<验证码>"}，hex 小写
        val paramsEncrypted = aesEncryptHex(
            JsonObject().apply {
                addProperty("mobile", mobile)
                addProperty("code", code)
            }.toString(),
            tempKey,
        )
        val body = JsonObject().apply {
            addProperty("plat", 1)
            addProperty("support_multi", 1)
            addProperty("t1", 0)
            addProperty("t2", 0)
            addProperty("t3", LOGIN_T3)
            addProperty("clienttime_ms", clienttimeMs)
            addProperty("mobile", maskMobile(mobile))
            addProperty("key", signParamsKey(clienttimeMs.toString()))
            addProperty("pk", pk)
            addProperty("params", paramsEncrypted)
        }.toString()
        // 与 body 内 clienttime_ms 同源取秒，避免跨秒时 query/header 与 body 的时刻不一致
        val clienttimeSec = (clienttimeMs / 1000).toString()
        val queryParams = commonParams(mid, clienttimeSec)
        val request = Request.Builder()
            .url(buildUrl(LOGIN_URL, queryParams, signParams(queryParams, body)))
            .headers(commonHeaders(mid, clienttimeSec))
            .header("User-Agent", UA_LOGIN)
            .header("support-calm", "1")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val root = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseJsonObject(response.body?.string().orEmpty())
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: login by sms request failed")
            null
        } ?: return@withContext false

        val status = root.intOf("status")
        if (status != 1) {
            Timber.w("$TAG: login rejected status=%s error_code=%s msg=%s",
                status, root.intOf("error_code"), root.strOf("msg"))
            return@withContext false
        }
        val data = root.get("data") as? JsonObject ?: return@withContext false
        val userid = data.strOf("userid")?.takeIf(String::isNotBlank) ?: return@withContext false
        val vipType = data.intOf("vip_type") ?: 0
        // token：响应明文可能直接带 token，否则解密 secu_params（tempKey 派生 key/iv）后取 JSON.token，
        // 解出文本非 JSON 时整段即 token
        val token = data.strOf("token")?.takeIf(String::isNotBlank)
            ?: data.strOf("secu_params")?.let { decryptToken(it, tempKey) }
        if (token.isNullOrBlank()) {
            Timber.w("$TAG: login ok but token missing (secu_params present=%s)", data.strOf("secu_params") != null)
            return@withContext false
        }

        // 持久化：app 登录票据 → web 风格 cookie 串 → 拉取昵称头像（失败不影响登录结果）
        cookieStore.setAppLogin(token, userid, vipType)
        cookieStore.setCookie("token=$token; userid=$userid; kg_mid=$mid; dfid=-")
        runCatching { fetchProfile() }.getOrNull()?.let { (nick, avatar) ->
            if (!nick.isNullOrBlank() || !avatar.isNullOrBlank()) cookieStore.setProfile(nick, avatar)
        }
        Timber.i("$TAG: kugou sms login success userid=%s", userid)
        true
    }

    /**
     * 用户资料（昵称+头像）：主接口 gateway.kugou.com/v3/get_my_info（x-router: usercenter.kugou.com，
     * token/userid 参与 query 签名，body.p 为 RSA 裸加密），失败降级 wwwapi.kugou.com/uc/userinfo（无签名）。
     */
    suspend fun fetchProfile(): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        val token = cookieStore.getAppToken()?.takeIf(String::isNotBlank) ?: return@withContext null
        val userid = cookieStore.getAppUserid()?.takeIf(String::isNotBlank) ?: return@withContext null
        val mid = cookieStore.getOrCreateMid()
        fetchGatewayProfile(token, userid, mid) ?: fetchUserinfoFallback(token, userid, mid)
    }

    private fun fetchGatewayProfile(token: String, userid: String, mid: String): Pair<String?, String?>? {
        val clienttimeSec = epochSeconds()
        // plat=1 与 token/userid 均加入 query 并参与签名（request.js defaultParams 行为）
        val params = commonParams(mid, clienttimeSec) + mapOf(
            "plat" to "1",
            "token" to token,
            "userid" to userid,
        )
        // p：RSA 裸加密 {"token":<token>,"clienttime":<秒>}（user_detail.js），大写 hex
        val p = rsaRawEncryptHexUppercase(
            JsonObject().apply {
                addProperty("token", token)
                addProperty("clienttime", clienttimeSec.toLong())
            }.toString(),
        )
        val body = JsonObject().apply {
            addProperty("visit_time", clienttimeSec.toLong())
            addProperty("usertype", 1)
            addProperty("p", p)
            addProperty("userid", userid.toLongOrNull() ?: 0L)
        }.toString()
        val request = Request.Builder()
            .url(buildUrl(PROFILE_URL, params, signParams(params, body)))
            .headers(commonHeaders(mid, clienttimeSec))
            .header("x-router", "usercenter.kugou.com")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val data = (parseJsonObject(response.body?.string().orEmpty())?.get("data") as? JsonObject)
                extractProfile(data)
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: get_my_info failed")
            null
        }
    }

    private fun fetchUserinfoFallback(token: String, userid: String, mid: String): Pair<String?, String?>? {
        val url = PROFILE_FALLBACK_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("srcappid", "2919")
            addQueryParameter("clientver", "20000")
            addQueryParameter("clienttime", epochSeconds())
            addQueryParameter("mid", mid)
            addQueryParameter("uuid", "-")
            addQueryParameter("dfid", "-")
            addQueryParameter("appid", "1005")
            addQueryParameter("token", token)
            addQueryParameter("userid", userid)
            addQueryParameter("platid", "4")
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DEFAULT)
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val data = (parseJsonObject(response.body?.string().orEmpty())?.get("data") as? JsonObject)
                extractProfile(data)
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: userinfo fallback failed")
            null
        }
    }

    /** 昵称/头像多字段兼容 + http 头像升级 https；两者皆空返回 null。 */
    private fun extractProfile(data: JsonObject?): Pair<String?, String?>? {
        if (data == null) return null
        val nick = data.strOf("nickname")
            ?: data.strOf("nick_name")
            ?: data.strOf("userName")
        val avatar = (data.strOf("pic")
            ?: data.strOf("userpic")
            ?: data.strOf("user_pic")
            ?: data.strOf("avatar"))
            ?.let { if (it.startsWith("http://")) "https://${it.substring("http://".length)}" else it }
        if (nick.isNullOrBlank() && avatar.isNullOrBlank()) return null
        return nick?.takeIf(String::isNotBlank) to avatar?.takeIf(String::isNotBlank)
    }

    /** secu_params（hex）AES 解密；解出文本能 JSON.parse 则取其 token 字段，否则整段文本即 token。 */
    private fun decryptToken(secuParamsHex: String, tempKey: String): String? {
        val text = runCatching { aesDecryptHex(secuParamsHex, tempKey) }.getOrNull() ?: return null
        val fromJson = runCatching {
            (JsonParser.parseString(text) as? JsonObject)?.strOf("token")
        }.getOrNull()
        return fromJson?.takeIf(String::isNotBlank) ?: text.trim().takeIf(String::isNotEmpty)
    }
}

/** JSON 字段兼容读取（数字/字符串两种原始类型互转）；酷狗各接口同一字段类型不统一，统一在此兜底。 */
internal fun JsonObject.strOf(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

internal fun JsonObject.intOf(name: String): Int? =
    get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

internal fun JsonObject.longOf(name: String): Long? =
    get(name)?.takeIf { it.isJsonPrimitive }?.let { el ->
        runCatching { el.asLong }.getOrElse {
            runCatching { el.asString.trim().toDouble().toLong() }.getOrNull()
        }
    }

internal fun JsonObject.boolOf(name: String): Boolean? =
    get(name)?.takeIf { it.isJsonPrimitive }?.let { el ->
        runCatching { el.asBoolean }.getOrElse {
            runCatching { el.asInt != 0 }.getOrNull()
        }
    }
