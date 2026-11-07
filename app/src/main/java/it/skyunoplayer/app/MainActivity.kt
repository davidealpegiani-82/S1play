package it.skyunoplayer.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    private data class Channel(val name: String, val url: String)
    private val channels = listOf(
        Channel("SKY UNO FULL HD", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/29.m3u8"),
        Channel("SKY UNO HD", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/28.m3u8"),
        Channel("SKY UNO 4K HEVC H265", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/6667.m3u8"),
        Channel("SKY UNO SD", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/27.m3u8"),
        Channel("SKY UNO +1", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/5521.m3u8"),
        Channel("SKY UNO PLUS FULL HD", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/52992.m3u8"),
        Channel("SKY UNO PLUS HD", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/52993.m3u8"),
        Channel("SKY UNO PLUS SD", "http://tacotaco61.inimdns.biz:80/live/alessiomgj/3CD3YJ2wpF/52994.m3u8")
    )
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var player: ExoPlayer
    private lateinit var channelButtons: List<Button>
    private val prefs by lazy { getSharedPreferences("sky_uno_player", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var activeIndex = -1
    private var retryCount = 0
    private var retryRunnable: Runnable? = null
    private var fullscreen = false
    private var diagnosticIndex = -1
    private var diagnosticResults = mutableListOf<String>()
    private var diagnosticTimeout: Runnable? = null
    private var diagnosticListener: Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 13, 20)
        window.navigationBarColor = Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 13, 20))
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title = TextView(this).apply {
            text = "SKY UNO PLAYER"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val full = Button(this).apply {
            text = "⛶"
            contentDescription = "Schermo intero"
            setOnClickListener { toggleFullscreen() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(full, LinearLayout.LayoutParams(dp(56), dp(44)))
        root.addView(header)

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            text = "Seleziona una qualità per avviare SKY UNO"
            textSize = 13f
            setTextColor(Color.rgb(190, 200, 215))
            setPadding(dp(4), dp(9), dp(4), dp(9))
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "QUALITÀ DISPONIBILI"
            textSize = 12f
            setTextColor(Color.rgb(145, 160, 180))
            setPadding(dp(2), dp(6), dp(2), dp(6))
        })

        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        channelButtons = channels.mapIndexed { index, _ ->
            Button(this).apply {
                text = listOf("FULL HD", "HD", "4K", "SD", "+1", "+1 FHD", "+1 HD", "+1 SD")[index]
                textSize = 11f
                isAllCaps = false
                setOnClickListener { playChannel(index) }
            }.also { strip.addView(it, LinearLayout.LayoutParams(-2, dp(48)).apply { marginEnd = dp(6) }) }
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "↻ Riprova"
            setOnClickListener { if (activeIndex >= 0) playChannel(activeIndex) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(Button(this).apply {
            text = "Verifica flussi"
            setOnClickListener { runDiagnostics() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)
        setContentView(root)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (diagnosticIndex >= 0) {
                    if (state == Player.STATE_READY) finishDiagnostic("OK")
                    return
                }
                if (activeIndex >= 0 && state == Player.STATE_BUFFERING)
                    setStatus("Connessione a ${channels[activeIndex].name}…")
                if (activeIndex >= 0 && state == Player.STATE_READY) {
                    retryCount = 0
                    setStatus("In riproduzione · ${channels[activeIndex].name}")
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (diagnosticIndex >= 0) {
                    finishDiagnostic("Non disponibile")
                } else if (activeIndex >= 0) {
                    setStatus("Errore di rete o flusso non disponibile. Nuovo tentativo…")
                    scheduleRetry(activeIndex)
                }
            }
        })
        playChannel(prefs.getInt("last_channel", 0).coerceIn(0, channels.lastIndex))
    }

    private fun playChannel(index: Int) {
        if (index !in channels.indices) return
        stopDiagnostics()
        retryRunnable?.let(handler::removeCallbacks)
        retryRunnable = null
        retryCount = 0
        activeIndex = index
        prefs.edit().putInt("last_channel", index).apply()
        title.text = channels[index].name
        channelButtons.forEachIndexed { i, b -> b.alpha = if (i == index) 1f else 0.72f }
        setStatus("Connessione a ${channels[index].name}…")
        player.stop()
        player.setMediaItem(MediaItem.fromUri(channels[index].url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun scheduleRetry(index: Int) {
        if (retryCount >= 3) {
            setStatus("Non disponibile dopo 3 tentativi. Prova un'altra qualità.")
            return
        }
        retryCount++
        val delay = (2000L shl (retryCount - 1)).coerceAtMost(12000L)
        retryRunnable = Runnable { if (activeIndex == index) playChannel(index) }
        handler.postDelayed(retryRunnable!!, delay)
    }

    private fun runDiagnostics() {
        retryRunnable?.let(handler::removeCallbacks)
        retryRunnable = null
        stopDiagnostics()
        diagnosticResults = MutableList(channels.size) { "In attesa" }
        diagnosticIndex = 0
        player.stop()
        testNextDiagnostic()
    }

    private fun testNextDiagnostic() {
        if (diagnosticIndex !in channels.indices) {
            activeIndex = -1
            player.stop()
            setStatus(diagnosticResults.mapIndexed { i, s -> "${i + 1}: $s" }.joinToString(" · "))
            diagnosticIndex = -1
            return
        }
        val i = diagnosticIndex
        setStatus("Verifica ${i + 1}/${channels.size} · ${channels[i].name}")
        player.setMediaItem(MediaItem.fromUri(channels[i].url))
        player.prepare()
        player.playWhenReady = true
        diagnosticTimeout = Runnable { finishDiagnostic("Timeout") }
        handler.postDelayed(diagnosticTimeout!!, 12000)
    }

    private fun finishDiagnostic(result: String) {
        if (diagnosticIndex !in channels.indices) return
        diagnosticTimeout?.let(handler::removeCallbacks)
        diagnosticTimeout = null
        diagnosticResults[diagnosticIndex] = result
        diagnosticIndex++
        handler.post { testNextDiagnostic() }
    }

    private fun stopDiagnostics() {
        diagnosticTimeout?.let(handler::removeCallbacks)
        diagnosticTimeout = null
        diagnosticIndex = -1
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (fullscreen) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun setStatus(message: String) { status.text = message }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        retryRunnable?.let(handler::removeCallbacks)
        stopDiagnostics()
        if (::player.isInitialized) {
            playerView.player = null
            player.release()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
