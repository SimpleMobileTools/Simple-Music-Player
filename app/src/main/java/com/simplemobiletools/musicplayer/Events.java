package com.simplemobiletools.musicplayer;

import java.util.ArrayList;

public class Events {
    public static class SongChanged {
        private Song song;

        SongChanged(Song song) {
            this.song = song;
        }

        public Song getSong() {
            return song;
        }
    }

    public static class SongStateChanged {
        private boolean isPlaying;

        SongStateChanged(boolean isPlaying) {
            this.isPlaying = isPlaying;
        }

        public boolean getIsPlaying() {
            return isPlaying;
        }
    }

    public static class PlaylistUpdated {
        private ArrayList<Song> songs;

        PlaylistUpdated(ArrayList<Song> songs) {
            this.songs = songs;
        }

        public ArrayList<Song> getSongs() {
            return songs;
        }
    }
}
