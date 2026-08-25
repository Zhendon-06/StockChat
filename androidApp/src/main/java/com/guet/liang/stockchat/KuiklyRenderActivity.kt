package com.guet.liang.stockchat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)
    private var microphonePermissionCallback: ((Boolean) -> Unit)? = null
    private var imagePickerCallback: ((ImagePickerResult) -> Unit)? = null
    private var imagePickerMaxCount = MAX_IMAGE_SELECTION_COUNT
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
        imagePickerCallback = null
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_IMAGES) {
            return
        }
        val callback = imagePickerCallback ?: return
        imagePickerCallback = null
        if (resultCode != RESULT_OK) {
            callback(ImagePickerResult(cancelled = true))
            return
        }

        val selectedUris = linkedSetOf<Uri>()
        data?.clipData?.let { clipData ->
            repeat(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri?.let(selectedUris::add)
            }
        }
        data?.data?.let(selectedUris::add)
        if (selectedUris.isEmpty()) {
            callback(ImagePickerResult(cancelled = true))
            return
        }

        selectedUris.forEach(::persistImageReadPermission)
        val selectedUriList = selectedUris.toList()
        Thread {
            val preparedImages = selectedUriList.take(imagePickerMaxCount).map { uri ->
                prepareImageAttachment(uri)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                if (preparedImages.any { it == null }) {
                    callback(
                        ImagePickerResult(
                            errorCode = "IMAGE_READ_FAILED",
                            errorMessage = "部分图片读取失败，请重新选择。",
                        ),
                    )
                    return@runOnUiThread
                }
                val successfulImages = preparedImages.filterNotNull()
                callback(
                    ImagePickerResult(
                        images = successfulImages.map(PreparedImageAttachment::requestDataUri),
                        previewImages = successfulImages.map(PreparedImageAttachment::previewUri),
                        truncated = selectedUriList.size > imagePickerMaxCount,
                    ),
                )
            }
        }.start()
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
        param["qwenApiKey"] = BuildConfig.QWEN_API_KEY
        param["mimoVoiceApiKey"] = BuildConfig.MIMO_VOICE_API_KEY
        param["aliyunNativeStreaming"] = 1
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

    @Suppress("DEPRECATION")
    internal fun pickImages(maxCount: Int, callback: (ImagePickerResult) -> Unit) {
        if (isFinishing || isDestroyed) {
            callback(
                ImagePickerResult(
                    errorCode = "ACTIVITY_UNAVAILABLE",
                    errorMessage = "当前页面无法打开图片选择器。",
                ),
            )
            return
        }
        if (imagePickerCallback != null) {
            callback(
                ImagePickerResult(
                    errorCode = "IMAGE_PICKER_BUSY",
                    errorMessage = "图片选择器已打开。",
                ),
            )
            return
        }

        imagePickerMaxCount = maxCount.coerceIn(1, MAX_IMAGE_SELECTION_COUNT)
        val pickerIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                if (imagePickerMaxCount > 1) {
                    putExtra(
                        MediaStore.EXTRA_PICK_IMAGES_MAX,
                        minOf(imagePickerMaxCount, MediaStore.getPickImagesMaxLimit()),
                    )
                }
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, imagePickerMaxCount > 1)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
        }
        imagePickerCallback = callback
        runCatching {
            startActivityForResult(pickerIntent, REQUEST_PICK_IMAGES)
        }.onFailure { throwable ->
            imagePickerCallback = null
            callback(
                ImagePickerResult(
                    errorCode = "IMAGE_PICKER_UNAVAILABLE",
                    errorMessage = throwable.message ?: "当前设备无法打开图片选择器。",
                ),
            )
        }
    }

    private fun persistImageReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
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

    private fun prepareImageAttachment(uri: Uri): PreparedImageAttachment? {
        return runCatching {
            val (width, height) = contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                options.outWidth to options.outHeight
            } ?: return null
            val maxDimension = 2048
            var sampleSize = 1
            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )
            } ?: return null
            try {
                val jpegBytes = ByteArrayOutputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                        return null
                    }
                    output.toByteArray()
                }
                val previewDirectory = File(filesDir, IMAGE_PREVIEW_DIRECTORY)
                if (!previewDirectory.exists() && !previewDirectory.mkdirs()) {
                    return null
                }
                val previewFile = File(previewDirectory, "image_${uri.toString().hashCode()}.jpg")
                previewFile.outputStream().use { output -> output.write(jpegBytes) }
                val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                PreparedImageAttachment(
                    previewUri = Uri.fromFile(previewFile).toString(),
                    requestDataUri = "data:image/jpeg;base64,$base64",
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 2001
        private const val REQUEST_PICK_IMAGES = 2002
        private const val IMAGE_PREVIEW_DIRECTORY = "chat_images"
        private const val MAX_IMAGE_SELECTION_COUNT = 9
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

private data class PreparedImageAttachment(
    val previewUri: String,
    val requestDataUri: String,
)

internal data class ImagePickerResult(
    val images: List<String> = emptyList(),
    val previewImages: List<String> = emptyList(),
    val cancelled: Boolean = false,
    val truncated: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
