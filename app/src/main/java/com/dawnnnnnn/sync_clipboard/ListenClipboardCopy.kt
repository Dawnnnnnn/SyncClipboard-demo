package com.dawnnnnnn.sync_clipboard

import android.app.AndroidAppHelper
import android.content.ClipboardManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.api.listener.MessageListener
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import java.io.IOException
import java.lang.Exception
import java.util.Base64


val redisAddr: String = "redis://" + System.getenv("redisAddr")
val redisPassword: String? = System.getenv("redisPassword")
val deviceToken: String? = System.getenv("deviceToken")
val topicKey: String? = System.getenv("topicKey")

const val PackageName = BuildConfig.APPLICATION_ID

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}


object RedisUtils {
    const val TAG = "RedisUtils"
    private var redissonClient: RedissonClient? = null

    @Synchronized
    @Throws(IOException::class)
    fun redisson(): RedissonClient {
        val config = Config()
        config.codec = StringCodec()
        config.useSingleServer().setAddress(redisAddr).password = redisPassword
        return Redisson.create(config)
    }

    fun initClient() {
        try {
            redissonClient = redisson()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun publishTopic(topic: String, message: String): Long? {
        if (redissonClient == null) {
            initClient()
        }
        if (redissonClient == null) {
            Log.w(
                TAG,
                "redis publish failed, topic = [$topic], message = [$message]"
            )
            return -1L
        }
        return try {
            val result = redissonClient!!.getTopic(topic).publish(message)
            Log.i(
                TAG, "redis publish success, topic = [" + topic + "], " +
                        "message = [" + message + "], [ " + result + " ] client received it"
            )
            result
        } catch (e: Exception) {
            Log.e(
                TAG,
                "redis publish error, topic = [$topic], message = [$message]", e
            )
            -1L
        }
    }

    fun subscribeTopic(topic: String?, messageListener: MessageListener<String>?): Boolean {
        if (redissonClient == null) {
            initClient()
        }
        if (topic == null) {
            Log.w(TAG, "subscribe topic [ $topic ] failed, topic is null")
            return false
        }
        if (redissonClient == null) {
            Log.e(TAG, "subscribe topic [ $topic ] failed")
            return false
        }
        return try {
            val rTopic = redissonClient!!.getTopic(topic)
            Log.e(TAG, "subscribe topic [ $topic ] begin")
            if (rTopic.countListeners() <= 0) {
                rTopic.addListener(String::class.java, messageListener)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

object ClipboardHelper {

    private const val LABEL = "LCountdown"

    fun put(context: Context, value: String): Boolean {
        return try {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(LABEL, value)
            clipboardManager.setPrimaryClip(clipData)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}


class ListenClipboardCopy : IXposedHookLoadPackage {
    private val mHookPasteData: XC_MethodHook = object : XC_MethodHook() {
        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            val clipData = param.args[0] as ClipData
            val clipStr = clipData.getItemAt(0).text.toString()
            val encodedString: String = Base64.getEncoder().encodeToString(clipStr.toByteArray())
            val pushJson =
                "{\"type\":\"text\",\"msg\":\"%s\",\"uuid\":\"%s\"}".format(
                    encodedString,
                    deviceToken
                )
            if (topicKey != null) {
                RedisUtils.publishTopic(topicKey, pushJson)
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam?) {
        if (loadPackageParam?.packageName.equals(PackageName)) {
            RedisUtils.subscribeTopic(topicKey, object : MessageListener<String> {
                override fun onMessage(channel: CharSequence, msg: String) {
                    val jsonObject = JSONObject(msg)
                    val jsonMsg = jsonObject.getString("msg")
                    val jsonDeviceToken = jsonObject.getString("uuid")
                    if (jsonDeviceToken != deviceToken) {
                        val decodedBytes = Base64.getDecoder().decode(jsonMsg)
                        val decodedString = String(decodedBytes)
                        val context = AndroidAppHelper.currentApplication().applicationContext
                        ClipboardHelper.put(context, decodedString)
                    }
                }
            })
        }
        XposedHelpers.findAndHookMethod(
            ClipboardManager::class.java, "setPrimaryClip",
            ClipData::class.java, mHookPasteData
        )
    }
}