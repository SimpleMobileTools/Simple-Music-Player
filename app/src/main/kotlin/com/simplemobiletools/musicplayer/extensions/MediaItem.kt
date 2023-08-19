package com.simplemobiletools.musicplayer.extensions

import android.net.Uri
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.simplemobiletools.musicplayer.helpers.MEDIA_ITEM_TAG
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
    tag: Any? = null
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
            if (tag != null) {
                setExtras(bundleOf(MEDIA_ITEM_TAG to tag))
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
        tag = this
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

fun MediaItem.toTrack(): Track? = mediaMetadata.extras?.getSerializable(MEDIA_ITEM_TAG) as? Track

fun Collection<MediaItem>.toTracks() = mapNotNull { it.toTrack() }

fun Collection<MediaItem>.indexOfTrack(track: Track) = indexOfFirst { it.mediaId == track.mediaStoreId.toString() }

fun Collection<MediaItem>.indexOfTrackOrNull(track: Track) = indexOfFirstOrNull { it.mediaId == track.mediaStoreId.toString() }
