package com.pkrete.xrd4j.rest.client;

import com.github.markusbernhardt.proxy.ProxySearch;
import com.github.markusbernhardt.proxy.selector.misc.BufferedProxySelector;
import com.pkrete.xrd4j.rest.ClientResponse;
import com.pkrete.xrd4j.rest.util.ClientUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.HttpHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an abstract base class for classes implementing GET, POST, PUT and
 * DELETE HTTP clients.
 *
 * @author Petteri Kivimäki
 */
public abstract class AbstractClient implements RESTClient {

    private static final Logger logger = LoggerFactory.getLogger(AbstractClient.class);

    protected abstract HttpUriRequest buildtHttpRequest(String url, String requestBody, Map<String, String> headers);

    // The proxy detection code is taken from:
    // https://stackoverflow.com/questions/4933677/detecting-windows-ie-proxy-setting-using-java
    public HttpHost getProxyHost(String url) {
        try {
            System.setProperty("java.net.useSystemProxies", "true");

            // Use proxy vole to find the default proxy
            ProxySearch ps = ProxySearch.getDefaultProxySearch();

            ps.setPacCacheSettings(32, 1000 * 60 * 5, BufferedProxySelector.CacheScope.CACHE_SCOPE_URL);
            logger.info("Selecting proxy");
            ProxySelector proxySelector = ps.getProxySelector();
            if (proxySelector == null) {
                logger.info("Proxyselector is null, not using proxy");
                return null;
            }
            List<Proxy> l = proxySelector.select(new URI(url));
            logger.info("Proxy list; {}", l);
            Iterator<Proxy> iter = l.iterator();

            if (!iter.hasNext()) {
                logger.info("No Proxy found");
                return null;
            } else {
                Proxy proxy = iter.next();

                logger.info("proxy type : {}", proxy.type());
                InetSocketAddress addr = (InetSocketAddress) proxy.address();

                if (addr == null) {
                    logger.info("No Proxy (addess is null)");
                    return null;
                } else {
                    logger.info("proxy hostname : {}", addr.getHostName());
                    logger.info("proxy port : {}", addr.getPort());
                    return new HttpHost(addr.getHostName(), addr.getPort());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Makes a HTTP request to the given URL using the given request body,
     * parameters and HTTP headers. The parameters are used as URL parameters,
     * but if there's a parameter "resourceId", it's added directly to the end
     * of the URL. If there's no request body, the value can be null.
     *
     * @param url URL where the request is sent
     * @param params request parameters
     * @param requestBody request body
     * @param headers HTTP headers to be added to the request
     * @return response as string
     */
    @Override
    public ClientResponse send(String url, String requestBody, Map<String, ?> params, Map<String, String> headers) {
        // Build target URL
        url = ClientUtil.buildTargetURL(url, params);

        // Create HTTP client
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        HttpHost proxy = getProxyHost(url);
        if (proxy != null) {
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
            httpClientBuilder.setRoutePlanner(routePlanner);
        }
        CloseableHttpClient httpClient = httpClientBuilder.build();

        // Build request
        HttpUriRequest request = this.buildtHttpRequest(url, requestBody, headers);

        logger.info("Starting HTTP {} operation.", request.getMethod());

        // Add headers
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                logger.debug("Add header : \"{}\" = \"{}\"", entry.getKey(), entry.getValue());
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }

        try {
            //Send the request; It will immediately return the response in HttpResponse object
            CloseableHttpResponse response = httpClient.execute(request);
            // Get Content-Type header
            Header[] contentTypeHeader = response.getHeaders("Content-Type");
            String contentType = null;
            // Check for null and empty
            if (contentTypeHeader != null && contentTypeHeader.length > 0) {
                contentType = contentTypeHeader[0].getValue();
            }
            // Get Status Code
            int statusCode = response.getStatusLine().getStatusCode();
            // Get reason phrase
            String reasonPhrase = response.getStatusLine().getReasonPhrase();

            // Get response payload
            String responseStr = ClientUtil.getResponseString(response.getEntity());

            response.close();
            httpClient.close();
            logger.debug("REST response content type: \"{}\".", contentType);
            logger.debug("REST response status code: \"{}\".", statusCode);
            logger.debug("REST response reason phrase: \"{}\".", reasonPhrase);
            logger.debug("REST response : \"{}\".", responseStr);
            logger.info("HTTP {} operation completed.", request.getMethod());
            return new ClientResponse(responseStr, contentType, statusCode, reasonPhrase);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            logger.warn("HTTP {} operation failed. An empty string is returned.", request.getMethod());
            return null;
        }
    }
}
