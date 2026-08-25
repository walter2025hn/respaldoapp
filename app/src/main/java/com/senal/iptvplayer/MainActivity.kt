package com.senal.iptvplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.widget.PopupMenu

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    // Native player overlay (same activity — leaving it never reloads the WebView)
    private lateinit var playerOverlay: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var titleOverlay: TextView
    private lateinit var topControlsBar: android.widget.LinearLayout
    private lateinit var aspectBtn: Button
    private lateinit var loadingSpinner: ProgressBar
    private var nativePlayer: ExoPlayer? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
            filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            filePathCallback = null
        }

    inner class WebAppInterface {
        // Plays a stream in the native in-app overlay (live TV, movies, series —
        // everything). Doesn't leave MainActivity, so the WebView underneath
        // (your channel list, section, scroll position) is never touched.
        @JavascriptInterface
        fun playNative(url: String, title: String) {
            runOnUiThread { openNativePlayer(url, title) }
        }

        // Kept as a manual fallback in case a particular stream truly won't
        // play through ExoPlayer either.
        @JavascriptInterface
        fun openExternal(url: String, title: String) {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "video/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, title)
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(chooser)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "No se encontró un reproductor externo instalado.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)

        playerOverlay = findViewById(R.id.native_player_overlay)
        playerView = findViewById(R.id.playerView)
        titleOverlay = findViewById(R.id.titleOverlay)
        topControlsBar = findViewById(R.id.topControlsBar)
        aspectBtn = findViewById(R.id.aspectBtn)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        findViewById<Button>(R.id.exitPlayerBtn).setOnClickListener { closeNativePlayer() }
        aspectBtn.setOnClickListener { showAspectMenu(it) }

        // Hide the title + "Formato/Salir" buttons together with the player's
        // own controls: tap the screen to reveal, auto-hide after 5s idle.
        playerView.controllerShowTimeoutMs = 5000
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                titleOverlay.visibility = visibility
                topControlsBar.visibility = visibility
                aspectBtn.visibility = visibility
            }
        )

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer?.visibility = View.VISIBLE
                fullscreenContainer?.addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                fullscreenContainer?.visibility = View.GONE
                fullscreenContainer?.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                webView.visibility = View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback = callback
                val intent = params?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        webView.loadUrl("file:///android_asset/www/index.html")
        webView.requestFocus()
    }

    private fun openNativePlayer(url: String, title: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "URL de transmisión inválida.", Toast.LENGTH_LONG).show()
            return
        }

        titleOverlay.text = title
        titleOverlay.visibility = View.VISIBLE
        topControlsBar.visibility = View.VISIBLE
        aspectBtn.visibility = View.VISIBLE
        playerOverlay.visibility = View.VISIBLE
        loadingSpinner.visibility = View.VISIBLE
        applyAspectMode("fit") // reset to the default fit every time a new stream starts

        // Reuse a single ExoPlayer instance across plays instead of leaking one per stream.
        val exoPlayer = nativePlayer ?: buildTunedPlayer().also {
            nativePlayer = it
            playerView.player = it
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    loadingSpinner.visibility =
                        if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                }

                override fun onPlayerError(error: PlaybackException) {
                    loadingSpinner.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo reproducir: " + (error.message ?: "error desconocido"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        // LiveConfiguration only takes effect when the stream turns out to be
        // live (HLS #EXT-X-PLAYLIST-TYPE live/event or no ENDLIST tag) — ExoPlayer
        // ignores it for VOD/movies/series, so it's safe to always set it.
        // A short target offset means "stay close to the live edge" instead of
        // the ~30s default some IPTV panels push, which is what usually reads
        // as "delay" on live channels.
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(4000)
                    .setMinPlaybackSpeed(0.96f)
                    .setMaxPlaybackSpeed(1.06f)
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playerView.requestFocus()
    }

    // Tuned ExoPlayer: starts playback sooner (smaller "buffer before play"),
    // still keeps a healthy buffer once playing so it doesn't stall on a weak
    // connection, uses real HTTP timeouts so a dead server fails fast instead
    // of hanging, and follows cross-protocol (http->https) redirects that a
    // lot of IPTV panels do.
    @OptIn(UnstableApi::class)
    private fun buildTunedPlayer(): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SenalIPTV/1.0 (Linux; Android) ExoPlayerLib/1.4.1")
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            // min, max, buffer-before-first-play, buffer-before-resume-after-stall (ms)
            .setBufferDurationsMs(15_000, 50_000, 1_200, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also {
                it.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
                it.setHandleAudioBecomingNoisy(true)
            }
    }

    private fun closeNativePlayer() {
        nativePlayer?.pause()
        nativePlayer?.stop()
        playerOverlay.visibility = View.GONE
        // The WebView underneath was never touched — the person lands right
        // back on the same section/list/scroll position they left.
    }

    // ---------------- Screen format (aspect ratio) ----------------
    private fun showAspectMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Ajustar (recomendado)")
        popup.menu.add(0, 2, 1, "Pantalla completa")
        popup.menu.add(0, 3, 2, "16:10")
        popup.menu.add(0, 4, 3, "4:3")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> applyAspectMode("fit")
                2 -> applyAspectMode("full")
                3 -> applyAspectMode("16:10")
                4 -> applyAspectMode("4:3")
            }
            true
        }
        popup.show()
    }

    private fun applyAspectMode(mode: String) {
        val overlayW = playerOverlay.width
        val overlayH = playerOverlay.height

        when (mode) {
            "fit" -> {
                // Default: shows the whole picture as the source intended,
                // letterboxed with black bars if needed — no cropping, no stretching.
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setPlayerViewSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            "full" -> {
                // Fills the entire screen with no black bars, cropping any
                // excess instead of stretching (keeps the picture undistorted).
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setPlayerViewSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            "16:10", "4:3" -> {
                if (overlayW <= 0 || overlayH <= 0) return
                val ratio = if (mode == "16:10") 16f / 10f else 4f / 3f
                var boxW = overlayW
                var boxH = (boxW / ratio).toInt()
                if (boxH > overlayH) {
                    boxH = overlayH
                    boxW = (boxH * ratio).toInt()
                }
                // Forces the picture into the chosen frame shape (stretching
                // to fit it exactly), matching the classic TV "16:10 / 4:3" toggle.
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                setPlayerViewSize(boxW, boxH)
            }
        }
    }

    private fun setPlayerViewSize(width: Int, height: Int) {
        val params = playerView.layoutParams as FrameLayout.LayoutParams
        params.width = width
        params.height = height
        params.gravity = android.view.Gravity.CENTER
        playerView.layoutParams = params
    }

    override fun onStop() {
        super.onStop()
        nativePlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        nativePlayer?.release()
        nativePlayer = null
    }

    override fun onBackPressed() {
        when {
            playerOverlay.visibility == View.VISIBLE -> closeNativePlayer()
            customView != null -> webView.webChromeClient?.onHideCustomView()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }
}
