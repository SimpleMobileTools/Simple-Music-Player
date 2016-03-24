package com.simplemobiletools.musicplayer;

import java.io.Serializable;

public class Song implements Serializable {
    private static final long serialVersionUID = 6717978783256842145L;
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

    public void setArtist(String newArtist) {
        artist = newArtist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String newTitle) {
        title = newTitle;
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null)
            return false;

        return this.toString().equals(o.toString());
    }
}
