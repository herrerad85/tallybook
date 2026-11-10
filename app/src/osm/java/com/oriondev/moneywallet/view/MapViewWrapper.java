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

package com.oriondev.moneywallet.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Coordinates;
import com.oriondev.moneywallet.model.Place;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.utils.Urls;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.util.constants.OverlayConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrea on 16/10/18.
 */

public class MapViewWrapper {

    private static final String TILE_CACHE_FOLDER = "osm_tiles";

    private static final String OSM_COPYRIGHT = "© OpenStreetMap contributors";

    /**
     * Bit identical to the policy the library's own default carries. Two of these limit us: at
     * most two requests in flight, and no speculative fetching of surrounding tiles. The no
     * argument policy has neither, so leaving it would make this app ask more of somebody's self
     * hosted server, and ask it more concurrently, than it asks of OpenStreetMap's.
     *
     * FLAG_NO_BULK is neither: it gates CacheManager, which this app never constructs, so it is
     * inert here and present only to stay identical to the default.
     *
     * MEANINGFUL is neither a limit nor inert, it is a gate: with it set the downloader refuses to
     * fetch anything at all unless a real user agent was configured, which setupConfiguration does
     * below. Removing the configured agent would silently stop every tile. NORMALIZED only makes
     * the downloader prefer a normalized agent where one exists, and refuses nothing.
     */
    private static final TileSourcePolicy TILE_POLICY = new TileSourcePolicy(2,
            TileSourcePolicy.FLAG_NO_BULK
                    | TileSourcePolicy.FLAG_NO_PREVENTIVE
                    | TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                    | TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED);

    private static final int TILE_SIZE_PIXELS = 256;
    private static final int TILE_MAX_ZOOM = 19;

    private static final double MIN_ZOOM_LEVEL = 2d;
    private static final double DEFAULT_MIN_ZOOM_LEVEL = 4d;
    private static final double DEFAULT_ZOOM_LEVEL = 14d;
    private static final float MARKER_HEIGHT_DP = 48f;

    private final MapView mMapView;

    private OnInfoWindowClickListener mInfoWindowClickListener;

    public MapViewWrapper(View view) {
        mMapView = (MapView) view;
        mMapView.setBuiltInZoomControls(false);
        mMapView.setMultiTouchControls(true);
        mMapView.setMinZoomLevel(MIN_ZOOM_LEVEL);
        mMapView.setVerticalMapRepetitionEnabled(false);
        mMapView.getController().setZoom(DEFAULT_ZOOM_LEVEL);
        setupConfiguration(mMapView.getContext());
    }

    private void setupConfiguration(Context context) {
        IConfigurationProvider configuration = Configuration.getInstance();
        // to prevent using a default user agent, we can set it to the current package name
        configuration.setUserAgentValue(context.getPackageName());
        // fix for empty map caused by a read-only access to the file system: to overcame this issue
        // we can use a temp directory inside the cache folder of the application
        File cacheFolder = context.getExternalCacheDir();
        if (cacheFolder != null) {
            // we have a reference to the global cache folder of the application, maybe it's better
            // to create a sub folder for caching tiles
            File tileCache = new File(cacheFolder, TILE_CACHE_FOLDER);
            if (tileCache.exists() || tileCache.mkdir()) {
                configuration.setOsmdroidTileCache(tileCache);
            }
        }
        setupTileSource();
    }

    /**
     * Point the map at whatever server the user chose, so that replacing the default one does not
     * need a change to this app. Doing nothing leaves the library's own default in place.
     *
     * The address is re checked here rather than trusted from storage. A rejected one leaves the
     * library's default in place, which shows a working map rather than an empty one, at the cost
     * of saying nothing about why the chosen server was not used.
     *
     * The source is named after the address, because osmdroid keys its tile cache on that name and
     * a fixed one would serve the previous server's tiles after a change. Note the name also
     * reaches BitmapTileSourceBase.pathBase, which the assets and zip archive providers use as a
     * path; both simply miss and fall through, so a url is tolerated there rather than intended.
     *
     * The attribution is the OpenStreetMap one because that is what this setting is documented for
     * and that data carries a licence requiring the notice. Passing nothing makes CopyrightOverlay
     * draw none at all, which is the worse failure for the case this exists to serve. Pointed at a
     * server carrying something other than OpenStreetMap data the notice is wrong, and there is no
     * way to change it.
     */
    private void setupTileSource() {
        String url = PreferenceManager.getMapTileServer();
        if (TextUtils.isEmpty(url) || !Urls.isUsableTileAddress(url)) {
            return;
        }
        mMapView.setTileSource(new TemplateTileSource(Urls.asTileTemplate(url)));
    }

    /**
     * osmdroid's own XYTileSource builds the address by concatenation, which fixes the path shape
     * and the image extension. Overriding the one method that produces the address is what lets a
     * user paste the {z}/{x}/{y} form that nearly every tile provider documents.
     */
    private static class TemplateTileSource extends OnlineTileSourceBase {

        private final String mTemplate;

        private TemplateTileSource(String template) {
            super(template, 0, TILE_MAX_ZOOM, TILE_SIZE_PIXELS, "", new String[] {template},
                    OSM_COPYRIGHT, TILE_POLICY);
            mTemplate = template;
        }

        @Override
        public String getTileURLString(long pMapTileIndex) {
            return Urls.tileUrl(mTemplate,
                    MapTileIndex.getZoom(pMapTileIndex),
                    MapTileIndex.getX(pMapTileIndex),
                    MapTileIndex.getY(pMapTileIndex));
        }
    }

    /**
     * @return whether this map implementation can be pointed at a different tile server.
     */
    public static boolean supportsCustomTileServer() {
        return true;
    }

    /**
     * What CopyrightOverlay's own constructor sets both offsets to in osmdroid 6.1.1, in pixels and
     * not scaled. Repeated here because the fields are package private, so setting an offset at all
     * means replacing that default instead of adding to it.
     */
    private static final int COPYRIGHT_EDGE_MARGIN = 10;

    private CopyrightOverlay mCopyrightOverlay;

    private boolean mOffsetCopyrightForSystemBars;

    /**
     * Only the maps that fill a window ask for this. Two of the four do; the other two are short
     * maps inside a card on the place screens, where offsetting the notice by a navigation bar they
     * never touch would park it in the middle of the card.
     */
    public void keepCopyrightClearOfSystemBars() {
        mOffsetCopyrightForSystemBars = true;
        if (mCopyrightOverlay != null) {
            applyCopyrightOffset();
        }
    }

    private void setupCopyrightOverlay() {
        // OpenStreetMap requires that you add “© OpenStreetMap contributors” to the map
        mCopyrightOverlay = new CopyrightOverlay(mMapView.getContext());
        mMapView.getOverlayManager().add(0, mCopyrightOverlay);
        applyCopyrightOffset();
    }

    /**
     * A map that fills a window is not padded, because it is meant to run under the navigation bar.
     * The notice still has to stay readable, and it is drawn on the canvas instead of laid out, so
     * it moves by its own offset. A larger offset draws it higher, since it is aligned to the
     * bottom of the canvas.
     */
    private void applyCopyrightOffset() {
        if (!mOffsetCopyrightForSystemBars) {
            return;
        }
        final CopyrightOverlay overlay = mCopyrightOverlay;
        ViewCompat.setOnApplyWindowInsetsListener(mMapView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            overlay.setOffset(bars.left + COPYRIGHT_EDGE_MARGIN, bars.bottom + COPYRIGHT_EDGE_MARGIN);
            view.invalidate();
            return insets;
        });
        mMapView.requestApplyInsets();
    }

    public void onCreate(Bundle savedInstanceState) {
        // here we can initialize the copyright overlay
        setupCopyrightOverlay();
    }

    public void onStart() {
        // do nothing
    }

    public void onResume() {
        mMapView.onResume();
    }

    public void onPause() {
        mMapView.onPause();
    }

    public void onStop() {
        // do nothing
    }

    public void onDestroy() {
        // do nothing
    }

    public void onSaveInstanceState(Bundle outState) {
        // do nothing
    }

    public void onLowMemory() {
        // do nothing
    }

    public void loadMapAsync(OnMapLoadedCallback callback) {
        // it does not require to load anything in background
        // so we can just fire the callback immediately
        if (callback != null) {
            callback.onMapReady();
        }
    }

    public boolean isMapReady() {
        return mMapView != null;
    }

    public void disableMapInteractions() {
        mMapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
    }

    private Drawable getMarkerIcon() {
        return mMapView.getContext().getResources().getDrawable(R.drawable.ic_osm_marker);
    }

    public void addPlaces(List<Place> places) {
        if (mMapView != null && places != null && !places.isEmpty()) {
            List<GeoPoint> points = new ArrayList<>();
            List<OverlayItem> items = new ArrayList<>();
            for (Place place : places) {
                if (place.hasCoordinates()) {
                    Coordinates coordinates = place.getCoordinates();
                    String identifier = String.valueOf(place.getId());
                    String name = place.getName();
                    String description = place.getAddress();
                    GeoPoint geoPoint = new GeoPoint(coordinates.getLatitude(), coordinates.getLongitude());
                    items.add(new OverlayItem(identifier, name, description, geoPoint));
                    points.add(geoPoint);
                }
            }
            ItemizedOverlayWithFocus<OverlayItem> overlay = new ItemizedOverlayWithFocus<>(items, getMarkerIcon(), getMarkerIcon(), OverlayConstants.NOT_SET, new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                @Override
                public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                    if (mInfoWindowClickListener != null && item.getUid() != null) {
                        long parsedId = Long.parseLong(item.getUid());
                        mInfoWindowClickListener.onInfoWindowClick(parsedId);
                    }
                    return true;
                }

                @Override
                public boolean onItemLongPress(final int index, final OverlayItem item) {
                    return false;
                }

            }, mMapView.getContext());
            overlay.setFocusItemsOnTap(true);
            overlay.setMarkerTitleForegroundColor(Color.BLACK);
            overlay.setMarkerDescriptionForegroundColor(Color.BLACK);
            overlay.setMarkerBackgroundColor(Color.WHITE);

            overlay.setDescriptionBoxPadding(10);
            overlay.setDescriptionBoxCornerWidth(10);

            mMapView.getOverlays().add(overlay);
            if (!points.isEmpty()) {
                BoundingBox boundingBox = BoundingBox.fromGeoPoints(points);
                if (mMapView.isLayoutOccurred()) {
                    zoomToBoundingBox(boundingBox);
                } else {
                    mMapView.addOnFirstLayoutListener((view, left, top, right, bottom) -> zoomToBoundingBox(boundingBox));
                }
            }
        }
    }

    private void zoomToBoundingBox(BoundingBox boundingBox) {
        int border = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MARKER_HEIGHT_DP,
                mMapView.getResources().getDisplayMetrics());
        if (hasRoomForBorder(border)) {
            mMapView.zoomToBoundingBox(boundingBox, false, border, DEFAULT_ZOOM_LEVEL, null);
        } else {
            mMapView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (hasRoomForBorder(border)) {
                        view.removeOnLayoutChangeListener(this);
                        zoomToBoundingBox(boundingBox);
                    }
                }

            });
        }
    }

    private boolean hasRoomForBorder(int border) {
        return mMapView.getWidth() > 2 * border && mMapView.getHeight() > 2 * border;
    }

    public void addCoordinates(Coordinates coordinates) {
        if (mMapView != null && coordinates != null) {
            GeoPoint geoPoint = new GeoPoint(coordinates.getLatitude(), coordinates.getLongitude());
            List<OverlayItem> items = new ArrayList<>();
            items.add(new OverlayItem("", "", geoPoint));
            ItemizedOverlayWithFocus<OverlayItem> overlay = new ItemizedOverlayWithFocus<>(items, getMarkerIcon(), null, OverlayConstants.NOT_SET, new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                @Override
                public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                    return false;
                }

                @Override
                public boolean onItemLongPress(final int index, final OverlayItem item) {
                    return false;
                }

            }, mMapView.getContext());
            overlay.setFocusItemsOnTap(true);
            mMapView.getOverlays().add(overlay);
            // setup map-view using current point as center
            mMapView.getController().setCenter(geoPoint);
            mMapView.getController().setZoom(DEFAULT_ZOOM_LEVEL);
        }
    }

    public void clear() {
        mMapView.getOverlays().clear();
        setupCopyrightOverlay();
    }

    public void setOnInfoClickListener(OnInfoWindowClickListener callback) {
        mInfoWindowClickListener = callback;
    }

    public Coordinates getCenterCoordinates() {
        IGeoPoint geoPoint = mMapView.getMapCenter();
        return new Coordinates(geoPoint.getLatitude(), geoPoint.getLongitude());
    }

    public void setMinZoomLevel() {
        mMapView.getController().setZoom(DEFAULT_MIN_ZOOM_LEVEL);
    }

    public interface OnMapLoadedCallback {

        void onMapReady();
    }

    public interface OnInfoWindowClickListener {

        void onInfoWindowClick(long placeId);
    }
}