package de.berlios.vch.parser.zdf;

public class VideoType {

    //@formatter:off
    public static enum Quality {
        low, 
        medium, 
        high, 
        veryhigh
    }
    //@formatter:on

    private Quality quality;
    private final String uri;
    private String format;
    private int height;

    public VideoType(String uri, String format, int height, Quality quality) {
        super();
        this.uri = uri;
        this.format = format;
        this.height = height;
        this.quality = quality;
    }

    public String getUri() {
        return uri;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
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
}
