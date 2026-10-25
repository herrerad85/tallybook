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

package com.oriondev.moneywallet.utils;

import com.oriondev.moneywallet.BuildConfig;

import java.util.Locale;

import okhttp3.HttpUrl;

/**
 * Address rules for the two places a user types a server address: the backup backend and the map
 * tile server. Free of any android framework class so it can be unit tested, which the neighbouring
 * Utils class cannot be, its static initializer needing a real framework.
 */
public class Urls {

    private static final String HTTPS = "https://";
    private static final String HTTP = "http://";

    private static final String TILE_Z = "{z}";
    private static final String TILE_X = "{x}";
    private static final String TILE_Y = "{y}";

    private static final String PLACEHOLDER_START = "{";
    private static final String PLACEHOLDER_START_ENCODED = "%7b";
    private static final char QUERY = '?';
    private static final char FRAGMENT = '#';

    private static final String TILE_SUFFIX = TILE_Z + "/" + TILE_X + "/" + TILE_Y + ".png";

    /**
     * Https, naming a server, matched case insensitively. Release builds ship no cleartext policy
     * so the platform would refuse http anyway; debug builds ship one, so they allow it and can be
     * pointed at a throwaway local server.
     *
     * This also gates the WebDAV server address, which carries a password. Relaxing the scheme
     * rule for the map would relax it for that too.
     */
    public static boolean isAcceptableUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ENGLISH);
        if (lower.startsWith(HTTPS)) {
            return namesAServer(url, HTTPS);
        }
        return BuildConfig.DEBUG && lower.startsWith(HTTP) && namesAServer(url, HTTP);
    }

    /**
     * Whether the part between the scheme and the path names a server.
     *
     * Handed to a parser rather than scanned by hand. Three hand rolled versions of this each
     * looked complete and each let a different hostless shape through, the last being anything that
     * put a character in front of the empty host.
     *
     * OkHttp's parser rather than java.net.URI, because URI implements the RFC 2396 hostname
     * grammar and refuses an underscore or a non ascii name. Both are ordinary on a home network,
     * which is the audience for this, and both are accepted by the clients that do the fetching.
     * A validator stricter than the fetcher refuses addresses that would have worked.
     *
     * Only the authority is parsed, not the whole address, because a template carries braces and
     * every parser rejects those. Userinfo is refused outright: neither client turns it into an
     * auth header, so it cannot work, and it would sit in plain view on the settings screen.
     */
    private static boolean namesAServer(String url, String scheme) {
        int start = scheme.length();
        int end = url.length();
        for (int i = start; i < end; i++) {
            char c = url.charAt(i);
            if (c == '/' || c == QUERY || c == FRAGMENT) {
                end = i;
                break;
            }
        }
        String authority = url.substring(start, end);
        if (authority.contains(PLACEHOLDER_START)
                || authority.toLowerCase(Locale.ENGLISH).contains(PLACEHOLDER_START_ENCODED)) {
            return false;
        }
        if (authority.endsWith(":")) {
            // a port separator with nothing after it, which parses as a bare host, so pasting an
            // address twice over gives the plausible looking host "https"
            return false;
        }
        HttpUrl parsed = HttpUrl.parse(HTTPS + authority + "/");
        return parsed != null && parsed.username().isEmpty() && parsed.password().isEmpty();
    }

    /**
     * A tile address is a bare base address, which asTileTemplate completes, or a template with
     * all three placeholders. Also refused: any other placeholder, including capitals and the
     * percent encoded spelling; a fragment, in either shape; and a query in a base address.
     *
     * The rules have tests in TileAddressTest, most of them naming the address that motivated the
     * rule and what it would have done. That is the place to read why, and it fails if the answer
     * changes.
     */
    public static boolean isUsableTileAddress(String url) {
        if (!isAcceptableUrl(url)) {
            return false;
        }
        // before the count: a stray placeholder is as fatal in a base address as in a template
        String stripped = stripKnownPlaceholders(url);
        if (stripped.contains(PLACEHOLDER_START)
                || stripped.toLowerCase(Locale.ENGLISH).contains(PLACEHOLDER_START_ENCODED)) {
            return false;
        }
        if (url.indexOf(FRAGMENT) >= 0 || containsWhitespace(url)) {
            return false;
        }
        int found = (url.contains(TILE_Z) ? 1 : 0)
                + (url.contains(TILE_X) ? 1 : 0)
                + (url.contains(TILE_Y) ? 1 : 0);
        if (found != 0 && found != 3) {
            return false;
        }
        return found == 3 || url.indexOf(QUERY) < 0;
    }

    /**
     * Anywhere, not just in the host. The field is trimmed at the ends only, so an interior space
     * or a tab pasted into the path survives into the request.
     */
    private static boolean containsWhitespace(String url) {
        for (int i = 0; i < url.length(); i++) {
            if (Character.isWhitespace(url.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String stripKnownPlaceholders(String url) {
        return url.replace(TILE_Z, "").replace(TILE_X, "").replace(TILE_Y, "");
    }

    /**
     * @return the address of one tile. Kept here rather than in the map wrapper so the string that
     *          becomes a network request is reachable from an ordinary unit test.
     */
    public static String tileUrl(String template, int zoom, int x, int y) {
        return template
                .replace(TILE_Z, String.valueOf(zoom))
                .replace(TILE_X, String.valueOf(x))
                .replace(TILE_Y, String.valueOf(y));
    }

    /**
     * @return the address as a template. A bare base address is completed, so the common case is
     *          just the server's own address and nobody has to know the convention. Assumes the
     *          address already passed isUsableTileAddress.
     */
    public static String asTileTemplate(String url) {
        if (url.contains(TILE_Z)) {
            return url;
        }
        return url.endsWith("/") ? url + TILE_SUFFIX : url + "/" + TILE_SUFFIX;
    }
}
