package com.gurio.gusic.data.lxmusic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TencentQrLoginClientTest {
    @Test
    fun `QQ ptqr token uses Tencent hash recurrence`() {
        assertThat(TencentQrLoginClient.qqPtqrToken("abc")).isEqualTo(108966)
    }

    @Test
    fun `QQ callback parser extracts callback status and redirect`() {
        val parsed = TencentQrLoginClient.parseQqCallback(
            "ptuiCB('0','Login','https://graph.qq.com/oauth2.0/login_jump?code=abc','nickname','0')",
        )

        assertThat(parsed?.code).isEqualTo("0")
        assertThat(parsed?.redirectUrl).isEqualTo("https://graph.qq.com/oauth2.0/login_jump?code=abc")
        assertThat(TencentQrLoginClient.parseQqCallback("not a callback")).isNull()
    }

    @Test
    fun `OAuth parser reads code from redirect URL and callback text`() {
        assertThat(TencentQrLoginClient.extractOAuthCode("https://y.qq.com/callback?code=a%2Bb&state=state"))
            .isEqualTo("a+b")
        assertThat(TencentQrLoginClient.extractOAuthCode("callback?code=plain-code&state=x"))
            .isEqualTo("plain-code")
        assertThat(TencentQrLoginClient.extractOAuthCodeFromCallbackBody("{\"callback\":\"callback(\\\"https://y.qq.com/callback?code=json-code\\\")\"}"))
            .isEqualTo("json-code")
        assertThat(TencentQrLoginClient.extractOAuthCodeFromCallbackBody("{\"other\":\"callback?code=wrong\"}"))
            .isNull()
        assertThat(TencentQrLoginClient.extractOAuthCode("https://y.qq.com/callback?state=x")).isNull()
    }

    @Test
    fun `WeChat poll codes map to public QR states`() {
        assertThat(TencentQrLoginClient.parseWeChatStatus("wx_errcode=408"))
            .isEqualTo(LxTxQrPoll.Status.PENDING)
        assertThat(TencentQrLoginClient.parseWeChatStatus("wx_errcode=403"))
            .isEqualTo(LxTxQrPoll.Status.ERROR)
        assertThat(TencentQrLoginClient.parseWeChatStatus("wx_errcode=404"))
            .isEqualTo(LxTxQrPoll.Status.SCANNED)
        assertThat(TencentQrLoginClient.parseWeChatStatus("wx_errcode=402"))
            .isEqualTo(LxTxQrPoll.Status.EXPIRED)
        assertThat(TencentQrLoginClient.parseWeChatStatus("wx_errcode=405;wx_code='auth-code'"))
            .isEqualTo(LxTxQrPoll.Status.SUCCESS)
        assertThat(TencentQrLoginClient.parseWeChatStatus("wx_errcode=405;wx_code=''"))
            .isEqualTo(LxTxQrPoll.Status.ERROR)
    }

    @Test
    fun `Tencent success requires a login cookie and OAuth redirects are scoped`() {
        assertThat(TencentQrLoginClient.hasTencentLoginCookie("other=x; qqmusic_key=secret")).isTrue()
        assertThat(TencentQrLoginClient.hasTencentLoginCookie("qm_keyst=secret")).isTrue()
        assertThat(TencentQrLoginClient.hasTencentLoginCookie("skey=secret")).isFalse()
        assertThat(TencentQrLoginClient.isTencentHost("graph.qq.com")).isTrue()
        assertThat(TencentQrLoginClient.isTencentHost("qq.com.attacker.test")).isFalse()
    }
}
