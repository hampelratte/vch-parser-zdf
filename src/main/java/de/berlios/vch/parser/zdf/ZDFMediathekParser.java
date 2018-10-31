package de.berlios.vch.parser.zdf;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.i18n.ResourceBundleLoader;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.zdf.VideoType.Quality;

@Component
@Provides
public class ZDFMediathekParser implements IWebParser, ResourceBundleProvider {
    public static final String ID = ZDFMediathekParser.class.getName();
    private static final String BASE_URI = "https://www.zdf.de";
    private static final String ROOT_PAGE = "dummy://localhost/" + ID;
    public static final String CHARSET = "UTF-8";

    public final OverviewPage abz;
    private final Map<String, String> aBisZ = new TreeMap<String, String>();

    @Requires
    private LogService logger;

    private BundleContext ctx;
    private ResourceBundle resourceBundle;

    public ZDFMediathekParser(BundleContext ctx) {
        this.ctx = ctx;

        // initialize the root page
        aBisZ.put("0-9", BASE_URI + "/sendungen-a-z?group=0+-+9");
        aBisZ.put("A", BASE_URI + "/sendungen-a-z?group=a");
        aBisZ.put("B", BASE_URI + "/sendungen-a-z?group=b");
        aBisZ.put("C", BASE_URI + "/sendungen-a-z?group=c");
        aBisZ.put("D", BASE_URI + "/sendungen-a-z?group=d");
        aBisZ.put("E", BASE_URI + "/sendungen-a-z?group=e");
        aBisZ.put("F", BASE_URI + "/sendungen-a-z?group=f");
        aBisZ.put("G", BASE_URI + "/sendungen-a-z?group=g");
        aBisZ.put("H", BASE_URI + "/sendungen-a-z?group=h");
        aBisZ.put("I", BASE_URI + "/sendungen-a-z?group=i");
        aBisZ.put("J", BASE_URI + "/sendungen-a-z?group=j");
        aBisZ.put("K", BASE_URI + "/sendungen-a-z?group=k");
        aBisZ.put("L", BASE_URI + "/sendungen-a-z?group=l");
        aBisZ.put("M", BASE_URI + "/sendungen-a-z?group=m");
        aBisZ.put("N", BASE_URI + "/sendungen-a-z?group=n");
        aBisZ.put("O", BASE_URI + "/sendungen-a-z?group=o");
        aBisZ.put("P", BASE_URI + "/sendungen-a-z?group=p");
        aBisZ.put("Q", BASE_URI + "/sendungen-a-z?group=q");
        aBisZ.put("R", BASE_URI + "/sendungen-a-z?group=r");
        aBisZ.put("S", BASE_URI + "/sendungen-a-z?group=s");
        aBisZ.put("T", BASE_URI + "/sendungen-a-z?group=t");
        aBisZ.put("U", BASE_URI + "/sendungen-a-z?group=u");
        aBisZ.put("V", BASE_URI + "/sendungen-a-z?group=v");
        aBisZ.put("W", BASE_URI + "/sendungen-a-z?group=w");
        aBisZ.put("X", BASE_URI + "/sendungen-a-z?group=x");
        aBisZ.put("Y", BASE_URI + "/sendungen-a-z?group=y");
        aBisZ.put("Z", BASE_URI + "/sendungen-a-z?group=z");
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

    public void parseOverviewPage(OverviewPage page) throws URISyntaxException, IOException {
        if (page != abz) {
            page.getPages().clear();
        }

        String uri = page.getUri().toString();
        if(uri.startsWith("dummy://cluster")) {
            // nothing to do
            return;
        }

        String content = HttpUtils.get(uri, null, CHARSET);
        if (uri.contains("/sendungen-a-z?group=")) {
            parsePrograms(page, content);
        } else {
            parseTeaserVideo(page, content);
            parseCards(page, content);
            parseClusters(page, content);
        }
    }

    private void parseTeaserVideo(OverviewPage page, String content) throws URISyntaxException {
        Element link = HtmlParserUtils.getTag(content, "div[class~=item-caption] h3[class~=teaser-title] a");
        VideoPage videoPage = new VideoPage();
        videoPage.setParser(getId());
        videoPage.setTitle(link.text());
        String programUri = link.attr("href").trim();
        if(programUri.startsWith("/")) {
            programUri = BASE_URI + programUri;
        }
        videoPage.setUri(new URI(programUri));
        page.getPages().add(videoPage);
    }

    private void parsePrograms(OverviewPage page, String content) throws URISyntaxException {
        Elements cards = HtmlParserUtils.getTags(content, "div#aria-teaser-list-shows article[class~=b-content-teaser-item]");
        for (Element card : cards) {
            String cardHtml = card.html();
            Element link = HtmlParserUtils.getTag(cardHtml, "h3.teaser-title a");
            String title = link.text();
            OverviewPage programmPage = new OverviewPage();
            programmPage.setParser(getId());
            programmPage.setTitle(title);
            String programUri = link.attr("href");
            programmPage.setUri(new URI(BASE_URI + programUri));
            page.getPages().add(programmPage);
        }
    }

    private void parseClusters(OverviewPage page, String content) throws URISyntaxException {
        Elements cards = HtmlParserUtils.getTags(content, "article[class~=b-cluster-teaser");
        for (Element card : cards) {
            parseCard(page, card.html());
        }
    }

    private void parseCards(OverviewPage page, String content) throws URISyntaxException {
        Elements cards = HtmlParserUtils.getTags(content, "div[class~=b-content-teaser-list] article[class~=b-content-teaser-item]");
        for (Element card : cards) {
            parseCard(page, card.html());
        }
    }

    private void parseCard(OverviewPage page, String html) throws URISyntaxException {
        try {
            Element link = HtmlParserUtils.getTag(html, "h3.teaser-title a");
            String title = link.text();
            VideoPage videoPage = new VideoPage();
            videoPage.setParser(getId());
            videoPage.setTitle(title);
            String programUri = link.attr("href").trim();
            if(programUri.startsWith("/")) {
                programUri = BASE_URI + programUri;
            }
            videoPage.setUri(new URI(programUri));

            Matcher m = Pattern.compile("\\d+\\s+min").matcher(html);
            if(m.find()) {
                page.getPages().add(videoPage);
            } else {
                logger.log(LogService.LOG_WARNING, title + " has no duration, probably no video");
            }
        } catch(RuntimeException e) {
            e.printStackTrace();
            // probably css selector didn't match
        }
    }

    private void parseVideoPage(VideoPage video) throws URISyntaxException, IOException, JSONException {
        String uri = video.getUri().toString();
        String content = HttpUtils.get(uri, HttpUtils.createFirefoxHeader(), CHARSET);

        String description = HtmlParserUtils.getText(content, "p.item-description");
        video.setDescription(description);
        video.setPublishDate(parsePubDate(content));
        long now = System.currentTimeMillis();
        if (video.getPublishDate().getTimeInMillis() > now) {
            if (content.contains("<strong>Vorab</strong>")) {
                // cool, this is a web premiere before the actual broadcast
            } else {
                // this is a future broadcast
                throw new RuntimeException("Video not yet available: Broadcast is on: " + video.getPublishDate().getTime());
            }
        }
        video.setTitle(HtmlParserUtils.getText(content, "h1.big-headline"));

        JSONObject playerParams = getPlayerParams(content);
        String apiKey = playerParams.getString("apiToken");
        String playerConfig = playerParams.getString("content");
        Map<String, String> header = HttpUtils.createFirefoxHeader();
        header.put("Accept", "application/vnd.de.zdf.v1.0+json");
        header.put("Api-Auth", "Bearer " + apiKey);
        header.put("Referer", uri);
        String playerContent = HttpUtils.get(playerConfig, header, CHARSET);
        JSONObject json = new JSONObject(playerContent);
        JSONObject brand = json.getJSONObject("http://zdf.de/rels/brand");
        JSONObject target = brand.getJSONObject("http://zdf.de/rels/target");
        boolean hasVideo = target.getBoolean("hasVideo");
        if(!hasVideo) {
            throw new RuntimeException("No video available");
        }

        video.setDuration(parseDuration(json));
        video.setThumbnail(parseThumbnail(json));
        video.setVideoUri(getVideoUri(json, header));
    }

    private URI getVideoUri(JSONObject json, Map<String, String> header) throws JSONException, IOException, URISyntaxException {
        JSONObject mainVideoContent = json.getJSONObject("mainVideoContent");
        JSONObject target = mainVideoContent.getJSONObject("http://zdf.de/rels/target");
        String path = target.getString("http://zdf.de/rels/streams/ptmd-template");
        String uri = "https://api.zdf.de" + path.replaceAll("\\{playerId\\}", "ngplayer_2_3");

        String mediaInfo = HttpUtils.get(uri, header, CHARSET);
        String videoUri = getBestVideo(mediaInfo);
        return new URI(videoUri);
    }

    private String getBestVideo(String mediaInfo) throws JSONException {
        JSONObject media = new JSONObject(mediaInfo);
        JSONArray prioList = media.getJSONArray("priorityList");
        List<VideoType> videoTypes = new ArrayList<VideoType>();
        for (int i = 0; i < prioList.length(); i++) {
            JSONObject item = prioList.getJSONObject(i);
            JSONArray formitaeten = item.getJSONArray("formitaeten");
            for (int j = 0; j < formitaeten.length(); j++) {
                JSONObject formitaet = formitaeten.getJSONObject(j);
                String mimeType = formitaet.getString("mimeType");
                if(!"video/mp4".equals(mimeType) && !"application/x-mpegURL".equals(mimeType)) {
                    continue;
                }

                JSONArray qualities = formitaet.getJSONArray("qualities");
                for (int k = 0; k < qualities.length(); k++) {
                    try {
                        JSONObject qualityItem = qualities.getJSONObject(k);
                        String quality = qualityItem.getString("quality");
                        Quality q = Quality.valueOf(quality);
                        JSONObject audio = qualityItem.getJSONObject("audio");
                        JSONObject track = audio.getJSONArray("tracks").getJSONObject(0);
                        String uri = track.getString("uri");
                        videoTypes.add(new VideoType(uri, 0, q));
                    } catch(Exception e) {
                        logger.log(LogService.LOG_ERROR, "Couldn't parse video", e);
                    }
                }
            }
        }

        Collections.sort(videoTypes, new VideoTypeComparator());
        Collections.reverse(videoTypes);
        return videoTypes.get(0).getUri();
    }

    private URI parseThumbnail(JSONObject json) {
        //        try {
        //            System.out.println(json.toString(2));
        //        } catch (JSONException e1) {
        //            // TODO Auto-generated catch block
        //            e1.printStackTrace();
        //        }

        URI thumb = null;
        try {
            JSONObject teaserImageRef = json.getJSONObject("teaserImageRef");
            JSONObject layouts = teaserImageRef.getJSONObject("layouts");
            String image;
            if(layouts.has("original")) {
                image = layouts.getString("original");
            } else {
                image = layouts.getString((String) layouts.keys().next());
            }
            thumb = new URI(image);
        } catch(Exception e) {
            logger.log(LogService.LOG_ERROR, "Couldn't parse thumbnail", e);
        }
        return thumb;
    }

    private JSONObject getPlayerParams(String content) throws JSONException {
        try {
            Element div = HtmlParserUtils.getTag(content, "div[class~=b-playerbox]");
            String json = div.attr("data-zdfplayer-jsb");
            return new JSONObject(json);
        } catch(RuntimeException e) {
            if(content.contains("Video leider nicht mehr verfügbar")) {
                throw new RuntimeException("Video leider nicht mehr verfügbar");
            } else {
                throw e;
            }
        }
    }

    private long parseDuration(JSONObject json) {
        try {
            JSONObject mainVideo = json.getJSONObject("mainVideoContent");
            JSONObject target = mainVideo.getJSONObject("http://zdf.de/rels/target");
            int seconds = target.getInt("duration");
            return seconds;
        } catch(Exception e) {
            return 0;
        }
    }

    private Calendar parsePubDate(String content) {
        try {
            Element time = HtmlParserUtils.getTag(content, "dd[class~=teaser-info] time");
            String airtime = time.attr("datetime"); // 2017-02-07T22:15:00.000+01:00
            Date pubDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse(airtime);
            Calendar cal = GregorianCalendar.getInstance();
            cal.setTime(pubDate);
            return cal;
        } catch (ParseException e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
        }
        return null;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            try {
                logger.log(LogService.LOG_DEBUG, "Loading resource bundle for " + getClass().getSimpleName());
                resourceBundle = ResourceBundleLoader.load(ctx, Locale.getDefault());
            } catch (IOException e) {
                logger.log(LogService.LOG_ERROR, "Couldn't load resource bundle", e);
            }
        }
        return resourceBundle;
    }
}