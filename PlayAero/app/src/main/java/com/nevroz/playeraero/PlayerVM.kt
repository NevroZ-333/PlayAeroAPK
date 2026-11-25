package com.nevroz.playeraero

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PlayerVM(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)

    var tracks by mutableStateOf(repo.loadTracks()); private set
    var playlists by mutableStateOf(repo.loadPlaylists()); private set
    var prefs by mutableStateOf(repo.loadPrefs()); private set
    var queue by mutableStateOf<List<Track>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var isPlaying by mutableStateOf(false); private set
    var waveMode by mutableStateOf(false); private set

    val current: Track? get() = queue.getOrNull(index)

    private var ctrl: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { this@PlayerVM.isPlaying = isPlaying }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            index = ctrl?.currentMediaItemIndex ?: 0
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            ctrl = c
            c.addListener(listener)
            isPlaying = c.isPlaying
        }, ContextCompat.getMainExecutor(app))
    }

    private fun Track.toItem() = MediaItem.Builder()
        .setMediaId(id).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        .build()

    fun playList(list: List<Track>, start: Int, wave: Boolean = false) {
        val p = list.filter { it.playable }
        if (p.isEmpty()) return
        val id = list.getOrNull(start)?.id
        val si = p.indexOfFirst { it.id == id }.coerceAtLeast(0)
        queue = p; index = si; waveMode = wave
        ctrl?.run {
            setMediaItems(p.map { it.toItem() }, si, 0L)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare(); play()
        }
    }

    fun startWave(): Boolean {
        val p = tracks.filter { it.playable }.shuffled()
        if (p.isEmpty()) return false
        playList(p, 0, true)
        return true
    }

    fun toggle() { ctrl?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { ctrl?.seekToNextMediaItem() }
    fun prev() { ctrl?.seekToPreviousMediaItem() }
    fun seekToIndex(i: Int) { ctrl?.seekTo(i, 0L) }
    fun seekTo(ms: Long) { ctrl?.seekTo(ms) }
    fun position(): Long = ctrl?.currentPosition ?: 0L
    fun duration(): Long = ctrl?.duration?.takeIf { it > 0 } ?: current?.durationMs ?: 0L

    fun setPrefs(p: Prefs) { prefs = p; repo.savePrefs(p) }

    fun addLocal(uris: List<Uri>, playlistId: String?) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val read = withContext(Dispatchers.IO) {
                uris.map { u ->
                    try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    catch (e: Exception) { }
                    repo.readLocal(u)
                }
            }
            addTracks(read, playlistId)
        }
    }

    fun addLink(url: String, playlistId: String?): Boolean {
        val t = repo.fromLink(url) ?: return false
        addTracks(listOf(t), playlistId)
        return true
    }

    private fun addTracks(new: List<Track>, playlistId: String?) {
        val have = tracks.map { it.id }.toSet()
        tracks = tracks + new.filter { it.id !in have }
        repo.saveTracks(tracks)
        if (playlistId != null) addToPlaylist(playlistId, new.map { it.id })
    }

    fun deleteTrack(id: String) {
        tracks = tracks.filter { it.id != id }
        playlists = playlists.map { it.copy(trackIds = it.trackIds - id) }
        repo.saveTracks(tracks); repo.savePlaylists(playlists)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        playlists = playlists + Playlist(UUID.randomUUID().toString(), name.trim(), emptyList())
        repo.savePlaylists(playlists)
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }; repo.savePlaylists(playlists)
    }

    fun addToPlaylist(plId: String, ids: List<String>) {
        playlists = playlists.map { p ->
            if (p.id == plId) p.copy(trackIds = (p.trackIds + ids).distinct()) else p
        }
        repo.savePlaylists(playlists)
    }

    override fun onCleared() { ctrl?.release(); super.onCleared() }
}
