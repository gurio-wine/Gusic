package com.gurio.gusic.data.lxmusic.kugou

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.gurio.gusic.data.lxmusic.LxArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * 酷狗关注歌手列表（v4/follow_list，relationuser.kugou.com）。
 *
 * 协议要点（与云歌单/账号信息族对齐的网关签名体系，参照 MakcRe/KuGouMusicApi user_follow.js）：
 * - POST https://gateway.kugou.com/v4/follow_list，头 x-router: relationuser.kugou.com；
 * - query 带公共参数 + plat=1 + token/userid（同 postCloud/get_my_info，均参与签名）；
 *   Cookie 头额外带 token/userid/kg_mid/dfid（与登录时持久化的串一致）；
 * - body：{"merge":2,"need_iden_type":1,"ext_params":"k_pic,jumptype,singerid,score",
 *   "userid":<数字>,"type":0,"id_type":0,"p":<RSA NO_PADDING 大写 hex({"clienttime":<秒>,"token":"<token>"})>}；
 * - 响应 data.lists[{singerid,nickname,pic}]（兜底 data.list；字段名兜底 id/userid、
 *   singername/name、k_pic/imgurl）；error_code==20017 表示登录态失效。
 */
internal class KugouRelationApi(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: KugouCookieStore,
) {
    companion object {
        private const val TAG = "KugouRelationApi"
        private const val GATEWAY_URL = "https://gateway.kugou.com"
        private const val FOLLOW_LIST_PATH = "/v4/follow_list"
        private const val RELATION_ROUTER = "relationuser.kugou.com"

        /** 登录态失效业务码（调研规格：20017=未登录）。 */
        private const val ERROR_CODE_NOT_LOGGED_IN = 20017

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * 拉取关注歌手列表；未登录/请求失败返回 null（与“成功但为空”区分）。
     *
     * v4/follow_list 的翻页参数未验证：当前只发一次请求，若默认页大小小于
     * 实际关注数会截断；真机核对响应是否带 total/page 字段后补翻页。
     */
    suspend fun fetchFollowedArtists(): List<LxArtist>? = withContext(Dispatchers.IO) {
        val token = cookieStore.getAppToken()?.takeIf(String::isNotBlank) ?: return@withContext null
        val userid = cookieStore.getAppUserid()?.takeIf(String::isNotBlank) ?: return@withContext null
        val mid = cookieStore.getOrCreateMid()
        val clienttimeSec = KugouAccountApi.epochSeconds()

        // p 参数：RSA 裸加密（NO_PADDING）后的 clienttime+token，大写 hex
        val pPayload = JsonObject().apply {
            addProperty("clienttime", clienttimeSec.toLongOrNull() ?: 0L)
            addProperty("token", token)
        }.toString()
        val p = runCatching { KugouAccountApi.rsaRawEncryptHexUppercase(pPayload) }
            .getOrElse { e ->
                Timber.w(e, "$TAG: rsa encrypt failed")
                return@withContext null
            }

        val body = JsonObject().apply {
            addProperty("merge", 2)
            addProperty("need_iden_type", 1)
            addProperty("ext_params", "k_pic,jumptype,singerid,score")
            addProperty("userid", userid.toLongOrNull() ?: 0L)
            addProperty("type", 0)
            addProperty("id_type", 0)
            addProperty("p", p)
        }.toString()

        val params = KugouAccountApi.commonParams(mid, clienttimeSec) +
            mapOf("plat" to "1", "token" to token, "userid" to userid)
        val request = Request.Builder()
            .url(
                KugouAccountApi.buildUrl(
                    GATEWAY_URL + FOLLOW_LIST_PATH,
                    params,
                    KugouAccountApi.signParams(params, body),
                )
            )
            .headers(KugouAccountApi.commonHeaders(mid, clienttimeSec))
            .header("x-router", RELATION_ROUTER)
            // relationuser 网关同时从 Cookie 取登录票据（与登录持久化串一致，替换公共头的 mid-only Cookie）
            .header("Cookie", "token=$token; userid=$userid; kg_mid=$mid; dfid=-")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val root = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: %s http %s", FOLLOW_LIST_PATH, response.code)
                    return@use null
                }
                KugouAccountApi.parseJsonObject(response.body?.string().orEmpty())
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: %s request failed", FOLLOW_LIST_PATH)
            null
        } ?: return@withContext null

        val errorCode = root.intOf("error_code")
        if (errorCode == ERROR_CODE_NOT_LOGGED_IN) {
            Timber.w("$TAG: follow_list not logged in (error_code=20017)")
            return@withContext null
        }
        if (errorCode != null && errorCode != 0) {
            Timber.w("$TAG: follow_list rejected error_code=%s msg=%s", errorCode, root.strOf("msg"))
            return@withContext null
        }

        // 实际响应为 data.lists（个别版本 data.list）；条目字段按 kugoFlutter/EchoMusic 兜底取值
        val data = root.get("data") as? JsonObject
        val singerList = (data?.get("lists") ?: data?.get("list")) as? JsonArray ?: JsonArray()
        singerList.mapNotNull { raw ->
            val obj = raw as? JsonObject ?: return@mapNotNull null
            val id = obj.strOf("singerid") ?: obj.strOf("id") ?: obj.strOf("userid")
            val idOrBlank = id?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            LxArtist(
                id = idOrBlank,
                name = obj.strOf("nickname") ?: obj.strOf("singername") ?: obj.strOf("name") ?: "",
                // 头像可能是 http 明文，统一升级 https 避免明文流量被禁
                pic = (obj.strOf("pic") ?: obj.strOf("k_pic") ?: obj.strOf("imgurl"))
                    ?.takeIf(String::isNotBlank)?.replaceFirst("http://", "https://"),
            )
        }
    }
}
