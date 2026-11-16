package com.haira.ai.next
import org.junit.Assert.assertEquals
import org.junit.Test
class SecondApkIdentityTest {
    @Test fun secondApkUsesIndependentApplicationId() {
        assertEquals("com.haira.ai.next", BuildConfig.APPLICATION_ID)
    }
}
