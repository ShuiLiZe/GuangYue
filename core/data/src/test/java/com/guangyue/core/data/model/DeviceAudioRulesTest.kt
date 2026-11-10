package com.guangyue.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAudioRulesTest {

    private fun row(path: String, dur: Long = 200_000L) =
        DeviceAudioRules.Row(path = path, displayName = path.substringAfterLast('/'), durationMs = dur)

    @Test fun `正常歌曲保留`() {
        assertTrue(DeviceAudioRules.accept(row("/storage/emulated/0/Music/周杰伦 - 晴天.mp3")))
        assertTrue(DeviceAudioRules.accept(row("/storage/emulated/0/Music/GuangYue/发如雪.flac")))
        assertTrue(DeviceAudioRules.accept(row("/storage/emulated/0/Download/Call Me Maybe.mp3")))
        assertTrue(DeviceAudioRules.accept(row("Music/Record Label Hits/Song.mp3")))
        // 时长读不到（0）不按时长排除
        assertTrue(DeviceAudioRules.accept(row("/storage/emulated/0/Music/a.mp3", dur = 0L)))
    }

    @Test fun `录音目录排除`() {
        val paths = listOf(
            "/storage/emulated/0/Recordings/Call/通话 张三.m4a",
            "/storage/emulated/0/Recordings/Voice Recorder/语音 001.m4a",
            "/storage/emulated/0/MIUI/sound_recorder/call_rec/abc.mp3",
            "/storage/emulated/0/Sounds/CallRecord/xyz.amr",
            "/storage/emulated/0/Call/xyz.mp3",
            "/storage/emulated/0/record/xyz.mp3",
            "/storage/emulated/0/录音/会议.mp3",
            "/storage/emulated/0/通话录音/张三.mp3",
            "/storage/emulated/0/Music/Recordings/demo.mp3",
            "/storage/emulated/0/Ringtones/beep.ogg",
            "/storage/emulated/0/Notifications/ding.ogg",
            "/storage/emulated/0/Alarms/wake.mp3",
            "Recordings/Call/a.m4a",
        )
        paths.forEach { p -> assertEquals(p, "path", DeviceAudioRules.rejectReason(row(p))) }
    }

    @Test fun `大小写不敏感`() {
        assertFalse(DeviceAudioRules.accept(row("/storage/emulated/0/RECORDINGS/a.mp3")))
        assertFalse(DeviceAudioRules.accept(row("/storage/emulated/0/callrecord/a.mp3")))
        assertFalse(DeviceAudioRules.accept(row("/storage/emulated/0/CallRecordings/a.mp3")))
    }

    @Test fun `文件名本身不算目录`() {
        // 歌名叫 Recording 的歌不该因为文件名被当成目录排除
        assertTrue(DeviceAudioRules.isExcludedPath("Recording.mp3").not())
        assertFalse(DeviceAudioRules.isExcludedPath("/storage/emulated/0/Music/Recording.mp3"))
    }

    @Test fun `录音文件名排除`() {
        assertTrue(DeviceAudioRules.isRecordingName("通话录音 张三 20241101.m4a"))
        assertTrue(DeviceAudioRules.isRecordingName("录音 20241101_1030.m4a"))
        assertTrue(DeviceAudioRules.isRecordingName("Call@13800000000(13800000000)_20241101.mp3"))
        assertTrue(DeviceAudioRules.isRecordingName("Record_20241101.amr"))
        assertFalse(DeviceAudioRules.isRecordingName("Call Me Maybe.mp3"))
        assertFalse(DeviceAudioRules.isRecordingName("晴天.mp3"))
        assertEquals("name", DeviceAudioRules.rejectReason(row("/storage/emulated/0/Download/通话录音-李四.m4a")))
    }

    @Test fun `标记位排除`() {
        val ok = row("/storage/emulated/0/Music/a.mp3")
        assertNull(DeviceAudioRules.rejectReason(ok))
        assertEquals("not-music", DeviceAudioRules.rejectReason(ok.copy(isMusic = false)))
        assertEquals("recording", DeviceAudioRules.rejectReason(ok.copy(isRecording = true)))
        assertEquals("ringtone", DeviceAudioRules.rejectReason(ok.copy(isRingtone = true)))
        assertEquals("notification", DeviceAudioRules.rejectReason(ok.copy(isNotification = true)))
        assertEquals("alarm", DeviceAudioRules.rejectReason(ok.copy(isAlarm = true)))
    }

    @Test fun `短于 30 秒排除`() {
        assertEquals("short", DeviceAudioRules.rejectReason(row("/storage/emulated/0/Music/a.mp3", dur = 29_999L)))
        assertNull(DeviceAudioRules.rejectReason(row("/storage/emulated/0/Music/a.mp3", dur = 30_000L)))
    }

    @Test fun `清库只删被排除且没收藏的手机行`() {
        val rows = listOf(
            TrackEntity(key = "mediastore:1", title = "通话录音"),
            TrackEntity(key = "mediastore:2", title = "收藏的录音", favorite = true),
            TrackEntity(key = "mediastore:3", title = "正常歌"),
            TrackEntity(key = "kw:1", title = "在线歌"),
        )
        val keys = LibraryRules.purgeable(rows, setOf("mediastore:1", "mediastore:2", "kw:1"))
        assertEquals(listOf("mediastore:1"), keys)
        assertTrue(LibraryRules.purgeable(rows, emptySet()).isEmpty())
    }
}
