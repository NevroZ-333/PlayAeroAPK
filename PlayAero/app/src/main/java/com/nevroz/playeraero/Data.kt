package com.nevroz.playeraero

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class Source(val label: String, val color: Long) {
    LOCAL("Память", 0xFF29B6F6),
    SOUNDCLOUD("SoundCloud", 0xFFFF7700),
    SPOTIFY("Spotify", 0xFF1DB954),
    YANDEX("Яндекс Музыка", 0xFFE6B800),
    WEB("Ссылка", 0xFF7E57C2)
}

private val AUDIO_EXT = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac")

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val source: Source,
    val durationMs: Long = 0
) {
    /** Играется внутри приложения: файлы из памяти и прямые аудио-ссылки. */
    val playable: Boolean
        get() = source == Source.LOCAL ||
                (source == Source.WEB && AUDIO_EXT.any { uri.substringBefore('?').lowercase().endsWith(it) })
}

data class Playlist(val id: String, val name: String, val trackIds: List<String>)

data class Prefs(
    val fps: Int = 0,          // 0 = авто
    val swipeStyle: Int = 0,   // 0 плавное, 1 быстрое, 2 без анимации
    val swipeCover: Boolean = true,
    val bubbles: Boolean = true
)

class Repo(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("aero", Context.MODE_PRIVATE)

    fun loadTracks(): List<Track> = try {
        val a = JSONArray(sp.getString("tracks", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Track(o.getString("id"), o.getString("title"), o.getString("artist"),
                o.getString("uri"), Source.valueOf(o.getString("source")), o.optLong("dur"))
        }
    } catch (e: Exception) { emptyList() }

    fun saveTracks(l: List<Track>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                .put("uri", it.uri).put("source", it.source.name).put("dur", it.durationMs))
        }
        sp.edit().putString("tracks", a.toString()).apply()
    }

    fun loadPlaylists(): List<Playlist> = try {
        val a = JSONArray(sp.getString("playlists", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val ids = o.getJSONArray("ids")
            Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { i -> ids.getString(i) })
        }
    } catch (e: Exception) { emptyList() }

    fun savePlaylists(l: List<Playlist>) {
        val a = JSONArray()
        l.forEach { p ->
            val ids = JSONArray(); p.trackIds.forEach { ids.put(it) }
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("ids", ids))
        }
        sp.edit().putString("playlists", a.toString()).apply()
    }

    fun loadPrefs() = Prefs(
        sp.getInt("fps", 0), sp.getInt("swipe", 0),
        sp.getBoolean("swipeCover", true), sp.getBoolean("bubbles", true)
    )

    fun savePrefs(p: Prefs) {
        sp.edit().putInt("fps", p.fps).putInt("swipe", p.swipeStyle)
            .putBoolean("swipeCover", p.swipeCover).putBoolean("bubbles", p.bubbles).apply()
    }

    private fun displayName(uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) return c.getString(i).substringBeforeLast('.')
                }
            }
        } catch (e: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Трек"
    }

    fun readLocal(uri: Uri): Track {
        val name = displayName(uri)
        val r = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var dur = 0L
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
        } finally {
            r.release()
        }
        return Track(
            uri.toString(),
            title?.takeIf { it.isNotBlank() } ?: name,
            artist?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель",
            uri.toString(), Source.LOCAL, dur
        )
    }

    fun fromLink(url: String): Track? {
        val u = url.trim()
        val uri = Uri.parse(u)
        if (uri.scheme != "http" && uri.scheme != "https") return null
        val host = (uri.host ?: return null).lowercase()
        val src = when {
            "soundcloud" in host -> Source.SOUNDCLOUD
            "spotify" in host -> Source.SPOTIFY
            "yandex" in host || host == "ya.cc" -> Source.YANDEX
            else -> Source.WEB
        }
        val last = uri.pathSegments.lastOrNull()?.substringBeforeLast('.')
        val title = last?.let { Uri.decode(it).replace('-', ' ').replace('_', ' ') }
            ?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() } ?: host
        return Track(u, title, src.label, u, src)
    }
}

object Covers {
    private val cache = LruCache<String, Bitmap>(48)
    private val missing = ConcurrentHashMap.newKeySet<String>()

    suspend fun get(ctx: Context, t: Track): Bitmap? = withContext(Dispatchers.IO) {
        if (t.source != Source.LOCAL) return@withContext null
        cache.get(t.uri)?.let { return@withContext it }
        if (t.uri in missing) return@withContext null
        val r = MediaMetadataRetriever()
        var out: Bitmap? = null
        try {
            r.setDataSource(ctx, Uri.parse(t.uri))
            val b = r.embeddedPicture
            if (b != null) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(b, 0, b.size, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 700) s *= 2
                out = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
            }
        } catch (e: Exception) {
        } finally {
            r.release()
        }
        val res = out
        if (res != null) cache.put(t.uri, res) else missing.add(t.uri)
        res
    }
}
