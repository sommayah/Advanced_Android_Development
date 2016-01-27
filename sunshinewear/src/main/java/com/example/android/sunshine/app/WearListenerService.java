package com.example.android.sunshine.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;

public class WearListenerService extends WearableListenerService {
    public WearListenerService() {
    }


    private static final String TAG = "ListenerService";

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";
    public static final String COUNT_PATH = "/count";
    public static final String IMAGE_PATH = "/image";
    public static final String IMAGE_KEY = "photo";
    GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged(): " + dataEvents);

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (WearListenerService.IMAGE_PATH.equals(path)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    Asset photoAsset = dataMapItem.getDataMap()
                            .getAsset(WearListenerService.IMAGE_KEY);
                    // Loads image on background thread.
                    new LoadBitmapAsyncTask().execute(photoAsset);

                } else if (WearListenerService.COUNT_PATH.equals(path)) {
                    Log.d(TAG, "Data Changed for COUNT_PATH");
                    Log.d("DataItem Changed", event.getDataItem().toString());
                } else {
                    Log.d(TAG, "Unrecognized path: " + path);
                }

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d("DataItem Deleted", event.getDataItem().toString());
            } else {
                Log.d("Unknown data event type", "Type = " + event.getType());
            }
        }
    }

    private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Asset... params) {

            if (params.length > 0) {

                Asset asset = params[0];

                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream);

            } else {
                Log.e(TAG, "Asset must be non-null");
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {

            if (bitmap != null) {
                Log.d(TAG, "Setting icon image");
                //  mAssetFragment.setBackgroundImage(bitmap);
                Bitmap mIconBitmap = Bitmap.createBitmap(bitmap);
            }
        }
    }

//    @Override
//    public void onDataChanged(DataEventBuffer dataEvents) {
//        LOGD(TAG, "onDataChanged: " + dataEvents);
//        if (!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
//            ConnectionResult connectionResult = mGoogleApiClient
//                    .blockingConnect(30, TimeUnit.SECONDS);
//            if (!connectionResult.isSuccess()) {
//                Log.e(TAG, "WearListenerService failed to connect to GoogleApiClient, "
//                        + "error code: " + connectionResult.getErrorCode());
//                return;
//            }
//        }
//
//        // Loop through the events and send a message back to the node that created the data item.
//        for (DataEvent event : dataEvents) {
//            Uri uri = event.getDataItem().getUri();
//            String path = uri.getPath();
//            if (COUNT_PATH.equals(path)) {
//                // Get the node id of the node that created the data item from the host portion of
//                // the uri.
//                String nodeId = uri.getHost();
//                // Set the data of the message to be the bytes of the Uri.
//                byte[] payload = uri.toString().getBytes();
//
//                // Send the rpc
//                Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, DATA_ITEM_RECEIVED_PATH,
//                        payload);
//            }
//        }
//    }



    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        LOGD(TAG, "onMessageReceived: " + messageEvent);

        // Check to see if the message is to start an activity
        if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
            Intent startIntent = new Intent(this, SunShineWatchFace.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startIntent);
        }
    }

    @Override
    public void onPeerConnected(Node peer) {
        LOGD(TAG, "onPeerConnected: " + peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        LOGD(TAG, "onPeerDisconnected: " + peer);
    }

    public static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

//    private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {
//
//        @Override
//        protected Bitmap doInBackground(Asset... params) {
//
//            if(params.length > 0) {
//
//                Asset asset = params[0];
//
//                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
//                        mGoogleApiClient, asset).await().getInputStream();
//
//                if (assetInputStream == null) {
//                    Log.w(TAG, "Requested an unknown Asset.");
//                    return null;
//                }
//                return BitmapFactory.decodeStream(assetInputStream);
//
//            } else {
//                Log.e(TAG, "Asset must be non-null");
//                return null;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Bitmap bitmap) {
//
//            if(bitmap != null) {
//                Log.d(TAG, "Setting weather icon");
//               // mIconBitmap = Bitmap.createBitmap(bitmap,0,0,20,20);
//
//            }
//        }
//    }



}
