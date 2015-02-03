package de.berlios.vch.parser.zdf;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Compares two videos according to their type and quality
 * 
 * @author <a href="mailto:hampelratte@users.berlios.de">hampelratte@users.berlios.de</a>
 */
public class VideoTypeComparator implements Comparator<VideoType> {

    private final Map<String, Integer> formatPrios = new HashMap<String, Integer>();

    public VideoTypeComparator() {
        // mp4 has higher priority than 3gp
        formatPrios.put("mp4", 2);
        formatPrios.put("3gp", 0);
    }

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

        // compare the formats
        Integer fmtp1 = formatPrios.get(vt1.getFormat());
        Integer fmtp2 = formatPrios.get(vt2.getFormat());

        if (fmtp1 != null && fmtp2 != null) {
            if (fmtp1 > fmtp2) {
                return -1;
            } else if (fmtp2 < fmtp2) {
                return 1;
            }
        } else {
            return vt1.getHeight().compareTo(vt2.getHeight());
        }

        return 0;
    }
}
