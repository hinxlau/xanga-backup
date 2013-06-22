package org.hinxlau.xanaga.task;

import org.hinxlau.xanaga.XangaHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

public class MonthPageDownloadCallable implements Callable<String> {

    private static final Logger L = LoggerFactory.getLogger(MonthPageDownloadCallable.class);

    private final XangaHttpClient xangaHttpClient;
    private final String monthLink;
    private final List<String> failedList;

    public MonthPageDownloadCallable(XangaHttpClient xangaHttpClient, List<String> failedList, String monthLink) {
        this.xangaHttpClient = xangaHttpClient;
        this.monthLink = monthLink;
        this.failedList = failedList;
    }

    @Override
    public String call() throws Exception {
        try {
            L.trace("call() start, {}", monthLink);
            String result = xangaHttpClient.downloadHtml(monthLink);
            return result;

        } finally {
            L.trace("call() end, {}", monthLink);
        }
    }


}
