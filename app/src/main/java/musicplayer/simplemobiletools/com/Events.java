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
}
