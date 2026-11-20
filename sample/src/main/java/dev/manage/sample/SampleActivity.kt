package dev.manage.sample

import android.app.Application
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dev.manage.ads.AdsManager
import kotlinx.coroutines.launch

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsManager.install(this)
    }
}

class SampleActivity : ComponentActivity() {
    private var homeOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "正在准备主页…" })
        val restoringHome = savedInstanceState?.getBoolean("home") == true
        requestPermission { allowed ->
            lifecycleScope.launch {
                lifecycle.withResumed { }
                if (restoringHome) AdsManager.setRequestPermission(allowed)
                else AdsManager.runStartup(this@SampleActivity, allowed)
                openHome()
            }
        }
    }

    private fun requestPermission(complete: (Boolean) -> Unit) {
        if (!AdsManager.isEnabled) {
            complete(false)
            return
        }
        val consent = UserMessagingPlatform.getConsentInformation(this)
        fun finish() {
            if (!isDestroyed && !isFinishing) complete(consent.canRequestAds())
        }
        consent.requestConsentInfoUpdate(this, ConsentRequestParameters.Builder().build(), {
            if (!isDestroyed && !isFinishing) {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { finish() }
            }
        }, { finish() })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("home", homeOpened)
    }

    private fun openHome() {
        homeOpened = true
        renderHome()
    }

    private fun renderHome() {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(this).apply { text = "主页" }
        column.addView(status)
        column.addView(Button(this).apply {
            text = "展示插屏"
            setOnClickListener {
                lifecycleScope.launch {
                    status.text = AdsManager.showInterstitial(this@SampleActivity, "editor_exit").name
                    AdsManager.preload("editor_exit")
                }
            }
        })
        val banner = FrameLayout(this)
        val native = FrameLayout(this)
        column.addView(banner)
        column.addView(native)
        setContentView(column)
        bindBanner(banner, "home_banner", this)
        bindNative(native, "home_native", this)
    }
}
