package com.github.k1rakishou.chan.core.site.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyResponseTest {

  @Test
  fun copyKeepsCaptchaFlags() {
    val original = ReplyResponse().also { response ->
      response.requireAuthentication = true
      response.captchaMistyped = true
    }

    val copy = ReplyResponse(original)

    assertTrue(copy.requireAuthentication)
    assertTrue(copy.captchaMistyped)
  }

  @Test
  fun captchaIsNotMistypedByDefault() {
    val response = ReplyResponse()

    assertFalse(response.requireAuthentication)
    assertFalse(response.captchaMistyped)
    assertFalse(ReplyResponse(response).captchaMistyped)
  }
}
