package com.oriondev.moneywallet.view;

import android.graphics.Point;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.Coordinates;
import com.oriondev.moneywallet.model.Place;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MapViewWrapperTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int NARROW_WIDTH = 720;
    private static final int NARROW_HEIGHT = 1280;
    private static final int TINY_SIZE = 80;
    private static final int BORDER_MDPI = 48;
    private static final int BORDER_XXHDPI = 144;
    private static final int EDGE_TOLERANCE = 2;
    private static final double MINIMUM_ZOOM = 2d;
    private static final double CAPPED_ZOOM = 14d;
    private static final double PANNED_ZOOM = 5d;

    private static final Place RALEIGH = new Place(1, "Raleigh", null, null, 35.78, -78.64);
    private static final Place GUADALAJARA = new Place(2, "Guadalajara", null, null, 20.67, -103.35);
    private static final Place LISBON = new Place(3, "Lisbon", null, null, 38.72, -9.14);
    private static final Place NOWHERE = new Place(4, "Nowhere", null, null, (Coordinates) null);
    private static final Place ROSSIO = new Place(5, "Rossio", null, null, 38.71390, -9.13940);
    private static final Place CARMO = new Place(6, "Carmo", null, null, 38.71410, -9.13960);
    private static final Place CHIADO = new Place(7, "Chiado", null, null, 38.71380, -9.13970);
    private static final List<Place> CITIES = Arrays.asList(RALEIGH, GUADALAJARA, LISBON);

    private MapView mapView() {
        return new MapView(ApplicationProvider.getApplicationContext()) {

            @Override
            public double zoomToBoundingBox(BoundingBox boundingBox, boolean animated, int borderSizeInPixels,
                                            double maximumZoom, Long animationSpeed) {
                assertTrue("the fit ran on a " + getWidth() + "x" + getHeight() + " map with a "
                                + borderSizeInPixels + " px border, which never returns",
                        getWidth() > 2 * borderSizeInPixels && getHeight() > 2 * borderSizeInPixels);
                return super.zoomToBoundingBox(boundingBox, animated, borderSizeInPixels, maximumZoom,
                        animationSpeed);
            }

        };
    }

    private void layOut(MapView mapView) {
        layOut(mapView, WIDTH, HEIGHT);
    }

    private void layOut(MapView mapView, int width, int height) {
        mapView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        mapView.layout(0, 0, width, height);
        assertTrue("the map did not report its first layout", mapView.isLayoutOccurred());
        assertEquals(width, mapView.getWidth());
        assertEquals(height, mapView.getHeight());
    }

    private void assertInView(MapView mapView, List<Place> places) {
        BoundingBox visible = mapView.getProjection().getBoundingBox();
        for (Place place : places) {
            Coordinates coordinates = place.getCoordinates();
            assertTrue(place.getName() + " is not in view, the visible box is " + visible,
                    visible.contains(coordinates.getLatitude(), coordinates.getLongitude()));
        }
    }

    private void assertClearOfTheEdges(MapView mapView, List<Place> places, int border) {
        Projection projection = mapView.getProjection();
        for (Place place : places) {
            Coordinates coordinates = place.getCoordinates();
            Point pixel = projection.toPixels(new GeoPoint(coordinates.getLatitude(), coordinates.getLongitude()), null);
            String where = place.getName() + " is at " + pixel + " on a " + mapView.getWidth() + "x"
                    + mapView.getHeight() + " map, closer than " + border + " px to an edge";
            assertTrue(where, pixel.x >= border - EDGE_TOLERANCE);
            assertTrue(where, pixel.y >= border - EDGE_TOLERANCE);
            assertTrue(where, mapView.getWidth() - pixel.x >= border - EDGE_TOLERANCE);
            assertTrue(where, mapView.getHeight() - pixel.y >= border - EDGE_TOLERANCE);
        }
    }

    @Test
    public void threeFarApartCitiesAreAllInViewAfterTheFit() {
        MapView mapView = mapView();
        layOut(mapView);
        List<Place> places = Arrays.asList(RALEIGH, GUADALAJARA, LISBON);
        new MapViewWrapper(mapView).addPlaces(places);
        assertInView(mapView, places);
    }

    @Test
    public void threeFarApartCitiesAreAllInViewOnANarrowScreen() {
        MapView mapView = mapView();
        layOut(mapView, NARROW_WIDTH, NARROW_HEIGHT);
        List<Place> places = Arrays.asList(RALEIGH, GUADALAJARA, LISBON);
        new MapViewWrapper(mapView).addPlaces(places);
        assertInView(mapView, places);
    }

    @Test
    public void placesAddedBeforeTheFirstLayoutAreAllInViewOnceTheMapIsLaidOut() {
        MapView mapView = mapView();
        assertFalse(mapView.isLayoutOccurred());
        List<Place> places = Arrays.asList(RALEIGH, GUADALAJARA, LISBON);
        new MapViewWrapper(mapView).addPlaces(places);
        layOut(mapView);
        assertInView(mapView, places);
    }

    @Test
    public void aSinglePlaceOpensAtTheZoomOfASingleCoordinate() {
        MapView mapView = mapView();
        layOut(mapView);
        List<Place> places = Arrays.asList(LISBON);
        new MapViewWrapper(mapView).addPlaces(places);
        assertEquals(14d, mapView.getZoomLevelDouble(), 0d);
        assertInView(mapView, places);
    }

    @Test
    public void aPlaceWithoutCoordinatesIsSkippedAndTheOthersAreStillInView() {
        MapView mapView = mapView();
        layOut(mapView);
        new MapViewWrapper(mapView).addPlaces(Arrays.asList(RALEIGH, NOWHERE, GUADALAJARA, LISBON));
        List<Place> placed = Arrays.asList(RALEIGH, GUADALAJARA, LISBON);
        assertInView(mapView, placed);
    }

    @Test
    public void aMapTooSmallForTheBorderWaitsForRoom() {
        MapView mapView = mapView();
        layOut(mapView, TINY_SIZE, TINY_SIZE);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        layOut(mapView);
        assertInView(mapView, CITIES);
    }

    @Test
    public void aMapTooShortForTheBorderWaitsForRoom() {
        MapView mapView = mapView();
        layOut(mapView, WIDTH, TINY_SIZE);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        layOut(mapView);
        assertInView(mapView, CITIES);
    }

    @Test
    public void aMapTooNarrowForTheBorderWaitsForRoom() {
        MapView mapView = mapView();
        layOut(mapView, TINY_SIZE, HEIGHT);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        layOut(mapView);
        assertInView(mapView, CITIES);
    }

    @Test
    public void aZeroSizeFirstLayoutWaitsForRoom() {
        MapView mapView = mapView();
        new MapViewWrapper(mapView).addPlaces(CITIES);
        layOut(mapView, 0, 0);
        layOut(mapView);
        assertInView(mapView, CITIES);
    }

    @Test
    public void aZeroSizeFirstLayoutBeforeThePlacesWaitsForRoom() {
        MapView mapView = mapView();
        layOut(mapView, 0, 0);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        layOut(mapView);
        assertInView(mapView, CITIES);
    }

    @Test
    public void aLaterLayoutDoesNotFitAgain() {
        MapView mapView = mapView();
        layOut(mapView, 0, 0);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        layOut(mapView);
        mapView.getController().setZoom(PANNED_ZOOM);
        mapView.getController().setCenter(new GeoPoint(0d, 0d));
        layOut(mapView, NARROW_WIDTH, NARROW_HEIGHT);
        assertEquals(PANNED_ZOOM, mapView.getZoomLevelDouble(), 0d);
        assertEquals(0d, mapView.getMapCenter().getLatitude(), 1e-6);
        assertEquals(0d, mapView.getMapCenter().getLongitude(), 1e-6);
    }

    @Test
    @Config(qualifiers = "mdpi")
    public void everyPlaceIsAtLeastTheBorderFromTheEdge() {
        MapView mapView = mapView();
        layOut(mapView);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        assertClearOfTheEdges(mapView, CITIES, BORDER_MDPI);
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    public void everyPlaceIsAtLeastTheBorderFromTheEdgeAtXxhdpi() {
        MapView mapView = mapView();
        layOut(mapView);
        new MapViewWrapper(mapView).addPlaces(CITIES);
        assertClearOfTheEdges(mapView, CITIES, BORDER_XXHDPI);
    }

    @Test
    public void theMinimumZoomIsTwo() {
        MapView mapView = mapView();
        new MapViewWrapper(mapView);
        assertEquals(MINIMUM_ZOOM, mapView.getMinZoomLevel(), 0d);
    }

    @Test
    public void theWorldDoesNotRepeatVerticallyAtTheMinimumZoom() {
        MapView mapView = mapView();
        new MapViewWrapper(mapView);
        layOut(mapView);
        mapView.getController().setZoom(MINIMUM_ZOOM);
        BoundingBox visible = mapView.getProjection().getBoundingBox();
        assertTrue("the visible box is " + visible, visible.getLatNorth() > visible.getLatSouth());
    }

    @Test
    public void severalPlacesCloseTogetherStopAtTheCap() {
        MapView mapView = mapView();
        layOut(mapView);
        List<Place> places = Arrays.asList(ROSSIO, CARMO, CHIADO);
        new MapViewWrapper(mapView).addPlaces(places);
        assertEquals(CAPPED_ZOOM, mapView.getZoomLevelDouble(), 0d);
        assertInView(mapView, places);
    }
}
