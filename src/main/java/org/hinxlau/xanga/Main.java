package org.hinxlau.xanga;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hinxlau.xanga.parser.ArchivesPageParser;
import org.hinxlau.xanga.parser.MonthPageParser;
import org.hinxlau.xanga.task.MonthPageDownloadCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class Main {

    private static final Logger L = LoggerFactory.getLogger(Main.class);

    private final SimpleDateFormat sdfFormat = new SimpleDateFormat("MMddyyyy_hhmmss_SSS", Locale.ENGLISH);

    public static void main(String[] args) {
        Main instance = null;
        try {

            Properties prop = new Properties();
            prop.load(new FileInputStream("xanga-backup.properties"));

            for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                L.info("{}={}", entry.getKey(), entry.getValue());
            }

            instance = new Main(prop);
            instance.doWork();

        }catch(Exception ex){
            L.error("Exception", ex);

        } finally {
            if (instance != null) {
                instance.shutdown();
            }
        }

    }

    private final String username;
    private final String password;
    private final String archivesUsername;

    private final XangaHttpClient xangaHttpClient;
    private final ListeningExecutorService httpEs;
    private final ListeningExecutorService parserEs;
    private final ListeningExecutorService fileEs;

    private final String location;

    private final ArchivesPageParser archivesPageParser = new ArchivesPageParser();
    private final MonthPageParser monthPageParser = new MonthPageParser();

    public Main(Properties prop){
        username = prop.getProperty("xanga.username").toString();
        password = prop.get("xanga.password").toString();
        archivesUsername = prop.getProperty("xanga.archivesUsername");
        int maxConnection = Integer.parseInt(prop.getProperty("httpClient.maxConnection", "1"));
        int threadHttpEs = Integer.parseInt(prop.getProperty("thread.http","1"));
        int threadParserEs = Integer.parseInt(prop.getProperty("thread.parser","1"));
        int threadFileEs = Integer.parseInt(prop.getProperty("thread.file","1"));
        String tempLocation = prop.getProperty("xanaga.backupLoation", "./");
        int stringLength = tempLocation.length();
        boolean endWithSlash = tempLocation.endsWith("/") || tempLocation.endsWith("\\");
        location = endWithSlash ? tempLocation : (tempLocation + "/");

        xangaHttpClient = new XangaHttpClient(maxConnection);
        httpEs = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threadHttpEs));
        parserEs = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threadParserEs));
        fileEs = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threadFileEs));
    }

    public void doWork(){
        try {

            File file = new File(location);
            if (!file.isDirectory()) {
                throw new RuntimeException("Backup location must be a directory");
            }
            final File newFolder;
            synchronized (sdfFormat) {
                newFolder = new File(location + "/" + archivesUsername + "_" + sdfFormat.format(new Date()));
                newFolder.mkdir();
            }


            xangaHttpClient.login(username, password);
            String archivesHtml = xangaHttpClient.downloadArchivesHtml(archivesUsername);
            List<String> monthLinkList = archivesPageParser.parseMonthsLink(archivesHtml, archivesUsername);

            // Parse all post link in month html
            List<ListenableFuture<List<Map.Entry<String, String>>>> monthPostFutureList = Lists.newArrayList();
            List<String> failedList = Collections.synchronizedList(new ArrayList<String>());
            for (final String monthLink : monthLinkList) {
                ListenableFuture<String> future = httpEs.submit(new MonthPageDownloadCallable(xangaHttpClient, failedList, monthLink));
                monthPostFutureList.add(Futures.transform(future, new AsyncFunction<String, List<Map.Entry<String, String>>>() {
                    @Override
                    public ListenableFuture<List<Map.Entry<String, String>>> apply(final String s) throws Exception {
                        L.trace("Start parsing {}", monthLink);
                        return parserEs.submit(new Callable<List<Map.Entry<String, String>>>() {
                            @Override
                            public List<Map.Entry<String, String>> call() throws Exception {
                                L.trace("Start parsing monthLink {}", monthLink);
                                List<Map.Entry<String, String>> result = monthPageParser.parseEntries(s);
                                L.trace("End parsing monthLink {}", monthLink);
                                return result;
                            }
                        });
                    }
                }));
            }

            final ListenableFuture<List<List<Map.Entry<String, String>>>> postFutureList = Futures.allAsList(monthPostFutureList);
            while (!postFutureList.isDone()) {
                L.info("Waiting for downloading and parsing all links...");
                Thread.sleep(1000L);
            }

            L.info("Sorting links...");
            List<List<Map.Entry<String, String>>> postList = postFutureList.get();
            Map<String, String> postMap = Maps.newHashMap();
            for (List<Map.Entry<String, String>> monthPostList : postList) {
                for (Map.Entry<String, String> entry : monthPostList) {
                    postMap.put(entry.getKey(), entry.getValue());
                }
            }
            final Map<String,String> finalPostMap = Collections.synchronizedMap(postMap);

            L.info("Writing content file...");
            Set<String> keySet = postMap.keySet();
            List<String> keyList = Lists.newArrayList(keySet);
            Collections.sort(keyList);
            L.info("Going to download {} posts", keyList.size());
            FileOutputStream fos = new FileOutputStream(newFolder.getPath() + "/content.txt");
            for (String key : keyList) {
                IOUtils.write(key + "," + postMap.get(key) + "\n", fos, Charset.forName("UTF-8"));
            }
            IOUtils.closeQuietly(fos);

            L.info("Start to download and saving post...");
            List<ListenableFuture<String>> downloadPostFutureList = Lists.newArrayList();
            for (final String key : keyList) {
                ListenableFuture<String> future = httpEs.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        L.trace("Start downloading post {}", key);
                        String result = xangaHttpClient.downloadHtml(finalPostMap.get(key));
                        L.trace("Finish downloading post {}", key);
                        Thread.sleep(100L);
                        L.trace("Finish cool down after downloading post {}", key);
                        return result;
                    }
                });

                Futures.addCallback(future, new FutureCallback<String>() {
                    @Override
                    public void onSuccess(String s) {
                        try{
                            L.trace("Start saving " + key+".html");
                            FileUtils.write(new File(newFolder.getPath() + "/" + key + ".html"), s, Charset.forName("UTF-8"));
                            L.debug("Saved " + key + ".html");
                        }

                        catch(Exception ex) {
                            L.error("Exception writing file " + key+".html, link: " + finalPostMap.get(key), ex);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        L.error("Failed to download", throwable);
                    }
                }, fileEs);
                downloadPostFutureList.add(future);
            }

            List<ListenableFuture<String>> progressingList = Lists.newArrayList();
            while (!downloadPostFutureList.isEmpty()) {
                L.info("Downling posts in progress...");
                for (ListenableFuture<String> future : downloadPostFutureList){
                    if (!future.isDone()){
                        progressingList.add(future);
                    }
                }
                downloadPostFutureList.clear();
                downloadPostFutureList.addAll(progressingList);
                progressingList.clear();
                System.gc();
                Thread.sleep(30000L);
            }

        } catch (Exception ex) {
            L.error("Exception", ex);
        }
    }

    public void shutdown() {
        if (xangaHttpClient != null) {
            xangaHttpClient.shutdown();
        }
        if (httpEs != null) {
            httpEs.shutdown();
        }
        if (fileEs != null) {
            fileEs.shutdown();
        }
        if (parserEs != null) {
            parserEs.shutdown();
        }
    }

}
