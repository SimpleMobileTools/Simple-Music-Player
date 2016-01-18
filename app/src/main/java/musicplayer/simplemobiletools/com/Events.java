package musicplayer.simplemobiletools.com;

public class Events {
    public static class PreviousSong {
    }

    public static class PlayPauseSong {
    }

    public static class NextSong {
    }

    public static class StopSong {
    }

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
}
