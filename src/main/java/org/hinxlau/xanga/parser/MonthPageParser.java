package org.hinxlau.xanga.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class MonthPageParser {

    private static final Logger L = LoggerFactory.getLogger(MonthPageParser.class);

    private final SimpleDateFormat sdfParse = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.ENGLISH);
    private final SimpleDateFormat sdfFormat = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.ENGLISH);

    public List<Map.Entry<String, String>> parseEntries(String monthPageHtml) {
        L.trace("parseEntries");
        Document monthHtmlDoc = Jsoup.parse(monthPageHtml);
        Elements postElements = monthHtmlDoc.select("div[id=maincontent]").select("div[class=itemfooter]>ul");
        L.debug("Found no. of posts = {}", postElements.size());
        List<Map.Entry<String, String>> resultList = new ArrayList<Map.Entry<String, String>>(postElements.size());
        for (Element postElement : postElements) {
            try {
                Element timestampElement = postElement.select("li[class=itemtimestamp]>a").first();
                String timestamp = timestampElement.html();
                String postLink = timestampElement.attr("href");
                L.debug("Post timestamp={}, link={}", timestamp, postLink);
                int itemNoPos = postLink.indexOf(".xanga.com/") + 11;
                String postId = postLink.substring(itemNoPos, postLink.indexOf("/", itemNoPos));
                Date date;
                String key;
                synchronized (this) {
                    date = sdfParse.parse(timestamp);
                    key = sdfFormat.format(date) + "_" + postId;
                }
                resultList.add(new AbstractMap.SimpleEntry<String, String>(key, postLink));
            } catch (Exception ex) {
                L.error("Exception at parsing post link " + postElement.html(), ex);

            }

        }
        return resultList;
    }

}
