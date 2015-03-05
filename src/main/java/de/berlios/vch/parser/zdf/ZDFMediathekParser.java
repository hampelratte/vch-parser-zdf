package de.berlios.vch.parser.zdf;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.felix.ipojo.annotations.Bind;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Unbind;
import org.osgi.service.log.LogService;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.net.INetworkProtocol;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.XmlParserUtils;
import de.berlios.vch.parser.zdf.VideoType.Quality;

@Component
@Provides
public class ZDFMediathekParser implements IWebParser {
    public static final String ID = ZDFMediathekParser.class.getName();
    private static final String BASE_URI = "http://www.zdf.de/ZDFmediathek/xmlservice/web";
    private static final String ROOT_PAGE = "dummy://localhost/" + ID;
    private static final int PREFERRED_THUMB_WIDTH = 300;

    public static final String CHARSET = "UTF-8";

    private final Map<String, String> aBisZ = new TreeMap<String, String>();

    public static Map<String, String> HTTP_HEADERS = new HashMap<String, String>();
    static {
        HTTP_HEADERS.put("User-Agent", "Mozilla/5.0 (X11; Linux i686) AppleWebKit/537.17 (KHTML, like Gecko) Chrome/24.0.1312.45 Safari/537.17");
        HTTP_HEADERS.put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
    }

    @Requires
    private LogService logger;

    // injected by iPojo, see #addProtocol(), #removeProtocol()
    List<INetworkProtocol> supportedProtocols = new ArrayList<INetworkProtocol>();

    public final OverviewPage abz;

    public ZDFMediathekParser() {
        // initialize the root page
        aBisZ.put("0-9", BASE_URI + "/sendungenAbisZ?characterRangeEnd=0-9&detailLevel=2&characterRangeStart=0-9");
        aBisZ.put("ABC", BASE_URI + "/sendungenAbisZ?characterRangeEnd=C&detailLevel=2&characterRangeStart=A");
        aBisZ.put("DEF", BASE_URI + "/sendungenAbisZ?characterRangeEnd=F&detailLevel=2&characterRangeStart=D");
        aBisZ.put("GHI", BASE_URI + "/sendungenAbisZ?characterRangeEnd=I&detailLevel=2&characterRangeStart=G");
        aBisZ.put("JKL", BASE_URI + "/sendungenAbisZ?characterRangeEnd=L&detailLevel=2&characterRangeStart=J");
        aBisZ.put("MNO", BASE_URI + "/sendungenAbisZ?characterRangeEnd=O&detailLevel=2&characterRangeStart=M");
        aBisZ.put("PQRS", BASE_URI + "/sendungenAbisZ?characterRangeEnd=S&detailLevel=2&characterRangeStart=P");
        aBisZ.put("TUV", BASE_URI + "/sendungenAbisZ?characterRangeEnd=V&detailLevel=2&characterRangeStart=T");
        aBisZ.put("WXYZ", BASE_URI + "/sendungenAbisZ?characterRangeEnd=Z&detailLevel=2&characterRangeStart=W");
        abz = new OverviewPage();
        abz.setParser(getId());
        abz.setTitle("Sendungen A-Z");

        try {
            abz.setUri(new URI(ROOT_PAGE));
            for (Entry<String, String> abzPage : aBisZ.entrySet()) {
                String title = abzPage.getKey();
                String uri = abzPage.getValue();
                OverviewPage tmp = new OverviewPage();
                tmp.setParser(getId());
                tmp.setTitle(title);
                tmp.setUri(new URI(uri));
                abz.getPages().add(tmp);
            }
        } catch (URISyntaxException e) {
            // this should never happen
            logger.log(LogService.LOG_ERROR, "Couldn't create root page", e);
        }
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        return abz;
    }

    @Override
    public String getTitle() {
        return "ZDFmediathek";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof VideoPage) {
            VideoPage video = (VideoPage) page;
            parseVideoPage(video);
        } else {
            parseOverviewPage((OverviewPage) page);
        }
        return page;
    }

    public void parseOverviewPage(OverviewPage page) throws URISyntaxException, IOException, SAXException, ParserConfigurationException {
        // download xml as string
        String xml = HttpUtils.get(page.getUri().toString(), HTTP_HEADERS, CHARSET);

        // parse the xml
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document content = builder.parse(new InputSource(new StringReader(xml)));

        String uri = page.getUri().toString();
        String statusCode = XmlParserUtils.getTextContent(content, "statuscode");
        if (!"ok".equals(statusCode)) {
            String debugInfo = "";
            try {
                debugInfo = XmlParserUtils.getTextContent(content, "debuginfo");
            } catch (Exception e) { /* fail silently */
            }
            throw new RuntimeException("Error: status code " + statusCode + " - " + debugInfo);
        }

        if (uri.contains("sendungenAbisZ")) {
            NodeList teasers = content.getElementsByTagName("teaser");
            page.getPages().clear();
            for (int i = 0; i < teasers.getLength(); i++) {
                Node teaser = teasers.item(i);
                String type = XmlParserUtils.getTextContent(teaser, "type");
                if (!"sendung".equals(type)) {
                    continue;
                }

                String title = XmlParserUtils.getTextContent(teaser, "title");
                String id = XmlParserUtils.getTextContent(teaser, "assetId");

                OverviewPage programmPage = new OverviewPage();
                programmPage.setParser(getId());
                programmPage.setTitle(title);
                programmPage.getUserData().put("id", id);
                programmPage.setUri(new URI(BASE_URI + "/aktuellste?offset=0&maxLength=15&id=" + id));
                page.getPages().add(programmPage);
            }
        } else if (uri.contains("/aktuellste?")) {
            NodeList teasers = content.getElementsByTagName("teaser");
            for (int i = 0; i < teasers.getLength(); i++) {
                Node teaser = teasers.item(i);
                String type = XmlParserUtils.getTextContent(teaser, "type");
                if (!"video".equals(type)) {
                    continue;
                }

                String title = XmlParserUtils.getTextContent(teaser, "title");
                String description = XmlParserUtils.getTextContent(content, "detail");
                String id = XmlParserUtils.getTextContent(teaser, "assetId");

                VideoPage video = new VideoPage();
                video.setParser(getId());
                video.setTitle(title);
                video.setDescription(description);
                video.getUserData().put("id", id);
                video.setUri(new URI(BASE_URI + "/beitragsDetails?ak=web&id=" + id));
                page.getPages().add(video);

                // parse the uri of the preview image
                Map<Integer, String> images = parsePreviewImages(teaser);
                if (images.size() > 0) {
                    List<Integer> sizes = new ArrayList<Integer>(images.keySet());
                    Collections.sort(sizes);
                    String thumbUri = images.get(getClosest(sizes, PREFERRED_THUMB_WIDTH));
                    video.setThumbnail(new URI(thumbUri));
                    // String biggest = images.get(sizes.get(sizes.size() - 1));
                }
            }
        }
    }

    /**
     * Determines the number in a list of numbers, which is the closest to the given number.
     *
     * @param list
     * @param n
     * @return
     */
    private static int getClosest(List<Integer> list, Integer n) {
        int closest = list.get(0);
        int distance = Math.abs(n - closest);
        for (Integer element : list) {
            int currentDistance = Math.abs(element - n);
            if (currentDistance < distance) {
                closest = element;
                distance = currentDistance;
            }
        }
        return closest;
    }

    private static List<String> supportedFormats = Arrays.asList(new String[] { "mp4"/* , "3gp" */});

    private static Pattern minutesPattern = Pattern.compile("(\\d+)\\s*min"); // "28 min"
    private static Pattern timestampPattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})\\.\\d{3}"); // 00:01:43.000

    private void parseVideoPage(VideoPage video) throws URISyntaxException, IOException, ParserConfigurationException, SAXException {
        // download xml as string
        String xml = HttpUtils.get(video.getUri().toString(), HTTP_HEADERS, CHARSET);

        // parse the xml
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document content = builder.parse(new InputSource(new StringReader(xml)));

        String statusCode = XmlParserUtils.getTextContent(content, "statuscode");
        if (!"ok".equals(statusCode)) {
            return;
        }

        // String webPage = XmlParserUtils.getTextContent(xml, "contextLink");

        // parse title and description
        String title = XmlParserUtils.getTextContent(content, "title");
        video.setTitle(title);
        String description = XmlParserUtils.getTextContent(content, "detail");
        video.setDescription(description);

        // parse length
        Node details = XmlParserUtils.getFirstElementByTagName(content, "details");
        int length = parseLength(details);
        video.setDuration(length);

        // parse the publish date
        Calendar pubDate = parsePubDate(details);
        video.setPublishDate(pubDate);

        // parse the video
        parseVideoUri(content, video);
    }

    private Map<Integer, String> parsePreviewImages(Node parent) {
        List<Node> images = new ArrayList<Node>();
        XmlParserUtils.getElementsByTagName(parent, "teaserimage", images);
        Map<Integer, String> imageUris = new HashMap<Integer, String>();
        for (int i = 0; i < images.size(); i++) {
            Node teaserimage = images.get(i);
            String size = teaserimage.getAttributes().getNamedItem("key").getNodeValue();
            String alt = teaserimage.getAttributes().getNamedItem("alt").getNodeValue();
            int width = Integer.parseInt(size.substring(0, size.indexOf('x')));
            String uri = teaserimage.getTextContent();
            if (!uri.contains("fallback") && alt.length() > 0) { // fallback URIs don't work at the moment (28.04.2013)
                imageUris.put(width, uri);
            }
        }
        return imageUris;
    }

    private Calendar parsePubDate(Node details) {
        String airtime = XmlParserUtils.getTextContent(details, "airtime"); // <airtime>23.04.2013 22:15</airtime>
        try {
            Date pubDate = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).parse(airtime);
            Calendar cal = GregorianCalendar.getInstance();
            cal.setTime(pubDate);
            return cal;
        } catch (ParseException e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse publish date " + airtime, e);
        }
        return null;
    }

    private void parseVideoUri(Document xml, IVideoPage video) throws URISyntaxException {
        List<VideoType> videoTypes = new ArrayList<VideoType>();
        logger.log(LogService.LOG_DEBUG, "Supported formats: " + supportedFormats.toString());
        logger.log(LogService.LOG_DEBUG, "Supported protocols: " + supportedProtocols.toString());
        NodeList formitaeten = xml.getElementsByTagName("formitaet");
        for (int i = 0; i < formitaeten.getLength(); i++) {
            Node formitaet = formitaeten.item(i);

            // skip files from metafilegenerator.de since it returns 403 (couldn't figure out the right HTTP headers to get a response)
            String videoUri = XmlParserUtils.getTextContent(formitaet, "url");
            if (videoUri.contains("metafilegenerator")) {
                continue;
            }

            // skip unsupported formats / protocols
            String basetype = formitaet.getAttributes().getNamedItem("basetype").getNodeValue();
            if (basetype.contains("3gp") || basetype.contains("rtsp")) {
                continue;
            }

            String type = videoUri.substring(videoUri.lastIndexOf('.') + 1);
            String quality = XmlParserUtils.getTextContent(formitaet, "quality");
            String ratio = XmlParserUtils.getTextContent(formitaet, "ratio");

            // skip weired formats
            if (!"16:9".equals(ratio)) {
                continue;
            }

            // parse meta data file for type and real video URL
            if (basetype.contains("rtmp_zdfmeta_http")) {
                try {
                    String metaXml = HttpUtils.get(videoUri, HTTP_HEADERS, CHARSET);
                    videoUri = XmlParserUtils.getStringWithXpath(metaXml, "/metafile/default-stream-url");
                    type = videoUri.substring(videoUri.lastIndexOf('.') + 1);
                } catch (Exception e) {
                    logger.log(LogService.LOG_WARNING, "Couldn't parse meta file at " + videoUri);
                }
            }

            // parse smil file (cotains 3 or 4 streams with different qualities)
            if (basetype.contains("rtmp_smil_http")) {
                // parse the smil file
                try {
                    parseSmilFile(videoUri, videoTypes);
                    continue;
                } catch (Exception e) {
                    logger.log(LogService.LOG_WARNING, "Couldn't parse smil file at " + videoUri);
                }
            }

            // parse the video height
            String heightString = XmlParserUtils.getTextContent(formitaet, "height");
            if (heightString != null) {
                int height = Integer.parseInt(heightString);
                if (supportedFormats.contains(type)) {
                    Quality q = Quality.valueOf(quality);
                    videoTypes.add(new VideoType(videoUri, type, height, q));
                }
            }
        }

        // sort out videos with unsupported schemes
        // since the smil only contains rtmp URIs, make sure, that we support RTMP
        for (Iterator<VideoType> iterator = videoTypes.iterator(); iterator.hasNext();) {
            VideoType videoType = iterator.next();
            String uri = videoType.getUri();
            boolean schemeSupported = checkSchemeSupport(uri);
            if (!schemeSupported) {
                iterator.remove();
            }
        }

        // figure out the video with the best quality
        Collections.sort(videoTypes, new VideoTypeComparator());
        if (videoTypes.size() > 0) {
            VideoType best = videoTypes.get(videoTypes.size() - 1);
            logger.log(LogService.LOG_DEBUG, "Best video is " + best.getQuality() + " " + best.getFormat() + " " + best.getHeight() + " " + best.getUri());
            video.setVideoUri(new URI(best.getUri()));
            if (video.getVideoUri().getScheme().equals("rtmp")) {
                int index = best.getUri().indexOf("/mp4:");
                String streamName = best.getUri().substring(index + 1);
                video.getUserData().put("streamName", streamName);
            }
        } else {
            String msg = "No video found for broadcast " + video.getUri();
            throw new RuntimeException(msg);
        }
    }

    private boolean checkSchemeSupport(String videoUri) {
        try {
            String scheme = new URI(videoUri).getScheme();
            boolean schemeSupported = false;
            for (INetworkProtocol protocol : supportedProtocols) {
                if (protocol.getSchemes().contains(scheme)) {
                    schemeSupported = true;
                    break;
                }
            }

            return schemeSupported;
        } catch (URISyntaxException e) {
        }
        return false;
    }

    private void parseSmilFile(String videoUri, List<VideoType> videoTypes) throws IOException, ParserConfigurationException, SAXException,
            XPathExpressionException {
        String smil = HttpUtils.get(videoUri, HTTP_HEADERS, CHARSET);
        Document doc = XmlParserUtils.parse(smil);
        Node hostParam = XmlParserUtils.getNodeWithXpath(doc, "/smil/head/paramGroup/param[@name='host']");
        String host = hostParam.getAttributes().getNamedItem("value").getNodeValue();
        Node appParam = XmlParserUtils.getNodeWithXpath(doc, "/smil/head/paramGroup/param[@name='app']");
        String app = appParam.getAttributes().getNamedItem("value").getNodeValue();

        List<Node> videos = new ArrayList<Node>();
        XmlParserUtils.getElementsByTagName(doc, "video", videos);
        for (Node node : videos) {
            String streamName = node.getAttributes().getNamedItem("src").getNodeValue();
            Node qualityParam = XmlParserUtils.getNodeWithXpath(node, "param[@name='quality']");
            String quality = qualityParam.getAttributes().getNamedItem("value").getNodeValue();
            Quality q = Quality.valueOf(quality);
            videoUri = "rtmp://" + host + "/" + app + "/" + streamName;
            videoTypes.add(new VideoType(videoUri, "mp4", 0, q));
        }
    }

    private int parseLength(Node parent) {
        String l = XmlParserUtils.getTextContent(parent, "length");
        Matcher m = minutesPattern.matcher(l);
        if (m.matches()) {
            int minutes = Integer.parseInt(m.group(1)) * 60;
            return minutes;
        } else {
            m = timestampPattern.matcher(l);
            if (m.matches()) {
                int hours = Integer.parseInt(m.group(1));
                int minutes = Integer.parseInt(m.group(2));
                int seconds = Integer.parseInt(m.group(3));
                int length = hours * 60 * 60 + minutes * 60 + seconds;
                return length;
            }
        }
        return 0;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Bind(id = "supportedProtocols", aggregate = true)
    public synchronized void addProtocol(INetworkProtocol protocol) {
        supportedProtocols.add(protocol);
    }

    @Unbind(id = "supportedProtocols", aggregate = true)
    public synchronized void removeProtocol(INetworkProtocol protocol) {
        supportedProtocols.remove(protocol);
    }
}