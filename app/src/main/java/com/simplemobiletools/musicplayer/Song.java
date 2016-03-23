package com.simplemobiletools.musicplayer;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String path;

    public Song(long id, String title, String artist, String path) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
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

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "Song{"
                + "id=" + getId()
                + ", title=" + getTitle()
                + ", artist=" + getArtist()
                + ", path=" + getPath()
                + "}";
    }
}
