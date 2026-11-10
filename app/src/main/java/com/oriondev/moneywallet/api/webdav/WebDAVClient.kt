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

package com.oriondev.moneywallet.api.webdav

import com.oriondev.moneywallet.model.WebDAVFile

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * The whole WebDAV surface this app needs: list one folder, create one folder, upload a file,
 * download a file. That is PROPFIND, MKCOL, PUT and GET.
 *
 * It runs on OkHttp rather than the platform's `HttpURLConnection` because the platform
 * client validates the method against a fixed list and throws
 * `ProtocolException: Expected one of [OPTIONS, GET, HEAD, POST, PUT, DELETE, TRACE, PATCH]`
 * for both PROPFIND and MKCOL. OkHttp has no such list, and its own source special cases WebDAV
 * verbs, so it is the only realistic transport here.
 */
class WebDAVClient {

    private val mBaseUrl: String
    private val mAuthorization: String
    private val mHttp: OkHttpClient

    constructor(baseUrl: String?, username: String?, password: String?) {
        mBaseUrl = normalizeBaseUrl(baseUrl)
        mAuthorization = Credentials.basic(if (username == null) "" else username,
                if (password == null) "" else password)
        mHttp = OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .build()
    }

    private fun urlFor(path: String?): String {
        return mBaseUrl + encodePath(if (path == null) "" else path)
    }

    /**
     * The path component of the base URL, used to strip a server's absolute href back down to a
     * path relative to the configured root.
     */
    private fun basePath(): String {
        try {
            val path: String? = URL(mBaseUrl).path
            return if (path == null) "" else path
        } catch (e: MalformedURLException) {
            return ""
        }
    }

    private fun request(path: String?): Request.Builder {
        return Request.Builder()
                .url(urlFor(path))
                .header("Authorization", mAuthorization)
    }

    @Throws(WebDAVException::class)
    fun list(path: String?): List<WebDAVFile> {
        val request = request(directoryPath(path))
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
                .header("Depth", "1")
                .build()
        try {
            mHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDAVException.forStatus(response.code, "list " + path)
                }
                val body = response.body
                if (body == null) {
                    throw WebDAVException("Server returned an empty folder listing", true)
                }
                return parsePropfind(body.byteStream(), basePath(), trimSlashes(path))
            }
        } catch (e: IOException) {
            throw WebDAVException("Could not list " + path, e, true)
        } catch (e: SAXException) {
            throw WebDAVException("Could not read the server's folder listing", e, true)
        } catch (e: ParserConfigurationException) {
            throw WebDAVException("Could not read the server's folder listing", e, true)
        }
    }

    @Throws(WebDAVException::class)
    fun makeCollection(path: String?) {
        // MKCOL takes no body. OkHttp requires an explicit empty one for a method it does not know
        // to be body-less.
        val request = request(directoryPath(path))
                .method("MKCOL", ByteArray(0).toRequestBody(null))
                .build()
        try {
            mHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDAVException.forStatus(response.code, "create folder " + path)
                }
            }
        } catch (e: IOException) {
            throw WebDAVException("Could not create folder " + path, e, true)
        }
    }

    /**
     * Streams the upload rather than buffering it: a backup archive is arbitrarily large and the
     * caller's stream already reports progress.
     */
    @Throws(WebDAVException::class)
    fun upload(path: String?, `in`: InputStream, size: Long) {
        val body: RequestBody = object : RequestBody() {

            override fun contentType(): MediaType? {
                return OCTET_STREAM
            }

            override fun contentLength(): Long {
                return if (size > 0) size else -1
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                copy(`in`, sink.outputStream())
            }
        }
        val request = request(path).put(body).build()
        try {
            mHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw WebDAVException.forStatus(response.code, "upload " + path)
                }
            }
        } catch (e: IOException) {
            throw WebDAVException("Could not upload " + path, e, true)
        }
    }

    /**
     * Opens the download stream. The caller owns it and must close it, which also releases the
     * connection back to the pool.
     */
    @Throws(WebDAVException::class)
    fun openDownload(path: String?): InputStream {
        val request = request(path).get().build()
        var response: Response? = null
        try {
            response = mHttp.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw WebDAVException.forStatus(code, "download " + path)
            }
            val body = response.body
            if (body == null) {
                response.close()
                throw WebDAVException("Server returned an empty response for " + path, true)
            }
            return body.byteStream()
        } catch (e: IOException) {
            if (response != null) {
                response.close()
            }
            throw WebDAVException("Could not download " + path, e, true)
        }
    }

    /**
     * A cheap PROPFIND against the root, used by the setup dialog so a typo or a wrong password is
     * reported while the user is still looking at the form.
     */
    @Throws(WebDAVException::class)
    fun checkConnection() {
        list("")
    }

    companion object {

        private const val DAV_NAMESPACE = "DAV:"

        private const val CONNECT_TIMEOUT_S: Long = 15
        private const val READ_TIMEOUT_S: Long = 20
        private const val WRITE_TIMEOUT_S: Long = 60

        private val XML: MediaType? = "application/xml; charset=utf-8".toMediaTypeOrNull()
        private val OCTET_STREAM: MediaType? = "application/octet-stream".toMediaTypeOrNull()

        private const val PROPFIND_BODY =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                        "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                        "<d:resourcetype/><d:getcontentlength/>" +
                        "</d:prop></d:propfind>"

        /**
         * Trims the URL and guarantees exactly one trailing slash, so joining a path is always a plain
         * concatenation. A base URL saved with or without the slash has to behave identically; users
         * type it both ways.
         */
        @JvmStatic
        fun normalizeBaseUrl(url: String?): String {
            var trimmed = if (url == null) "" else url.trim { it <= ' ' }
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length - 1)
            }
            return trimmed + "/"
        }

        /**
         * Percent encodes each path segment, leaving the separators alone. A backup file named with a
         * space or an accented character is ordinary, and an unencoded one produces a 400 from most
         * servers, so this is not a corner case.
         */
        @JvmStatic
        fun encodePath(path: String?): String {
            if (path == null || path.isEmpty()) {
                return ""
            }
            val builder = StringBuilder()
            val segments = path.split("/")
            for (i in segments.indices) {
                if (i > 0) {
                    builder.append('/')
                }
                builder.append(encodeSegment(segments[i]))
            }
            return builder.toString()
        }

        private fun encodeSegment(segment: String): String {
            if (segment.isEmpty()) {
                return segment
            }
            try {
                // URLEncoder targets form bodies, not paths: it maps a space to '+' and escapes a few
                // characters that are legal in a path. Undo those three rather than hand rolling a
                // second encoder.
                return URLEncoder.encode(segment, "UTF-8")
                        .replace("+", "%20")
                        .replace("%7E", "~")
                        .replace("*", "%2A")
            } catch (e: UnsupportedEncodingException) {
                throw AssertionError("UTF-8 is always available")
            }
        }

        private fun decodePath(encoded: String): String {
            try {
                return URLDecoder.decode(encoded, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                throw AssertionError("UTF-8 is always available")
            } catch (e: IllegalArgumentException) {
                // A malformed escape is the server's problem, not a reason to lose the whole listing.
                return encoded
            }
        }

        /**
         * Reduces an href from a PROPFIND response to a path relative to the base URL. Servers are
         * inconsistent here: some return an absolute URL, some return an absolute path including the
         * base URL's own prefix, so both shapes are handled.
         */
        @JvmStatic
        fun hrefToRelativePath(href: String, basePath: String?): String {
            var path = href
            val schemeEnd = path.indexOf("://")
            if (schemeEnd >= 0) {
                val hostEnd = path.indexOf('/', schemeEnd + 3)
                path = if (hostEnd >= 0) path.substring(hostEnd) else "/"
            }
            path = decodePath(path)
            if (!basePath!!.isEmpty() && path.startsWith(basePath)) {
                path = path.substring(basePath.length)
            }
            return trimSlashes(path)
        }

        @JvmStatic
        fun nameOf(path: String): String {
            val slash = path.lastIndexOf('/')
            return if (slash >= 0) path.substring(slash + 1) else path
        }

        /**
         * Parses a PROPFIND multistatus body. Namespace aware on purpose: servers disagree about the
         * prefix (D:, d:, lp1:, ns0:), so matching on the DAV: namespace rather than the tag text is
         * what makes this work across Nextcloud, ownCloud and a plain Apache mod_dav.
         *
         * The entry describing the requested folder itself is dropped: a Depth 1 PROPFIND always
         * returns it alongside its children.
         */
        @JvmStatic
        @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
        fun parsePropfind(`in`: InputStream?, basePath: String?, requestedPath: String?): List<WebDAVFile> {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            // The response is data, not a document that gets to reference the local filesystem.
            setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.isExpandEntityReferences = false

            val builder = factory.newDocumentBuilder()
            val document = builder.parse(`in`)

            val files: MutableList<WebDAVFile> = ArrayList()
            val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
            for (i in 0 until responses.length) {
                val response = responses.item(i) as Element
                val href = textOf(response, "href")
                if (href == null) {
                    continue
                }
                val path = hrefToRelativePath(href, basePath)
                if (path == requestedPath) {
                    continue
                }
                val isDirectory = firstElement(response, "collection") != null
                var size = 0L
                val contentLength = textOf(response, "getcontentlength")
                if (contentLength != null) {
                    try {
                        size = contentLength.trim { it <= ' ' }.toLong()
                    } catch (ignored: NumberFormatException) {
                        // A folder, or a server that omits the length. Zero is the right answer.
                    }
                }
                files.add(WebDAVFile(nameOf(path), path, size, isDirectory))
            }
            return files
        }

        private fun setFeatureQuietly(factory: DocumentBuilderFactory, feature: String, value: Boolean) {
            try {
                factory.setFeature(feature, value)
            } catch (ignored: ParserConfigurationException) {
                // Not every parser knows this feature. Namespace awareness is the part we depend on.
            }
        }

        private fun firstElement(parent: Element, localName: String): Element? {
            val nodes = parent.getElementsByTagNameNS(DAV_NAMESPACE, localName)
            return if (nodes.length > 0) nodes.item(0) as Element else null
        }

        private fun textOf(parent: Element, localName: String): String? {
            val element = firstElement(parent, localName)
            if (element == null) {
                return null
            }
            val text = StringBuilder()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                    text.append(child.nodeValue)
                }
            }
            return text.toString()
        }

        @JvmStatic
        fun trimSlashes(path: String?): String {
            var result = if (path == null) "" else path
            while (result.startsWith("/")) {
                result = result.substring(1)
            }
            while (result.endsWith("/")) {
                result = result.substring(0, result.length - 1)
            }
            return result
        }

        private fun directoryPath(path: String?): String {
            val trimmed = trimSlashes(path)
            return if (trimmed.isEmpty()) "" else trimmed + "/"
        }

        @Throws(IOException::class)
        private fun copy(`in`: InputStream, out: OutputStream) {
            val chunk = ByteArray(8192)
            var read: Int
            while (`in`.read(chunk).also { read = it } != -1) {
                out.write(chunk, 0, read)
            }
        }
    }
}
