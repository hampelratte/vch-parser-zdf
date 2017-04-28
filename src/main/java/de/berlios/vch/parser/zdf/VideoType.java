package de.berlios.vch.parser.zdf;

public class VideoType {

    //@formatter:off
    public static enum Quality {
        low,
        medium,
        high,
        veryhigh,
        hd
    }
    //@formatter:on

    private Quality quality;
    private final String uri;
    private int height;

    public VideoType(String uri, int height, Quality quality) {
        super();
        this.uri = uri;
        this.height = height;
        this.quality = quality;
    }

    public String getUri() {
        return uri;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Quality getQuality() {
        return quality;
    }

    public void setQuality(Quality quality) {
        this.quality = quality;
    }

    @Override
    public String toString() {
        return quality + " " + height + " " + uri;
    }
}
