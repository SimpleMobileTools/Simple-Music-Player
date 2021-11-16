package com.simplemobiletools.musicplayer.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getFilenameExtension
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getTempFile
import com.simplemobiletools.musicplayer.models.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.SupportedFileFormat
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

class TagHelper(private val activity: BaseSimpleActivity) {

    init {
        TagOptionSingleton.getInstance().isAndroid = true
    }

    companion object {
        private const val TEMP_FOLDER = "music"
        // Editing tags in WMA and WAV files are flaky so we exclude them
        private val EXCLUDED_EXTENSIONS = listOf("wma", "wav")
        private val SUPPORTED_EXTENSIONS = SupportedFileFormat.values().map { it.filesuffix }.filter { it !in EXCLUDED_EXTENSIONS }
    }

    fun isEditTagSupported(track: Track): Boolean {
        return SUPPORTED_EXTENSIONS.any { it == track.path.getFilenameExtension() }
    }

    fun writeTag(track: Track, newArtist: String, newTitle: String, newAlbum: String) {
        if (isEditTagSupported(track)) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId)
            val temp = activity.getTempFile(TEMP_FOLDER, track.path.getFilenameFromPath())
            activity.contentResolver.openInputStream(uri)!!.use { inputStream ->
                temp!!.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
            }

            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tag ?: createTag(track.path.getFilenameExtension()).also { audioFile.tag = it }
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            audioFile.commit()

            activity.contentResolver.openOutputStream(uri, "w")!!.use { outputStream ->
                outputStream.write(temp!!.readBytes())
            }

            temp!!.delete()

            updateContentResolver(track)
        }
    }

    private fun createTag(extension: String): Tag {
        return when (extension) {
            SupportedFileFormat.OGG.filesuffix -> VorbisCommentTag()
            SupportedFileFormat.M4A.filesuffix -> Mp4Tag()
            SupportedFileFormat.FLAC.filesuffix -> FlacTag()
            else -> ID3v24Tag()
        }
    }

    private fun updateContentResolver(track: Track) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val where = "${MediaStore.Audio.Media._ID} = ?"
        val args = arrayOf(track.mediaStoreId.toString())

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.ALBUM, track.album)
        }
        activity.contentResolver.update(uri, values, where, args)
    }
}
