package com.simplemobiletools.musicplayer.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.simplemobiletools.musicplayer.helpers.EXTRA_ALBUM
import com.simplemobiletools.musicplayer.helpers.EXTRA_ALBUM_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_ARTIST
import com.simplemobiletools.musicplayer.helpers.EXTRA_ARTIST_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_COVER_ART
import com.simplemobiletools.musicplayer.helpers.EXTRA_DATE_ADDED
import com.simplemobiletools.musicplayer.helpers.EXTRA_DURATION
import com.simplemobiletools.musicplayer.helpers.EXTRA_FLAGS
import com.simplemobiletools.musicplayer.helpers.EXTRA_FOLDER_NAME
import com.simplemobiletools.musicplayer.helpers.EXTRA_GENRE
import com.simplemobiletools.musicplayer.helpers.EXTRA_GENRE_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_MEDIA_STORE_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_ORDER_IN_PLAYLIST
import com.simplemobiletools.musicplayer.helpers.EXTRA_PATH
import com.simplemobiletools.musicplayer.helpers.EXTRA_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_TITLE
import com.simplemobiletools.musicplayer.helpers.EXTRA_TRACK_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_YEAR
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.*

fun buildMediaItem(
    mediaId: String,
    title: String,
    album: String? = null,
    artist: String? = null,
    genre: String? = null,
    mediaType: @MediaMetadata.MediaType Int,
    trackCnt: Int? = null,
    trackNumber: Int? = null,
    year: Int? = null,
    sourceUri: Uri? = null,
    artworkUri: Uri? = null,
    track: Track? = null
): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setAlbumTitle(album)
        .setArtist(artist)
        .setGenre(genre)
        .setIsBrowsable(mediaType != MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsPlayable(mediaType == MediaMetadata.MEDIA_TYPE_MUSIC)
        .setTotalTrackCount(trackCnt)
        .setTrackNumber(trackNumber)
        .setReleaseYear(year)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setArtworkUri(artworkUri)
        .apply {
            if (track != null) {
                setExtras(createBundleFromTrack(track))
            }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(sourceUri)
        .setMediaMetadata(metadata)
        .build()
}

fun Track.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = mediaStoreId.toString(),
        title = title,
        album = album,
        artist = artist,
        genre = genre,
        mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        trackNumber = trackId,
        sourceUri = getUri(),
        artworkUri = coverArt.toUri(),
        track = this
    )
}

fun Playlist.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = id.toString(),
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        trackCnt = trackCount
    )
}

fun Folder.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = title,
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        trackCnt = trackCount
    )
}

fun Artist.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = id.toString(),
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
        trackCnt = trackCnt,
        artworkUri = albumArt.toUri()
    )
}

fun Album.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = id.toString(),
        title = title,
        artist = artist,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        trackCnt = trackCnt,
        artworkUri = coverArt.toUri(),
        year = year
    )
}

fun Genre.toMediaItem(): MediaItem {
    return buildMediaItem(
        title = title,
        mediaId = id.toString(),
        mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
        trackCnt = trackCnt,
        artworkUri = albumArt.toUri()
    )
}

fun Collection<MediaItem>.toTracks() = mapNotNull { it.toTrack() }

fun Collection<MediaItem>.indexOfTrack(track: Track) = indexOfFirst { it.isSameMedia(track) }

fun Collection<MediaItem>.indexOfTrackOrNull(track: Track) = indexOfFirstOrNull { it.isSameMedia(track) }

fun MediaItem?.isSameMedia(track: Track) = this?.mediaId == track.mediaStoreId.toString()

fun MediaItem.toTrack(): Track? = mediaMetadata.extras?.let { createTrackFromBundle(it) }

private fun createBundleFromTrack(track: Track) = bundleOf(
    EXTRA_ID to track.id,
    EXTRA_MEDIA_STORE_ID to track.mediaStoreId,
    EXTRA_TITLE to track.title,
    EXTRA_ARTIST to track.artist,
    EXTRA_PATH to track.path,
    EXTRA_DURATION to track.duration,
    EXTRA_ALBUM to track.album,
    EXTRA_GENRE to track.genre,
    EXTRA_COVER_ART to track.coverArt,
    EXTRA_PLAYLIST_ID to track.playListId,
    EXTRA_TRACK_ID to track.trackId,
    EXTRA_FOLDER_NAME to track.folderName,
    EXTRA_ALBUM_ID to track.albumId,
    EXTRA_ARTIST_ID to track.artistId,
    EXTRA_GENRE_ID to track.genreId,
    EXTRA_YEAR to track.year,
    EXTRA_DATE_ADDED to track.dateAdded,
    EXTRA_ORDER_IN_PLAYLIST to track.orderInPlaylist,
    EXTRA_FLAGS to track.flags
)

private fun createTrackFromBundle(bundle: Bundle): Track {
    return Track(
        id = bundle.getLong(EXTRA_ID),
        mediaStoreId = bundle.getLong(EXTRA_MEDIA_STORE_ID),
        title = bundle.getString(EXTRA_TITLE) ?: "",
        artist = bundle.getString(EXTRA_ARTIST) ?: "",
        path = bundle.getString(EXTRA_PATH) ?: "",
        duration = bundle.getInt(EXTRA_DURATION),
        album = bundle.getString(EXTRA_ALBUM) ?: "",
        genre = bundle.getString(EXTRA_GENRE) ?: "",
        coverArt = bundle.getString(EXTRA_COVER_ART) ?: "",
        playListId = bundle.getInt(EXTRA_PLAYLIST_ID),
        trackId = bundle.getInt(EXTRA_TRACK_ID),
        folderName = bundle.getString(EXTRA_FOLDER_NAME) ?: "",
        albumId = bundle.getLong(EXTRA_ALBUM_ID),
        artistId = bundle.getLong(EXTRA_ARTIST_ID),
        genreId = bundle.getLong(EXTRA_GENRE_ID),
        year = bundle.getInt(EXTRA_YEAR),
        dateAdded = bundle.getInt(EXTRA_DATE_ADDED),
        orderInPlaylist = bundle.getInt(EXTRA_ORDER_IN_PLAYLIST),
        flags = bundle.getInt(EXTRA_FLAGS)
    )
}
