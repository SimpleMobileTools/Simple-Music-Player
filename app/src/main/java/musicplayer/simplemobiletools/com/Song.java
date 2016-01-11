package musicplayer.simplemobiletools.com;

public class Song {
    private long id;
    private String title;
    private String artist;

    public Song(long id, String title, String artist) {
        this.id = id;
        this.title = title;
        this.artist = artist;
    }

    public long getId() {
        return id;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return "Song{"
                + "id=" + getId()
                + ", title=" + getTitle()
                + ", artist=" + getArtist()
                + "}";
    }
}
