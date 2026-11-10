/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.api.webdav;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.model.WebDAVFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/**
 * The whole WebDAV surface this app needs: list one folder, create one folder, upload a file,
 * download a file. That is PROPFIND, MKCOL, PUT and GET.
 *
 * It runs on OkHttp rather than the platform's {@code HttpURLConnection} because the platform
 * client validates the method against a fixed list and throws
 * {@code ProtocolException: Expected one of [OPTIONS, GET, HEAD, POST, PUT, DELETE, TRACE, PATCH]}
 * for both PROPFIND and MKCOL. OkHttp has no such list, and its own source special cases WebDAV
 * verbs, so it is the only realistic transport here.
 *
 * The methods that do not touch the network are static and package visible so they can be unit
 * tested on the JVM.
 */
public class WebDAVClient {

    static final String DAV_NAMESPACE = "DAV:";

    private static final long CONNECT_TIMEOUT_S = 15;
    private static final long READ_TIMEOUT_S = 20;
    private static final long WRITE_TIMEOUT_S = 60;

    private static final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    private static final String PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<d:propfind xmlns:d=\"DAV:\"><d:prop>"
                    + "<d:resourcetype/><d:getcontentlength/>"
                    + "</d:prop></d:propfind>";

    private final String mBaseUrl;
    private final String mAuthorization;
    private final OkHttpClient mHttp;

    public WebDAVClient(String baseUrl, String username, String password) {
        mBaseUrl = normalizeBaseUrl(baseUrl);
        mAuthorization = Credentials.basic(username == null ? "" : username,
                password == null ? "" : password);
        mHttp = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Trims the URL and guarantees exactly one trailing slash, so joining a path is always a plain
     * concatenation. A base URL saved with or without the slash has to behave identically; users
     * type it both ways.
     */
    static String normalizeBaseUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/";
    }

    /**
     * Percent encodes each path segment, leaving the separators alone. A backup file named with a
     * space or an accented character is ordinary, and an unencoded one produces a 400 from most
     * servers, so this is not a corner case.
     */
    static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(encodeSegment(segments[i]));
        }
        return builder.toString();
    }

    private static String encodeSegment(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        try {
            // URLEncoder targets form bodies, not paths: it maps a space to '+' and escapes a few
            // characters that are legal in a path. Undo those three rather than hand rolling a
            // second encoder.
            return URLEncoder.encode(segment, "UTF-8")
                    .replace("+", "%20")
                    .replace("%7E", "~")
                    .replace("*", "%2A");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always available");
        }
    }

    static String decodePath(String encoded) {
        try {
            return URLDecoder.decode(encoded, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always available");
        } catch (IllegalArgumentException e) {
            // A malformed escape is the server's problem, not a reason to lose the whole listing.
            return encoded;
        }
    }

    /**
     * Reduces an href from a PROPFIND response to a path relative to the base URL. Servers are
     * inconsistent here: some return an absolute URL, some return an absolute path including the
     * base URL's own prefix, so both shapes are handled.
     */
    static String hrefToRelativePath(String href, String basePath) {
        String path = href;
        int schemeEnd = path.indexOf("://");
        if (schemeEnd >= 0) {
            int hostEnd = path.indexOf('/', schemeEnd + 3);
            path = hostEnd >= 0 ? path.substring(hostEnd) : "/";
        }
        path = decodePath(path);
        if (!basePath.isEmpty() && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        return trimSlashes(path);
    }

    static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * Parses a PROPFIND multistatus body. Namespace aware on purpose: servers disagree about the
     * prefix (D:, d:, lp1:, ns0:), so matching on the DAV: namespace rather than the tag text is
     * what makes this work across Nextcloud, ownCloud and a plain Apache mod_dav.
     *
     * The entry describing the requested folder itself is dropped: a Depth 1 PROPFIND always
     * returns it alongside its children.
     */
    static List<WebDAVFile> parsePropfind(InputStream in, String basePath, String requestedPath)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // The response is data, not a document that gets to reference the local filesystem.
        setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(in);

        List<WebDAVFile> files = new ArrayList<>();
        NodeList responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Element response = (Element) responses.item(i);
            String href = textOf(response, "href");
            if (href == null) {
                continue;
            }
            String path = hrefToRelativePath(href, basePath);
            if (path.equals(requestedPath)) {
                continue;
            }
            boolean isDirectory = firstElement(response, "collection") != null;
            long size = 0L;
            String contentLength = textOf(response, "getcontentlength");
            if (contentLength != null) {
                try {
                    size = Long.parseLong(contentLength.trim());
                } catch (NumberFormatException ignored) {
                    // A folder, or a server that omits the length. Zero is the right answer.
                }
            }
            files.add(new WebDAVFile(nameOf(path), path, size, isDirectory));
        }
        return files;
    }

    private static void setFeatureQuietly(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // Not every parser knows this feature. Namespace awareness is the part we depend on.
        }
    }

    @Nullable
    private static Element firstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(DAV_NAMESPACE, localName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    @Nullable
    private static String textOf(Element parent, String localName) {
        Element element = firstElement(parent, localName);
        if (element == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }
        return text.toString();
    }

    static String trimSlashes(String path) {
        String result = path == null ? "" : path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String directoryPath(String path) {
        String trimmed = trimSlashes(path);
        return trimmed.isEmpty() ? "" : trimmed + "/";
    }

    String urlFor(String path) {
        return mBaseUrl + encodePath(path == null ? "" : path);
    }

    /**
     * The path component of the base URL, used to strip a server's absolute href back down to a
     * path relative to the configured root.
     */
    String basePath() {
        try {
            String path = new URL(mBaseUrl).getPath();
            return path == null ? "" : path;
        } catch (MalformedURLException e) {
            return "";
        }
    }

    private Request.Builder request(String path) {
        return new Request.Builder()
                .url(urlFor(path))
                .header("Authorization", mAuthorization);
    }

    @NonNull
    public List<WebDAVFile> list(String path) throws WebDAVException {
        Request request = request(directoryPath(path))
                .method("PROPFIND", RequestBody.create(PROPFIND_BODY, XML))
                .header("Depth", "1")
                .build();
        try (Response response = mHttp.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw WebDAVException.forStatus(response.code(), "list " + path);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new WebDAVException("Server returned an empty folder listing", true);
            }
            return parsePropfind(body.byteStream(), basePath(), trimSlashes(path));
        } catch (IOException e) {
            throw new WebDAVException("Could not list " + path, e, true);
        } catch (SAXException | ParserConfigurationException e) {
            throw new WebDAVException("Could not read the server's folder listing", e, true);
        }
    }

    public void makeCollection(String path) throws WebDAVException {
        // MKCOL takes no body. OkHttp requires an explicit empty one for a method it does not know
        // to be body-less.
        Request request = request(directoryPath(path))
                .method("MKCOL", RequestBody.create(new byte[0], null))
                .build();
        try (Response response = mHttp.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw WebDAVException.forStatus(response.code(), "create folder " + path);
            }
        } catch (IOException e) {
            throw new WebDAVException("Could not create folder " + path, e, true);
        }
    }

    /**
     * Streams the upload rather than buffering it: a backup archive is arbitrarily large and the
     * caller's stream already reports progress.
     */
    public void upload(String path, final InputStream in, final long size) throws WebDAVException {
        RequestBody body = new RequestBody() {

            @Override
            public MediaType contentType() {
                return OCTET_STREAM;
            }

            @Override
            public long contentLength() {
                return size > 0 ? size : -1;
            }

            @Override
            public void writeTo(@NonNull BufferedSink sink) throws IOException {
                copy(in, sink.outputStream());
            }
        };
        Request request = request(path).put(body).build();
        try (Response response = mHttp.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw WebDAVException.forStatus(response.code(), "upload " + path);
            }
        } catch (IOException e) {
            throw new WebDAVException("Could not upload " + path, e, true);
        }
    }

    /**
     * Opens the download stream. The caller owns it and must close it, which also releases the
     * connection back to the pool.
     */
    public InputStream openDownload(String path) throws WebDAVException {
        Request request = request(path).get().build();
        Response response = null;
        try {
            response = mHttp.newCall(request).execute();
            if (!response.isSuccessful()) {
                int code = response.code();
                response.close();
                throw WebDAVException.forStatus(code, "download " + path);
            }
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw new WebDAVException("Server returned an empty response for " + path, true);
            }
            return body.byteStream();
        } catch (IOException e) {
            if (response != null) {
                response.close();
            }
            throw new WebDAVException("Could not download " + path, e, true);
        }
    }

    /**
     * A cheap PROPFIND against the root, used by the setup dialog so a typo or a wrong password is
     * reported while the user is still looking at the form.
     */
    public void checkConnection() throws WebDAVException {
        list("");
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            out.write(chunk, 0, read);
        }
    }
}
