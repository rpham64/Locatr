package com.bignerdranch.android.locatr;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

/**
 * created feature2
 *
 * Displays map with images taken at a given location
 *
 * Created by Rudolf on 3/24/2016.
 */
public class LocatrFragment extends SupportMapFragment {

    private static final String TAG = "LocatrFragment";

    private GoogleApiClient mGoogleApiClient;
    private GoogleMap mMap;

    private Bitmap mMapImage;
    private GalleryItem mMapItem;
    private Location mCurrentLocation;

    private ProgressDialog mProgressDialog;

    public LocatrFragment() {
    }

    public static LocatrFragment newInstance() {
        return new LocatrFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        getActivity().invalidateOptionsMenu();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .build();

        /** Loading Progress Dialog */
        mProgressDialog = new ProgressDialog(getActivity(), ProgressDialog.STYLE_SPINNER);

        mProgressDialog.setMessage("Downloading image...");

        /** Creating the Map */
        getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                updateUI();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().invalidateOptionsMenu();  // Recreate options menu
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();

        mGoogleApiClient.disconnect();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_locatr, menu);

        MenuItem locateItem = menu.findItem(R.id.action_locate);
        locateItem.setEnabled(mGoogleApiClient.isConnected());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.action_locate:

                mProgressDialog.show();
                findImage();

                return true;

            default:
                return super.onOptionsItemSelected(item);

        }

    }

    /**
     * Retrieves a fix for the user's current location
     */
    private void findImage() {

        // Build location fix request
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setNumUpdates(1);
        locationRequest.setInterval(0);

        if (ActivityCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getActivity(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // Send off request and listen for Locations that are returned
        LocationServices.FusedLocationApi
                .requestLocationUpdates(mGoogleApiClient, locationRequest, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        Log.i(TAG, "Got a location fix: " + location);
                        new SearchTask().execute(location);
                    }
                });

    }

    /**
     * Updates UI to display zoomed-in area of interest
     */
    private void updateUI() {

        if (mMap == null || mMapImage == null) return;

        // Retrieve current and image locations
        LatLng imagePoint = new LatLng(mMapItem.getLatitude(), mMapItem.getLongitude());
        LatLng myPoint = new LatLng(
                mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());

        Log.i(TAG, "ImagePoint: " + imagePoint.toString());
        Log.i(TAG, "MyPoint: " + myPoint.toString());

        // Create location markers
        BitmapDescriptor imageBitmap = BitmapDescriptorFactory.fromBitmap(mMapImage);

        MarkerOptions imageMarker = new MarkerOptions()
                .position(imagePoint)
                .icon(imageBitmap);

        MarkerOptions myMarker = new MarkerOptions()
                .position(myPoint);

        mMap.clear();
        mMap.addMarker(imageMarker);
        mMap.addMarker(myMarker);

        // Create map area bounds
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(imagePoint)
                .include(myPoint)
                .build();

        // Update camera
        int margin = getResources().getDimensionPixelSize(R.dimen.map_inset_margin);
        CameraUpdate update = CameraUpdateFactory.newLatLngBounds(bounds, margin);

        mMap.animateCamera(update);
    }

    private class SearchTask extends AsyncTask<Location, Void, Void> {

        private GalleryItem mGalleryItem;
        private Bitmap mBitmap;
        private Location mLocation;

        /**
         * Download first GalleryItem that comes up
         *
         * @param params
         * @return
         */
        @Override
        protected Void doInBackground(Location... params) {

            mLocation = params[0];
            FlickrFetchr flickrFetchr = new FlickrFetchr();
            List<GalleryItem> items = flickrFetchr.searchPhotos(params[0]);

            if (items.isEmpty()) return null;

            mGalleryItem = items.get(0);

            // Download Bitmap from mGalleryItem's URL
            try {
                byte[] bytes = flickrFetchr.getUrlBytes(mGalleryItem.getUrl());
                mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (IOException ioe) {
                Log.i(TAG, "Unable to download bitmap", ioe);
            }

            return null;
        }

        /**
         * Post mGalleryItem to mImageView
         *
         * @param result
         */
        @Override
        protected void onPostExecute(Void result) {
            mMapImage = mBitmap;
            mMapItem = mGalleryItem;
            mCurrentLocation = mLocation;

            mProgressDialog.dismiss();
            updateUI();
        }
    }
}
