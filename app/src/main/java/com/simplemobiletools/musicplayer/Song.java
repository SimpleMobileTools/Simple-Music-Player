package com.simplemobiletools.musicplayer;

import java.io.Serializable;

public class Song implements Serializable {
    private static final long serialVersionUID = 6717978783256842145L;

    private String mTitle;
    private String mArtist;
    private String mPath;

    private long mId;
    private int mDuration;

    public Song(long id, String title, String artist, String path, int duration) {
        mId = id;
        mTitle = title;
        mArtist = artist;
        mPath = path;
        mDuration = duration;
    }

    public long getId() {
        return mId;
    }

    public String getArtist() {
        return mArtist;
    }

    public void setArtist(String newArtist) {
        mArtist = newArtist;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setmTitle(String newTitle) {
        mTitle = newTitle;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String newPath) {
        mPath = newPath;
    }

    public int getDuration() {
        return mDuration;
    }

    @Override
    public String toString() {
        return "Song{" + "mId=" + getId() +
                ", mTitle=" + getTitle() +
                ", mArtist=" + getArtist() +
                ", mPath=" + getPath() +
                ", mDuration=" + getDuration() + "}";
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
