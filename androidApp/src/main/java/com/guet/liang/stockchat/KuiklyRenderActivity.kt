package com.guet.liang.stockchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.guet.liang.stockchat.adapter.KRColorParserAdapter
import com.guet.liang.stockchat.adapter.KRFontAdapter
import com.guet.liang.stockchat.adapter.KRImageAdapter
import com.guet.liang.stockchat.adapter.KRLogAdapter
import com.guet.liang.stockchat.adapter.KRRouterAdapter
import com.guet.liang.stockchat.adapter.KRThreadAdapter
import com.guet.liang.stockchat.adapter.KRUncaughtExceptionHandlerAdapter
import com.guet.liang.stockchat.module.KRBridgeModule
import com.guet.liang.stockchat.module.KRShareModule
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)
    private var microphonePermissionCallback: ((Boolean) -> Unit)? = null
    private var drawerGestureCallback: ((String) -> Unit)? = null
    private var drawerGestureStartX = 0f
    private var drawerGestureStartY = 0f

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                "router"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupEdgeToEdge()
        setContentView(R.layout.activity_hr)
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
    }

    override fun onDestroy() {
        microphonePermissionCallback = null
        drawerGestureCallback = null
        kuiklyRenderViewDelegator.onDetach()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        trackDrawerGesture(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return
        }
        val callback = microphonePermissionCallback
        microphonePermissionCallback = null
        callback?.invoke(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {

        }
    }

    override fun softInputMode(): Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

    private fun createPageData(): Map<String, Any> {
        val param = argsToMap()
        param["appId"] = 1
        param["mimoApiKey"] = BuildConfig.MIMO_API_KEY
        param["mimoNativeStreaming"] = 1
        return param
    }

    internal fun requestRecordAudioPermission(callback: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            callback(true)
            return
        }
        if (isFinishing || isDestroyed) {
            callback(false)
            return
        }
        microphonePermissionCallback = callback
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION,
        )
    }

    internal fun setDrawerGestureCallback(callback: ((String) -> Unit)?) {
        drawerGestureCallback = callback
    }

    private fun trackDrawerGesture(event: MotionEvent) {
        if (drawerGestureCallback == null) {
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drawerGestureStartX = event.rawX
                drawerGestureStartY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.rawX - drawerGestureStartX
                val deltaY = event.rawY - drawerGestureStartY
                val threshold = DRAWER_SWIPE_DISTANCE_DP * resources.displayMetrics.density
                if (kotlin.math.abs(deltaX) >= threshold &&
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
                ) {
                    drawerGestureCallback?.invoke(if (deltaX > 0f) "right" else "left")
                }
            }
        }
    }

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(Color.rgb(246, 247, 244))
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 2001
        private const val DRAWER_SWIPE_DISTANCE_DP = 48f

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}
