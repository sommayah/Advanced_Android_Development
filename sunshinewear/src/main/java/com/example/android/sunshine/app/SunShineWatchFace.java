/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunShineWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "SunShineWatchFace";
    public static final String IMAGE_PATH = "/image";
    public static final String IMAGE_KEY = "photo";
    private static final String HIGH_KEY = "high";
    private static final String LOW_KEY = "low";
    public static final String ACTION_RECEIVE = "com.example.android.sunshine.app.DATA";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    public Bitmap mIconBitmap;

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {

        return new Engine();
    }






    private static class EngineHandler extends Handler {
        private final WeakReference<SunShineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunShineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunShineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredDataReceiver = false;
        Paint mBackgroundPaint;
        Paint mLinePaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mDividerPaint;
        Paint mIconPaint;
        Paint mHighPaint;
        Paint mLowPaint;
        boolean mAmbient;
        Time mTime;
        Date mDate;
        Calendar mCalendar;
        float mLineHeight;
        String high_temp = "";
        String low_temp = "";

        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;



        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        final BroadcastReceiver mDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //get data here
                Log.d(TAG, intent.getStringExtra("testing"));
                Log.d(TAG, "in on receive");

            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        GoogleApiClient mGoogleApiClient;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        Asset photoAsset;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);


            setWatchFaceStyle(new WatchFaceStyle.Builder(SunShineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());


            Resources resources = SunShineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mLinePaint = new Paint();
            mLinePaint.setColor(resources.getColor(R.color.line_color));
            mLinePaint.setTextAlign(Paint.Align.CENTER);


            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDividerPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mTime = new Time();
            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mIconPaint = new Paint();
            mIconPaint.setColor(Color.BLUE);
            initFormats();

            mGoogleApiClient = new GoogleApiClient.Builder(SunShineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();


        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            //mDateFormat = DateFormat.getDateFormat(SunShineWatchFace.this);
            mDateFormat = DateFormat.getDateInstance();
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {

                mGoogleApiClient.connect();
                registerReceiver();
                registerDataReceiver();


                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
                unregisterReceiver();
                unregisterDataReceiver();



            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunShineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void registerDataReceiver(){
            if(mRegisteredDataReceiver){
                return;
            }
            mRegisteredDataReceiver = true;
            IntentFilter filter = new IntentFilter(ACTION_RECEIVE);
            SunShineWatchFace.this.registerReceiver(mDataReceiver, filter);
        }

        private void unregisterDataReceiver() {
            if (!mRegisteredDataReceiver) {
                return;
            }
            mRegisteredDataReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mDataReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunShineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_size_round : R.dimen.digital_date_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mTextPaint.setTextSize(textSize);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint.setTextSize(dateTextSize);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mHighPaint.setTextSize(tempTextSize);
            mLowPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mDividerPaint.setAntiAlias(!inAmbientMode);
                    mHighPaint.setAntiAlias(!inAmbientMode);
                    mLowPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunShineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            mTime.setToNow();

            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d", mTime.hour, mTime.minute/*, mTime.second*/);
            canvas.drawText(text, bounds.width() / 2, mYOffset, mTextPaint);
            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                // Date
                canvas.drawText(
                        mDayOfWeekFormat.format(mDate) + ", " + mDateFormat.format(mDate),
                        bounds.width() / 2, mYOffset + mLineHeight, mDatePaint);
                canvas.drawLine(bounds.width() * 3 / 8, mYOffset + 2 * mLineHeight,
                        bounds.width() * 5 / 8, mYOffset + 2 * mLineHeight, mLinePaint);
                if(mIconBitmap != null) {
                     /* Scale loaded background image (more efficient) if surface dimensions change. */
                    float scale = ((float) bounds.width()/6) / (float) mIconBitmap.getWidth();

                    mIconBitmap = Bitmap.createScaledBitmap(mIconBitmap,
                            (int) (mIconBitmap.getWidth() * scale),
                            (int) (mIconBitmap.getHeight() * scale), true);
                    canvas.drawBitmap(mIconBitmap, bounds.width() * 1 / 8, mYOffset + 2 * mLineHeight, mIconPaint);
                    //canvas.drawBitmap(mIconBitmap,0, mYOffset + 2 * mLineHeight, mIconPaint);
                }
                if(high_temp != null && low_temp != null) {
                    canvas.drawText(high_temp, bounds.width() * 4 / 8, mYOffset + 3 * mLineHeight, mHighPaint);
                    canvas.drawText(low_temp, bounds.width() * 6 / 8, mYOffset + 3 * mLineHeight, mLowPaint);
                }



            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
           // if (Log.isLoggable(TAG, Log.DEBUG)) {
            //    Log.d(TAG, "onConnected: " + connectionHint);
           // }
            Log.d(TAG, "onConnected: ");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateUiOnStartup();
        }

        private void updateUiOnStartup() {
            new GetDataTask().execute();
        }


        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
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
                        high_temp = dataMapItem.getDataMap()
                                .getString(HIGH_KEY);
                        low_temp = dataMapItem.getDataMap()
                                .getString(LOW_KEY);
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
                    mIconBitmap = Bitmap.createBitmap(bitmap);
                   // mIconBitmap = Bitmap.createBitmap(bitmap,0,0,100,100);
                }
            }
        }



        private Collection<String> getNodes() {
            HashSet<String> results = new HashSet<>();
            NodeApi.GetConnectedNodesResult nodes =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            for (Node node : nodes.getNodes()) {
                results.add(node.getId());
            }

            return results;
        }

        private String getRemoteNodeId() {
            HashSet<String> results = new HashSet<String>();
            NodeApi.GetConnectedNodesResult nodesResult =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
            List<Node> nodes = nodesResult.getNodes();
            if (nodes.size() > 0) {
                return nodes.get(0).getId();
            }
            return null;
        }

        private class GetDataTask extends AsyncTask<Void, Void, Asset> {

            @Override
            protected Asset doInBackground(Void... args) {
                String node = getRemoteNodeId();
                if(node != null){
                    Uri uri = new Uri.Builder()
                            .scheme("wear")
                            .path(IMAGE_PATH)
                            .authority(node)
                            .build();
                    //Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(SunShineWatchFace.this);

                    DataApi.DataItemResult result = Wearable.DataApi.getDataItem(mGoogleApiClient,uri).await();

                        String path = uri.getPath();
                        if (WearListenerService.IMAGE_PATH.equals(path)) {
                            DataMapItem dataItem = DataMapItem.fromDataItem(result.getDataItem());
                            photoAsset = dataItem.getDataMap()
                                    .getAsset(WearListenerService.IMAGE_KEY);
                            high_temp = dataItem.getDataMap()
                                    .getString(HIGH_KEY);
                            low_temp = dataItem.getDataMap()
                                    .getString(LOW_KEY);
                            // Loads image on background thread.
                            return photoAsset;


                        } else if (WearListenerService.COUNT_PATH.equals(path)) {
                            Log.d(TAG, "Data Changed for COUNT_PATH");
                          //  Log.d("DataItem Changed", dataItem.toString());
                        } else {
                            Log.d(TAG, "Unrecognized path: " + path);
                        }

                }
                return null;
            }

            @Override
            protected void onPostExecute(Asset asset) {

                if (asset != null) {
                    Log.d(TAG, "Setting icon image");
                    new LoadBitmapAsyncTask().execute(asset);
                }
            }
        }







    }


}