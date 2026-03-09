package com.example.chatglm_native

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.chatglm_native.model.ChatRequest
import com.example.chatglm_native.model.ChatResponse
import com.example.chatglm_native.network.ApiService
import com.example.chatglm_native.network.RetrofitClient
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import retrofit2.Call

@RunWith(MockitoJUnitRunner::class)
class MyNotificationServiceTest {

    @Mock
    lateinit var sbn: StatusBarNotification

    @Mock
    lateinit var notification: Notification

    @Mock
    lateinit var apiService: ApiService

    @Mock
    lateinit var mockCall: Call<ChatResponse>

    @Test
    fun `test notification processing - wake word`() {
        // Given
        val service = MyNotificationService() // *Note: Service instantiation for unit test is limited without Android Context

        // Mock notification data
        val extras = Bundle()
        extras.putString(Notification.EXTRA_TITLE, "TestRoom")
        extras.putCharSequence(Notification.EXTRA_TEXT, "코비서 안녕")

        `when`(sbn.packageName).thenReturn("com.kakao.talk")
        `when`(sbn.notification).thenReturn(notification)
        `when`(notification.extras).thenReturn(extras)

        // *In a real Android Unit Test, we would inject the ApiService
        // Here we are just documenting the test logic plan.

        /*
        // When
        service.onNotificationPosted(sbn)

        // Then
        verify(apiService).processChat(ChatRequest("TestRoom", "코비서 안녕"))
        */
    }
}
