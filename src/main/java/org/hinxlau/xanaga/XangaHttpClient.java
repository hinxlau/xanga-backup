package org.hinxlau.xanaga;

import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class XangaHttpClient {

    private static final Logger L = LoggerFactory.getLogger(XangaHttpClient.class);

    private static final String BEFORE_LOGIN = "http://hk.xanga.com/";
    private static final String LINK_LOGIN = "http://hk.xanga.com/front.aspx";
    private static final String LINK_ARCHIVES = "http://%s.xanga.com/archives/";

    private final PoolingClientConnectionManager connectionManager;

    private final HttpClient httpClient;

    private final HttpContext localContext = new BasicHttpContext();

    public XangaHttpClient(int maxConnection) {
        connectionManager = new PoolingClientConnectionManager();
        connectionManager.setMaxTotal(maxConnection);
        httpClient = new DefaultHttpClient(connectionManager);
    }

    public void login(String username, String password) {
        try {
            connectionManager.closeIdleConnections(0L, TimeUnit.MILLISECONDS);

            HttpGet httpGet = new HttpGet(BEFORE_LOGIN);
            HttpResponse httpResponse = httpClient.execute(httpGet, localContext);
            L.debug("login, GET {}, Status Code = {}", BEFORE_LOGIN, httpResponse.getStatusLine().getStatusCode());
            EntityUtils.consume(httpResponse.getEntity());
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                throw new Exception("Expected status code 200, but was " + httpResponse.getStatusLine().getStatusCode());
            }

            ArrayList<NameValuePair> pairList = new ArrayList<NameValuePair>(3);
            pairList.add(new BasicNameValuePair("IsPostBack", "true"));
            pairList.add(new BasicNameValuePair("XangaHeader$txtSigninUsername", username));
            pairList.add(new BasicNameValuePair("XangaHeader$txtSigninPassword", password));
            HttpPost httpPost = new HttpPost(LINK_LOGIN);
            httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.110 Safari/537.36");
            httpPost.setEntity(new UrlEncodedFormEntity(pairList, Consts.UTF_8));
            httpResponse = httpClient.execute(httpPost, localContext);
            L.debug("login, POST {}, Status Code = {}", LINK_LOGIN, httpResponse.getStatusLine().getStatusCode());
            EntityUtils.consume(httpResponse.getEntity());
            if (httpResponse.getStatusLine().getStatusCode() != 302) {
                throw new Exception("Expected status code 302, but was " + httpResponse.getStatusLine().getStatusCode());
            }

            L.info("Login user:{} successful");

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String downloadHtml(String link) {
        try {
            L.trace("download, link={}", link);
            HttpGet httpGet = new HttpGet(link);
            HttpResponse httpResponse = httpClient.execute(httpGet, localContext);
            L.info("download, GET {}, Status Code = {}", link, httpResponse.getStatusLine().getStatusCode());
            String result = EntityUtils.toString(httpResponse.getEntity());
            L.debug("download, link={}, result length={}", link, result == null ? null : result.length());
            return result;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String downloadArchivesHtml(String username){
        try {
            L.trace("downloadArchivesHtml, username={}", username);
            HttpGet httpGet = new HttpGet(String.format(LINK_ARCHIVES, username));
            HttpResponse httpResponse = httpClient.execute(httpGet, localContext);
            L.info("downloadArchivesHtml, GET {}, Status Code = {}", httpGet.getURI().toURL().toString(), httpResponse.getStatusLine().getStatusCode());
            String result = EntityUtils.toString(httpResponse.getEntity());
            L.debug("downloadArchivesHtml finished with username {}", username);
            return result;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void shutdown(){
        // TODO
    }
}
