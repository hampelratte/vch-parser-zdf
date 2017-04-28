package de.berlios.vch.parser.zdf;

import java.util.Comparator;

/**
 * Compares two videos according to their type and quality
 *
 * @author <a href="mailto:hampelratte@users.berlios.de">hampelratte@users.berlios.de</a>
 */
public class VideoTypeComparator implements Comparator<VideoType> {

    @Override
    public int compare(VideoType vt1, VideoType vt2) {
        // compare the quality
        int quality = vt1.getQuality().compareTo(vt2.getQuality());
        if (quality != 0) {
            return quality;
        }

        // compare the height
        int height = vt1.getHeight().compareTo(vt2.getHeight());
        if (height != 0) {
            return height;
        }

        return 0;
    }
}
