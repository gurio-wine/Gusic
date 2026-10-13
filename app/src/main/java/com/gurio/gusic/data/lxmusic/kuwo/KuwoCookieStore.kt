package com.gurio.gusic.data.lxmusic.kuwo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KuwoCookieStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "KuwoCookieStore"
        private const val PREFS_NAME = "kuwo_prefs"
        private const val KEY_COOKIE = "kw_cookie"
        private const val KEY_VIP_TYPE = "kw_vip_type"
        private const val KEY_ACCOUNT_ID = "kw_account_id"
        private const val KEY_VIP_UPDATED_AT = "kw_vip_updated_at"
        private const val KEY_COOKIE_VALID = "kw_cookie_valid"
        private const val KEY_NICKNAME = "kw_nickname"
        private const val KEY_AVATAR_URL = "kw_avatar_url"
        private const val KEY_DEVICE_USER = "kw_native_device_user"
        private const val KEY_ANDROID_ID = "kw_native_android_id"
        private const val KEY_APP_UID = "kw_native_app_uid"
        private const val KEY_NATIVE_UID = "kw_native_uid"
        private const val KEY_NATIVE_SID = "kw_native_sid"
        const val VIP_UNKNOWN = -1
    }

    /** 原生 pl3 协议凭证（短信验证码登录签发），区别于 web cookie 里的 sid */
    data class NativeCredentials(val uid: String, val sid: String)

    /** 装机级设备身份：原生协议四元组的设备侧三元组，登出后保留复用 */
    data class DeviceIdentity(val deviceUser: String, val androidId: String, val appUid: String)

    private val prefs: SharedPreferences = createCredentialPrefs()

    private val _cookieFlow = MutableStateFlow(prefs.getString(KEY_COOKIE, null))
    val cookieFlow: StateFlow<String?> = _cookieFlow.asStateFlow()

    private val _vipTypeFlow = MutableStateFlow(prefs.getInt(KEY_VIP_TYPE, VIP_UNKNOWN))
    val vipTypeFlow: StateFlow<Int> = _vipTypeFlow.asStateFlow()

    private val _cookieValidFlow = MutableStateFlow(
        prefs.getBoolean(KEY_COOKIE_VALID, true)
    )
    val cookieValidFlow: StateFlow<Boolean> = _cookieValidFlow.asStateFlow()

    private val _nicknameFlow = MutableStateFlow(prefs.getString(KEY_NICKNAME, null))
    val nicknameFlow: StateFlow<String?> = _nicknameFlow.asStateFlow()

    private val _avatarUrlFlow = MutableStateFlow(prefs.getString(KEY_AVATAR_URL, null))
    val avatarUrlFlow: StateFlow<String?> = _avatarUrlFlow.asStateFlow()

    fun hasCookie(): Boolean = !_cookieFlow.value.isNullOrBlank()

    fun getCookie(): String? = _cookieFlow.value

    fun getVipType(): Int = _vipTypeFlow.value

    fun getAccountId(): Long = prefs.getLong(KEY_ACCOUNT_ID, -1L)

    fun getNickname(): String? = _nicknameFlow.value

    fun getAvatarUrl(): String? = _avatarUrlFlow.value

    fun getAccountUpdatedAt(): Long = prefs.getLong(KEY_VIP_UPDATED_AT, 0L)

    fun getDeviceIdentity(): DeviceIdentity? {
        val deviceUser = prefs.getString(KEY_DEVICE_USER, null) ?: return null
        val androidId = prefs.getString(KEY_ANDROID_ID, null) ?: return null
        val appUid = prefs.getString(KEY_APP_UID, null) ?: return null
        return DeviceIdentity(deviceUser, androidId, appUid)
    }

    fun saveDeviceIdentity(identity: DeviceIdentity) {
        prefs.edit()
            .putString(KEY_DEVICE_USER, identity.deviceUser)
            .putString(KEY_ANDROID_ID, identity.androidId)
            .putString(KEY_APP_UID, identity.appUid)
            .apply()
    }

    fun getNativeCredentials(): NativeCredentials? {
        val uid = prefs.getString(KEY_NATIVE_UID, null) ?: return null
        val sid = prefs.getString(KEY_NATIVE_SID, null) ?: return null
        return NativeCredentials(uid, sid)
    }

    fun saveNativeCredentials(uid: String, sid: String) {
        prefs.edit()
            .putString(KEY_NATIVE_UID, uid)
            .putString(KEY_NATIVE_SID, sid)
            .apply()
    }

    fun setCookie(value: String) {
        val trimmed = value.trim()
        prefs.edit()
            .putString(KEY_COOKIE, trimmed)
            .putBoolean(KEY_COOKIE_VALID, true)
            .apply()
        _cookieFlow.value = trimmed
        _cookieValidFlow.value = true
    }

    fun setAccountInfo(vipType: Int, accountId: Long) {
        prefs.edit()
            .putInt(KEY_VIP_TYPE, vipType)
            .putLong(KEY_ACCOUNT_ID, accountId)
            .putLong(KEY_VIP_UPDATED_AT, System.currentTimeMillis())
            .putBoolean(KEY_COOKIE_VALID, true)
            .apply()
        _vipTypeFlow.value = vipType
        _cookieValidFlow.value = true
    }

    fun setProfile(nickname: String?, avatarUrl: String?) {
        // 接口可能只回传单边（如只回头像）：仅覆盖非空侧，避免偶发空值清掉已有昵称/头像
        val newNickname = nickname ?: _nicknameFlow.value
        val newAvatar = avatarUrl ?: _avatarUrlFlow.value
        prefs.edit()
            .putString(KEY_NICKNAME, newNickname)
            .putString(KEY_AVATAR_URL, newAvatar)
            .apply()
        _nicknameFlow.value = newNickname
        _avatarUrlFlow.value = newAvatar
    }

    fun markCookieInvalid() {
        prefs.edit()
            .putBoolean(KEY_COOKIE_VALID, false)
            .putInt(KEY_VIP_TYPE, VIP_UNKNOWN)
            .apply()
        _cookieValidFlow.value = false
        _vipTypeFlow.value = VIP_UNKNOWN
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_COOKIE)
            .remove(KEY_VIP_TYPE)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_VIP_UPDATED_AT)
            .remove(KEY_COOKIE_VALID)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_URL)
            // 原生会话凭证随账号登出清除；设备三元组装机级，保留给下次登录复用
            .remove(KEY_NATIVE_UID)
            .remove(KEY_NATIVE_SID)
            .apply()
        _cookieFlow.value = null
        _vipTypeFlow.value = VIP_UNKNOWN
        _cookieValidFlow.value = true
        _nicknameFlow.value = null
        _avatarUrlFlow.value = null
    }

    private fun createCredentialPrefs(): SharedPreferences = try {
        createEncryptedPrefs()
    } catch (e: Exception) {
        Timber.e(e, "$TAG: EncryptedSharedPreferences unreadable, deleting and recreating")
        context.deleteSharedPreferences(PREFS_NAME)
        try {
            createEncryptedPrefs()
        } catch (e2: Exception) {
            Timber.e(e2, "$TAG: EncryptedSharedPreferences still failing, falling back to plain prefs")
            context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
