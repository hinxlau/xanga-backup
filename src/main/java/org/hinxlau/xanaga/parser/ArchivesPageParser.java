package org.hinxlau.xanaga.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ArchivesPageParser {

    private static final Logger L = LoggerFactory.getLogger(ArchivesPageParser.class);

    public List<String> parseMonthsLink(String archivesHtml, String username) {
        L.trace("parseMonthsLink");
        Document archivesHtmlDoc = Jsoup.parse(archivesHtml);
        Elements elements = archivesHtmlDoc.select("a[href^=http://"+username+".xanga.com/archives/]").select("a[title^=View]");
        L.info("Found no. of months = {}", elements.size());
        List<String> resultList = new ArrayList<String>(elements.size());
        for (Element monthLinkElement : elements) {
            String monthLink = monthLinkElement.attr("href");
            L.debug("Found month link = {}", monthLink);
            resultList.add(monthLink);
        }
        return resultList;
    }
}
