package com.simplemobiletools.musicplayer.models

data class AlbumHeader(val title: String, val coverArt: String, val year: Int, val trackCnt: Int, val duration: Int, val artist: String) : ListItem()
