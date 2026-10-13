package com.gurio.gusic.data.lxmusic.kuwo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 魔改 DES 移植正确性由外部测试向量锁定（独立 Python 参照实现生成，key=17894932），
 * 改动任何置换表/S 盒/旋转掩码都会在此失败。
 */
class KuwoNativeCryptoTest {
    private val b64 = Base64.getDecoder()
    private val b64enc = Base64.getEncoder()

    private fun checkVector(plainB64: String, key: String, cipherB64: String) {
        val cipher = KuwoNativeCrypto.encrypt(b64.decode(plainB64), key.toByteArray(Charsets.UTF_8))
        assertEquals(cipherB64, b64enc.encodeToString(cipher))
        val back = KuwoNativeCrypto.decrypt(cipher, key.toByteArray(Charsets.UTF_8))
        assertTrue(b64.decode(plainB64).contentEquals(back))
    }

    @Test
    fun `independent reference vectors`() {
        checkVector("", "17894932", "vS7Ra0NdsLI=")
        checkVector("YQ==", "17894932", "/MGrwW08ByM=")
        checkVector("MTIzNDU2Nzg=", "17894932", "rEJmC4K0BMG9LtFrQ12wsg==")
        checkVector("dWlkPTAmc2lkPTA=", "17894932", "d907vJsh1BwQc0rZNDF88g==")
        checkVector(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uvs7e7v8PHy8/T19vf4+fr7/P3+/w==",
            "17894932",
            "5uFrLYTrNsS/Y/vPJdFxbKNjgP6ejSWx1dH0zQ2FTyySh4QLUqTo5z7xkMEm3Xux5TGu7mc6Z/WAEJaYceGHCZCp9Y1x22cFsBNuiNJD0aYkBUXAaGE0GqLGXjUztaYaD7TBstRFfRUYPafGzxStEY1XhS0wsWETLGChXKZWA7nGx9WwNLzeIN35B/R20cys+fzcXO/lDzGMz8zcilNIPg/c0C5IZMUXVBi8bIoFyuuNoFc2xkn2asMJxxdfqD+A/zcORnPiA0NE/M35AqyCYdrBunBtF+h/LoAXNbBZ4hDHl17zUMBvdedFFhQ30vc08csINWeCLTPL/3FC3dmYAr0u0WtDXbCy",
        )
        checkVector("5ZCN5a2XJisl", "17894932", "bf+DThio3JPMtI7Ss22Y1A==")
    }

    /**
     * 密钥编排覆盖：响应解密 key 是任意毫秒时间戳前 8 位数字，仅靠单一密钥
     * (17894932) 的向量不足以锁定 schedule 对各位模式的正确性；以下密钥由
     * Python 参照实现（kwproto.py）对同一段 login 明文生成，覆盖全 0/全 9/
     * 递增/一般值四种位模式。
     */
    @Test
    fun `schedule correctness across key bit patterns`() {
        val plain =
            "mobile=13800000000&code=24680&tm=server-token%2B%26%251&src=kwplayer_ar_12.2.2.0_newpcguanwangmobile.apk&version=12.2.2.0&dev_id=1234567890&dev_name=TuneWeave+SDK+client&devType=SDK&sx=17894932&from=android&devResolution=0*0"
        checkVector(
            java.util.Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8)),
            "00000000",
            "zes9kWZOJTRGmpYD1ts88UNc+cE/FOpvSjFVp+ksGX4hJoOznAsRB/mV+BgpXaOveVO0cscswLDb3l5np8nAf+loqNBC0cBOAxZp/F7dy2MgPkDbn8k06IZNWM9vN/1mZ0PlV4bWPyrrkPlvsDVtJCc0s3FyayYB7RNk1tiIM3OZai7e1UTdLZodkHR7q5BDUvwQGDckeih3SroREBeGMkjw7vaRzB/ss1gF2gFB8WWQ/R5IO8jtnQq+EA8WH7V3MjHu7ZFphEKJrQuEvw8iQyhpjRjyfYa//yUdKAsF3SX2hyLJqpX6FA==",
        )
        checkVector(
            java.util.Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8)),
            "99999999",
            "NkbU6jY6bHF3wagwyrMcvqiGAyl0Y5iyZZ/QFEqEZvpcWRAyWw4GxxHH5tWe8ypv3n5p0I9Oijoilup92zVFu29ASPbsNlmOcclipw/HtbfZQsQXa3cMBnGcOJPr0BVh229POYmkeneMvZaHLnZQLNVzjBIvjIA2c4S6aOULkIh48JqQccV9qrzBhu5s5WZ1u8hSMCCHk278EQnJe6UnPNDSH/ATpQvMV4Mzb1NiRobO9keZFFwFT9CxJ+6GAH6PE/aZi0M62YW29ICmyav3/se6fq8kiqX2GH1GsjNKBPeJDysv04YhfQ==",
        )
        checkVector(
            java.util.Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8)),
            "12345678",
            "MY6ZVAYcuVIqGEExUt6rrrKAf9pJ1QxHF7BLrRTQ+ZNoq9iP7ZAGNa/uyklkLI1CMfyNKwthhA83USCl09eX+sVZRvTRzWzHwCJIwy7VemVUYP0vL2JP8sMd7IUX4p2bjDPXb3LlRmbcG8fi+O9beI960XImR9zPqzY+8x+kjuHTwlivCmruEVO1JIwJLa7EL1L8gPseLyk/v05eiK4mZvDpEpWhj4SEopLcyLI1lahg+WJInUQS/1eqVPqQH+ZMjhyaohvj8pD7EVLPEpjjYRiGXMG1ECcXXlUxvbTaFCfNvpTJ7x2Yqw==",
        )
        checkVector(
            java.util.Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8)),
            "17895000",
            "lyAVgQkCLb7OQV28EI96A3NFpYx0DtCGWyOinm+XVWYNAmRy93Vlg63djwyhFxIsbFdgnfmbOeaFF8Y8/sStkBVF9I3CoJipyuGHIaUHVTRUC80t3tKCDPg3vj5AAIlZKMGUr3YSL2puWJiq809SK9B27rOUk4+XrL2qcl9GGHJp5eHdDeyxf0uklBKWvY7BTfb4604ejcmgOXVdZ64gqO2Uzq4xrq7MQkj5B1h2YiD8C8TZ+4Bai3E9/oa7h1qfKFkCmuhCREKowSKaToN8zjGUY2vy6aKQlAonV0g00hUjRaT/eb1m5g==",
        )
    }

    @Test
    fun `official anonymous error decrypts`() {
        val plain = KuwoNativeCrypto.openResponse(
            "rHf7RfVYYmRyE2hny0X/kU6OUt86lwPY/bUuAt1ZMqjUchYLFdi1tEMLKdnUejNhVLST48UCHb5xCAf6N2Pa5MLmb2D2tg3cwBsOhrlosohYdSarkYv5DRWphyUG8lqavS7Ra0NdsLI=",
            "17894932",
        )
        assertTrue(plain != null && plain.contains("\"result\":\"fail\""))
        assertTrue(plain != null && plain.contains("1136"))
    }

    @Test
    fun `synthetic native request seal`() {
        assertEquals(
            "eyEDmGUi7bMnGcbwsHwdIMNoqA7Q4a+8+tRSigV05DZc1Gu4VC/zFtvN+TR7BLSK8OCKPRsze8CEJ2OBSerOR6Rt5tik3UvA1QEK7KyJexpYYOimaNXu9lj/4o84ZYyOI3M2lWosNP8TiFjd8z0G+5QPgwHmkEXTFOhSFkbTyrc5NSTwE6oO7IICEbSR+TGsWjHidURhu5Ch0e3ALWmzaaR2QbrwgRwhNUQGO5e6HRunrZRr0LtPA/c08a6wKa48zwz8MdX0AH/3oxBU04aCCdmJaISAWTlZoE50+GPiM5E54RtU9IYB6698YzTDZ3wrJOq3zbyhfzE=",
            KuwoNativeCrypto.sealQuery(
                "uid=42&sid=session-42%26%2B%25&username=&password=&dev_id=123456789&dev_name=TuneWeave&user=device-user-123&src=kwplayer_ar_12.2.2.0_newpcguanwangmobile.apk&urlencode=1&devResolution=0*0&devType=arr&sx=17894932&from=android&version=12.2.2.0",
            ),
        )
    }

    @Test
    fun `sms send secret digest`() {
        assertEquals(
            "8767C392149F778EA9AE1DEE0CEDBAAE",
            KuwoNativeCrypto.smsSecret("13800000000", "1789500000123"),
        )
    }

    @Test
    fun `sms send query seal`() {
        assertEquals(
            "q9SUEF5l8EbVPIAt+a1Eqj8KlTw5JKt7yGejxr3dUE5JMGQM136LXu5zVE0Rp8x4rrBrsNE5esaqOGbw89/u8tHgio9dRU+AOyc3gi4ilyRWwlsVCqA0QSnt3tyAnfX2RiHGR7j5gmHbmyeiAJLm6K0INqcTwrc7igP2r9fKbUW4HkVAguL7cLMxaAd7eNvfttcR9Nvto0T7u6VmnSYXqna8mBav7psLDj14WIzJiaLBFnFA7o5TH5zjU671BhPZyHXOOWbpmQpaMZIjStFZZ4+QPQx0CYGW2bYHB5XP78e4JW2BBffyRO7qQscvWFaSEfLp97b8u/P3oxBU04aCCdmJaISAWTlZoE50+GPiM5E7IKwkq18ddorgGxC3u8EFw2j/Jv/PBgo=",
            KuwoNativeCrypto.sealQuery(
                "mobile=13800000000&type=4&tm=1789500000123&secret=8767C392149F778EA9AE1DEE0CEDBAAE&src=kwplayer_ar_12.2.2.0_newpcguanwangmobile.apk&version=12.2.2.0&dev_id=1234567890&user=00112233445546778899aabbccddeeff&dev_name=TuneWeave+SDK+client&devType=SDK&sx=17894932&from=android&devResolution=0*0",
            ),
        )
    }

    @Test
    fun `sms login query seal`() {
        assertEquals(
            "q9SUEF5l8EbVPIAt+a1Eqp5c8GeUmgJecARUFdOTCiIusorVOSIr/Oo9VH1uImjfkujlumaJwvKALspHvLD7EdnrnaLlON1Tr3xjNMNnfCtWU5IDuYZzXbdkrsxM1y9x8Nv+z7RGGJENa+3T2WwQnV1HHMrQ04hWrcwx4oxH+Rki82LuhJ64/MxbcTZ6uu3Z1QEK7KyJexoWMkaVMt17VjCdcs63WIS3lutAUA29RP6VESJlH5xhdaU2dLHQAjT+D7bO5Tm/P/ulvQNPHiqAU9JXm8JrO9j6orknR8j5Es4k6rfNvKF/MQ==",
            KuwoNativeCrypto.sealQuery(
                "mobile=13800000000&code=24680&tm=server-token%2B%26%251&src=kwplayer_ar_12.2.2.0_newpcguanwangmobile.apk&version=12.2.2.0&dev_id=1234567890&dev_name=TuneWeave+SDK+client&devType=SDK&sx=17894932&from=android&devResolution=0*0",
            ),
        )
    }

    @Test
    fun `response key takes first 8 digits of timestamp`() {
        assertEquals("17894932", KuwoNativeCrypto.responseKey(1789493212345L))
        assertEquals("99999999", KuwoNativeCrypto.responseKey(99999999999999L))
    }

    @Test
    fun `device register token is salted xor then base64`() {
        // "yeelion " 循环异或后应可逆
        val token = KuwoNativeCrypto.deviceRegisterToken("&new_user=1&mac=abc")
        val key = "yeelion ".toByteArray(Charsets.UTF_8)
        val decoded = Base64.getDecoder().decode(token)
        val plain = ByteArray(decoded.size) { i -> (decoded[i].toInt() xor key[i % key.size].toInt()).toByte() }
        assertEquals("&new_user=1&mac=abc", String(plain, Charsets.UTF_8))
    }

    @Test
    fun `openResponse rejects non digit or wrong length keys`() {
        assertEquals(null, KuwoNativeCrypto.openResponse("vS7Ra0NdsLI=", "1789493"))
        assertEquals(null, KuwoNativeCrypto.openResponse("vS7Ra0NdsLI=", "1789493a"))
        assertEquals(null, KuwoNativeCrypto.openResponse("!!!not-base64!!!", "17894932"))
    }
}
