// 对应 Folia 文件：src/components/app/playback/bridges/usePlaybackAudioBridge.ts（AnalyserNode fftSize 2048、smoothing 0.6）
//                src/components/app/playback/bridges/usePlaybackVisualizerBridge.ts（getEnergy 分频、process 提升、闲置呼吸）
//                src/stores/motionSignals.ts（audioPower / bass / lowMid / mid / vocal / treble，0..255 量纲）
//
// 手机化：Folia 从 Web Audio AnalyserNode 读频谱；这里在 ExoPlayer 音频链上挂 TeeAudioProcessor，
// 自己按 Web Audio 规范算（Blackman 窗 → FFT → |X|/N → 时间平滑 0.6 → dB → 字节 −100..−30 dB），
// 不需要录音权限（android.media.audiofx.Visualizer 要 RECORD_AUDIO）。
// 处理器拿到的是「写进 AudioTrack 之前」的数据，比耳朵听到的早一个缓冲（DefaultAudioSink PCM 约 250 ms），
// 读取时按 [OUTPUT_LATENCY_MS] 往回取历史，让电平跟声音对上。
package com.guangyue.core.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 一帧频段电平，量纲同 Folia motionSignals（0..255，已过 process 提升）。 */
data class AudioBands(
    val power: Double,
    val bass: Double,
    val lowMid: Double,
    val mid: Double,
    val vocal: Double,
    val treble: Double,
) {
    companion object {
        val ZERO = AudioBands(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /** Folia 不在播时的「呼吸」：(sin(Date.now()/2000)+1)·20，六路同值。 */
        fun breath(nowMs: Long): AudioBands {
            val b = (sin(nowMs / 2000.0) + 1) * 20
            return AudioBands(b, b, b, b, b, b)
        }
    }
}

/**
 * 纯 JVM 频谱分析（单测锁定）：喂单声道样本，每 [hop] 个样本出一帧 [AudioBands]。
 */
class SpectrumAnalyzer(private var sampleRate: Int = 44100) {
    companion object {
        const val FFT_SIZE = 2048
        const val SMOOTHING = 0.6
        const val MIN_DB = -100.0
        const val MAX_DB = -30.0

        /** Folia process：(v/255)^boost·255。 */
        fun process(v: Double, boost: Double): Double = (v / 255.0).coerceIn(0.0, 1.0).pow(boost) * 255.0

        /** Web Audio 规范的 Blackman 窗（α = 0.16）。 */
        private val WINDOW = DoubleArray(FFT_SIZE) { i ->
            val x = 2 * PI * i / FFT_SIZE
            0.42 - 0.5 * cos(x) + 0.08 * cos(2 * x)
        }
    }

    private val ring = DoubleArray(FFT_SIZE)
    private var pos = 0
    private var filled = 0
    private var sinceHop = 0
    private val smoothed = DoubleArray(FFT_SIZE / 2)
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    val bytes = IntArray(FFT_SIZE / 2)

    /** 约 60 帧/秒（Folia 每个 rAF 读一次 getByteFrequencyData）。 */
    val hop: Int get() = (sampleRate / 60).coerceAtLeast(64)

    fun reset(rate: Int) {
        sampleRate = rate.coerceAtLeast(8000)
        ring.fill(0.0); smoothed.fill(0.0); pos = 0; filled = 0; sinceHop = 0
    }

    /** 推一个样本；到一跳时返回新一帧，否则 null。 */
    fun push(sample: Double): AudioBands? {
        ring[pos] = sample
        pos = (pos + 1) % FFT_SIZE
        if (filled < FFT_SIZE) filled++
        sinceHop++
        if (sinceHop < hop) return null
        sinceHop = 0
        return analyze()
    }

    fun analyze(): AudioBands {
        for (i in 0 until FFT_SIZE) {
            re[i] = ring[(pos + i) % FFT_SIZE] * WINDOW[i]
            im[i] = 0.0
        }
        fft(re, im)
        val range = MAX_DB - MIN_DB
        for (k in 0 until FFT_SIZE / 2) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k]) / FFT_SIZE
            val s = SMOOTHING * smoothed[k] + (1 - SMOOTHING) * mag
            smoothed[k] = if (s.isFinite()) s else 0.0
            val db = if (smoothed[k] > 0) 20 * log10(smoothed[k]) else Double.NEGATIVE_INFINITY
            bytes[k] = floor(255.0 / range * (db - MIN_DB)).let { if (it.isFinite()) it.toInt().coerceIn(0, 255) else 0 }
        }
        val binHz = sampleRate.toDouble() / FFT_SIZE
        fun energy(minHz: Double, maxHz: Double): Double {
            val start = floor(minHz / binHz).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            val end = floor(maxHz / binHz).toInt().coerceIn(0, FFT_SIZE / 2 - 1)
            var sum = 0.0
            for (i in start..end) sum += bytes[i]
            val n = end - start + 1
            return if (n > 0) sum / n else 0.0
        }
        val bass = energy(20.0, 150.0)
        val lowMid = energy(150.0, 400.0)
        val mid = energy(400.0, 1200.0)
        val vocal = energy(1000.0, 3500.0)
        val treble = energy(3500.0, 12000.0)
        return AudioBands(
            power = process((bass + lowMid) / 2, 3.0),
            bass = process(bass, 1.8),
            lowMid = process(lowMid, 2.0),
            mid = process(mid, 2.0),
            vocal = process(vocal, 1.5),
            treble = process(treble, 2.0),
        )
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * 全局音频电平（对应 Folia motionSignals 的 audioPower / audioBands）。
 * 写：播放线程（TeeAudioProcessor 回调）；读：界面每帧 [read]。
 */
@UnstableApi
object AudioSpectrum : TeeAudioProcessor.AudioBufferSink {
    /** 处理器早于出声的时间（DefaultAudioSink PCM 缓冲约 250 ms + 设备输出延迟）。 */
    const val OUTPUT_LATENCY_MS = 280L

    /** 超过这么久没新数据就当没在出声（缓冲耗尽 / 暂停），回到呼吸。 */
    private const val STALE_MS = 450L
    private const val HISTORY = 64

    private val analyzer = SpectrumAnalyzer()
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private val lock = Any()
    private val histT = LongArray(HISTORY)
    private val histB = arrayOfNulls<AudioBands>(HISTORY)
    private var head = 0
    private var count = 0

    /** 播放器是否在出声（PlaybackController 写）。 */
    @Volatile var playing: Boolean = false

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        analyzer.reset(sampleRateHz)
        synchronized(lock) { count = 0; head = 0 }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val ch = channels
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (b.remaining() >= 2 * ch) {
                    var s = 0.0
                    for (c in 0 until ch) s += b.short / 32768.0
                    emit(analyzer.push(s / ch))
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (b.remaining() >= 4 * ch) {
                    var s = 0.0
                    for (c in 0 until ch) s += b.float
                    emit(analyzer.push(s / ch))
                }
            }
            else -> Unit // 24/32 位整型极少见，不算（读到的是呼吸）
        }
    }

    private fun emit(bands: AudioBands?) {
        if (bands == null) return
        val now = System.nanoTime()
        synchronized(lock) {
            histT[head] = now
            histB[head] = bands
            head = (head + 1) % HISTORY
            if (count < HISTORY) count++
        }
    }

    /**
     * 当前「耳朵听到的」频段电平；没在出声时给 Folia 的呼吸值。
     */
    fun read(nowNanos: Long = System.nanoTime()): AudioBands {
        if (!playing) return AudioBands.breath(System.currentTimeMillis())
        val target = nowNanos - OUTPUT_LATENCY_MS * 1_000_000
        synchronized(lock) {
            if (count == 0) return AudioBands.breath(System.currentTimeMillis())
            val newest = (head - 1 + HISTORY) % HISTORY
            if (nowNanos - histT[newest] > (OUTPUT_LATENCY_MS + STALE_MS) * 1_000_000) {
                return AudioBands.breath(System.currentTimeMillis())
            }
            // 从新往旧找第一帧 ≤ target；全都比 target 新（刚起播）就取最旧的。
            var idx = newest
            for (n in 0 until count) {
                val i = (newest - n + HISTORY) % HISTORY
                idx = i
                if (histT[i] <= target) break
            }
            return histB[idx] ?: AudioBands.ZERO
        }
    }
}

/** 给界面模块的门面：不露出 media3 类型（feature 模块不直接依赖 media3）。 */
object AudioLevels {
    fun read(nowNanos: Long = System.nanoTime()): AudioBands = AudioSpectrum.read(nowNanos)
}
