package com.sandersmart.townnotify;

import android.Manifest;
//import android.app.NotificationChannel;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.location.Location;
import android.os.Handler;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    Location previousLocation = null;

    HashMap<Location, Bundle> addressMap = new HashMap<>();
    ArrayList<Bundle> nearbyList = new ArrayList<>();
    HashMap<String, Integer> townColors = new HashMap<>();

    int numRequested = 0;
    int numReceived = 0;
    int numFromCache = 0;

    public static final int REQUEST_LOCATION = 99;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private long UPDATE_INTERVAL = 12000;
    private long FASTEST_INTERVAL = 12000;

    TextView mMainText;
    TextView mProgressText;
    Button mPopulateButton;
    RecyclerView townRV;
    TownRVAdapter mAdapter;
    RecyclerView.LayoutManager mLayoutManager;
    ProgressBar progressBar;
    SwipeRefreshLayout mSwipeRefreshLayout;
    SurfaceView surfaceView;

    String mAddressOutput = "";

    String currentTown = "";
    String currentState = "";

    TextToSpeech ttobj;

    public static int[] colorArray = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.YELLOW, Color.MAGENTA,
                                        Color.rgb(255, 165, 0), //orange
                                        Color.rgb(0, 100, 0), //dark green
                                        Color.rgb(218,112,214),
                                        Color.rgb(255,153,153)};

    class MySurfaceView extends SurfaceView {
        public MySurfaceView(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
        }

        protected void onDraw(Canvas canvas) {
            Log.d(this.getClass().getName(), "onDraw called!");
        }
    }

    class LocationInfo {
        Location location;

    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {


            try {
                // Display the address string
                // or an error message sent from the intent service.
                mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
                Location location = resultData.getParcelable("Location");
                addressMap.put(location, resultData);

                int requestType = resultData.getInt("RequestType");
                //displayAddressOutput();
                Log.d("onReceiveResult", "" + mAddressOutput);

                if (requestType == 2) {
                    numReceived++;
                    progressBar.setProgress(numReceived);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mProgressText.setText("" + numReceived + "/" + numRequested + " " + numFromCache + " from Cache");
                        }
                    });

                    Log.d("onReceiveResult", "Request #" + numReceived + " received!");
                    if (numReceived == numRequested) {
                        Log.d("onReceiveResult", "All " + numRequested + " have been completed!");
                        updateNearbyTownList();
                    }
                }

                if (requestType != 1) return;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMainText.setText("You are at " + mAddressOutput);
                    }
                });


                // Show a toast message if an address was found.
                if (resultCode == Constants.SUCCESS_RESULT) {
                    String town = resultData.getString("Town");
                    if (!town.equals(currentTown)) {
                        if(!currentTown.equals(""))
                            createTownChangeNotification(currentTown, town);
                        currentTown = town;
                    }
                    String state = resultData.getString("State");
                    if (!state.equals(currentState)) {
                        if(!currentState.equals(""))
                            createStateChangeNotification(currentState, state);
                        currentState = state;
                    }
                    //showToast(getString(R.string.address_found));
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "onReceiveResult: Unhandled exception caught", Toast.LENGTH_LONG).show();
            }

        }
    }

    protected Location mLastLocation;
    private AddressResultReceiver mResultReceiver = new AddressResultReceiver(null);

    protected void startIntentService() {

        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        intent.putExtra("RequestType", 1);
        startService(intent);
    }

    protected void requestAddressForLocation(Location location) {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);
        intent.putExtra("RequestType", 2);
        startService(intent);
    }

    public void mySpeak(final String mytext) {
        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttobj.setLanguage(Locale.US);
                String utteranceId=this.hashCode() + "";
                ttobj.speak(mytext, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);//Menu Resource, Menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings_option:
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivityForResult(intent, 1);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMainText = (TextView) findViewById(R.id.mainText);
        mProgressText = (TextView) findViewById(R.id.progressTextView);
        mPopulateButton = (Button) findViewById(R.id.populateButton);

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setZOrderOnTop(true);
        surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        surfaceView.setWillNotDraw(false);
        //surfaceView.getHolder().setFixedSize(100,100);

        surfaceView.setVisibility(View.GONE);

        townRV = (RecyclerView) findViewById(R.id.townsRV);
        townRV.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        townRV.setLayoutManager(mLayoutManager);

        // specify an adapter (see also next example)
        mAdapter = new TownRVAdapter(nearbyList);
        townRV.setAdapter(mAdapter);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setScaleY(12f);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);

        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        //Log.i(LOG_TAG, "onRefresh called from SwipeRefreshLayout");

                        // This method performs the actual data-refresh operation.
                        // The method calls setRefreshing(false) when it's finished.
                        populateNearbyTownList(mLastLocation);
                    }
                }
        );

//        mPopulateButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                populateNearbyTownList(mLastLocation);
//            }
//        });

        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttobj.setLanguage(Locale.US);
                String utteranceId=this.hashCode() + "";
                //ttobj.speak("Testing 1 2 3", TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            }
        });

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Check Permissions Now
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION);
        } else {

            setupLocationServices();
        }

    }

    public void setupLocationServices() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        LocationCallback mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {

                for (Location location : locationResult.getLocations()) {
                    LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());
                    Log.d("onLocationResult", "" + location.getLatitude() + ", " + location.getLongitude() +
                            " " + location.getAltitude());

                    mLastLocation = location;
                    if (previousLocation == null) {
                        previousLocation = mLastLocation;
                    } else {
                        if (previousLocation.distanceTo(mLastLocation) >= 1000) {
                            previousLocation = mLastLocation;
                            Toast.makeText(getApplicationContext(), "You have traveled at least 1 KM updating nearby town list...", Toast.LENGTH_LONG).show();
                            populateNearbyTownList(previousLocation);
                        }
                    }
                    startIntentService();
                }
            }

            ;
        };

        try {

            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback,
                    null /* Looper */);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    setupLocationServices();

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void onDraw(Canvas canvas) {
        Log.d(this.getClass().getName(), "onDraw Called!!!");
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(this.getClass().getName(), "surfaceCreated called!");
        if(nearbyList.size() > 0) {
            drawRects();
        }
    }

    private void setTownColors() {
        int range = 10000;
        int colorIndex = 0;
        for(Bundle bundle : nearbyList) {
            int color = colorArray[colorIndex];
            colorIndex++;
            if(colorIndex >= colorArray.length)
                colorIndex = 0;
            bundle.putInt("Color", color);
        }
    }

    private int getTownColor(String town) {
        for(Bundle bundle : nearbyList) {
            if(bundle.getString("Town").equals(town))
                return bundle.getInt("Color", Color.BLACK);
        }
        return Color.BLACK;
    }

    private void drawRects() {
        surfaceView.setVisibility(View.VISIBLE);
        Canvas canvas = surfaceView.getHolder().lockCanvas();
        if(canvas == null) {
            Log.d(this.getClass().getName(), "drawRects: canvas is null!");
            return;
        }
        int totalWidth = canvas.getWidth();
        int totalHeight = canvas.getHeight();
        int X = 10;
        int Y = 10;
        int d = 1000;
        int dX = totalWidth / X;
        int dY = totalHeight / Y;
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        int colorIndex = 0;

        Bundle cachedCurrentLocation = getCachedLocation(mLastLocation, 500);
        String currentTown = cachedCurrentLocation.getString("Town");

        //canvas.drawRect(0, 0, totalWidth / 10, 25, paint);

        //setTownColors();

        canvas.rotate(270, totalWidth / 2, totalHeight / 2);

        for(int i = 0; i < X; i++) {
            for(int j = 0; j < Y; j++) {
                int di = i - X / 2;
                int dj = j - Y / 2;
                Location offsetLocation = createOffsetLocation(mLastLocation, d * di, d * dj);
                Bundle cachedLocation = getCachedLocation(offsetLocation, 500);
//                if(cachedLocation.getString("Town").equals(currentTown)) {
//                    canvas.drawRect(i * dX, i * dY, (i + 1) * dX, (j + 1) * dY, paint);
//                }
                int color = getTownColor(cachedLocation.getString("Town"));
                paint.setColor(color);
                canvas.drawRect(i * dX, j * dY, (i + 1) * dX, (j + 1) * dY, paint);
            }
        }

        surfaceView.getHolder().unlockCanvasAndPost(canvas);
    }

    public static Location createOffsetLocation(Location location, int dx, int dy) {
        Location offsetLocation = new Location("");

        double offsetLatitude = location.getLatitude() + (180 / Math.PI) * ((double)dx / 6378137);
        double offsetLongitude = location.getLongitude() + (180 / Math.PI) * ((double)dy / 6378136) * Math.cos(Math.PI/180 * location.getLatitude());

        offsetLocation.setLatitude(offsetLatitude);
        offsetLocation.setLongitude(offsetLongitude);

        return offsetLocation;
    }

    private void populateNearbyTownList(Location location) {
        int X = 10;
        int Y = 10;

        numRequested = X * Y;
        numReceived = 0;
        numFromCache = 0;

        progressBar.setMax(numRequested);
        progressBar.setProgress(0);

        int d = 1000; //1000 meters or 1 KM
        for(int i = -X / 2; i < X / 2; i++) {
            for(int j = - Y / 2; j < Y / 2; j++) {
                Location offsetLocation = createOffsetLocation(location, d * i, d * j);
                Bundle cachedLocation = getCachedLocation(offsetLocation, 500);
                if(cachedLocation == null) {
                    Log.d("populateNearbyTownList", "A reverse geocoding request has been made.");
                    requestAddressForLocation(offsetLocation);
                } else {
                    numReceived++;
                    numFromCache++;
                    progressBar.setProgress(numReceived);
                    mProgressText.setText("" + numReceived + "/" + numRequested + " " + numFromCache + " from Cache");
                    Log.d("populateNearbyTownList", "Request #" + numReceived + " completed from cache!");
                    if(numReceived == numRequested) {
                        Log.d("populateNearbyTownList", "All " + numRequested + " have been completed!");
                        updateNearbyTownList();
                    }
                }
            }
        }
    }

    private void updateNearbyTownList() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int range = Integer.parseInt(sharedPreferences.getString("pref_town_search_range", "7000"));

        nearbyList = computeNearbyTowns(mLastLocation, range);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                sortBundlesByDistance(nearbyList);

                setTownColors();
                drawRects();

                mAdapter.mDataset = nearbyList;
                mAdapter.currentLocation = mLastLocation;
                mAdapter.notifyDataSetChanged();
                mSwipeRefreshLayout.setRefreshing(false);
//                            mAdapter = new TownRVAdapter(nearbyList);
//                            townRV.setAdapter(mAdapter);

            }
        });
    }

    private void sortBundlesByName(ArrayList<Bundle> bundles) {
        Collections.sort(bundles, new Comparator<Bundle>() {
            @Override
            public int compare(Bundle lhs, Bundle rhs) {
                // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
                //double leftDistance = mLastLocation.distanceTo((Location)lhs.getParcelable("Location"));
                //double rightDistance = mLastLocation.distanceTo((Location)rhs.getParcelable("Location"));

                return lhs.getString("Town").compareTo(rhs.getString("Town"));
            }
        });
    }

    private void sortBundlesByDistance(ArrayList<Bundle> bundles) {
        Collections.sort(bundles, new Comparator<Bundle>() {
            @Override
            public int compare(Bundle lhs, Bundle rhs) {
                // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
                double leftDistance = mLastLocation.distanceTo((Location)lhs.getParcelable("Location"));
                double rightDistance = mLastLocation.distanceTo((Location)rhs.getParcelable("Location"));

                if(leftDistance > rightDistance)
                    return 1;
                else if(rightDistance > leftDistance)
                    return -1;
                else
                    return 0;
            }
        });
    }

    private void sortBundlesByBearing(ArrayList<Bundle> bundles) {
        Collections.sort(bundles, new Comparator<Bundle>() {
            @Override
            public int compare(Bundle lhs, Bundle rhs) {
                // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
                float leftBearing = mLastLocation.bearingTo((Location)lhs.getParcelable("Location"));
                float rightBearing = mLastLocation.bearingTo((Location)rhs.getParcelable("Location"));

                if(leftBearing < 0) leftBearing += 360;
                if(rightBearing < 0) rightBearing += 360;

                if(leftBearing > rightBearing)
                    return 1;
                else if(rightBearing > leftBearing)
                    return -1;
                else
                    return 0;
            }
        });
    }


    private Bundle getCachedLocation(Location targetLocation, int range) {
        for(Location location : addressMap.keySet()) {
            try {
                if (targetLocation.distanceTo(location) < range) {
                    return addressMap.get(location);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private ArrayList<Bundle> computeNearbyTowns(Location origin, int range) {
        ArrayList<Bundle> nearbyTownBundles = new ArrayList<>();
        ArrayList<String> townList = new ArrayList<>();
        for(Location location : addressMap.keySet()) {
            if(location == null) continue;
            float distance = origin.distanceTo(location);
            if(distance <= range) {
                Bundle resultData = addressMap.get(location);
                String town = resultData.getString("Town");
                String state = resultData.getString("State");
                String address = resultData.getString(Constants.RESULT_DATA_KEY);
                Log.d("computeNearbyTowns", "" + town + " is within range, " + distance + " about meters away");
                int index = townList.indexOf(town + ", " + state);
                if(index == -1) {
                    townList.add(town + ", " + state);
                    nearbyTownBundles.add(resultData);
                } else {
                    Location townLocation = nearbyTownBundles.get(index).getParcelable("Location");
                    float previousDistance = origin.distanceTo(townLocation);
                    if(distance < previousDistance) {
                        nearbyTownBundles.set(index, resultData);
                    }
                }
            }
        }
        Log.d("computeNearbyTowns", townList.toString());
        return nearbyTownBundles;
    }

    public void createTownChangeNotification(String previousTown, String newTown) {
        // The id of the channel.
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean enableNotifications = sharedPreferences.getBoolean("notifications_town_change", false);
            if(!enableNotifications)
                return;
            String CHANNEL_ID = "my_channel_01";
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(this)
                            .setPriority(Notification.PRIORITY_HIGH)
                            .setVibrate(new long[0])
                            .setSmallIcon(R.drawable.cast_ic_notification_small_icon)
                            .setContentTitle("Town Change!")
                            .setContentText("Entering " + newTown);
            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(this, MainActivity.class);

            // The stack builder object will contain an artificial back stack for the
            // started Activity.
            // This ensures that navigating backward from the Activity leads out of
            // your app to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(MainActivity.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                    stackBuilder.getPendingIntent(
                            0,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // mNotificationId is a unique integer your app uses to identify the
            // notification. For example, to cancel the notification, you can pass its ID
            // number to NotificationManager.cancel().
            int mNotificationId = 1;
            mNotificationManager.notify(mNotificationId, mBuilder.build());

            String utteranceId = this.hashCode() + "";

            boolean enableTTS = sharedPreferences.getBoolean("notification_town_change_tts", false);
            if(enableTTS)
                ttobj.speak("Entering " + newTown, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Exception occurred in createTownChangeNotification", Toast.LENGTH_LONG).show();
        }
        //mySpeak("Entering " + newTown);
    }

    public void createStateChangeNotification(String previousState, String newState) {
        // The id of the channel.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableNotifications = sharedPreferences.getBoolean("notifications_town_change", false);
        if(!enableNotifications)
            return;
        String CHANNEL_ID = "my_channel_02";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setVibrate(new long[0])
                        .setSmallIcon(R.drawable.cast_ic_notification_small_icon)
                        .setContentTitle("State Change!")
                        .setContentText("Entering " + newState);
// Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);

// The stack builder object will contain an artificial back stack for the
// started Activity.
// This ensures that navigating backward from the Activity leads out of
// your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
// Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
// Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_ONE_SHOT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

// mNotificationId is a unique integer your app uses to identify the
// notification. For example, to cancel the notification, you can pass its ID
// number to NotificationManager.cancel().
        int mNotificationId = 2;
        mNotificationManager.notify(mNotificationId, mBuilder.build());

        String utteranceId=this.hashCode() + "";
        boolean enableTTS = sharedPreferences.getBoolean("notification_town_change_tts", false);
        if(enableTTS)
            ttobj.speak("Entering " + newState, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        //mySpeak("Entering " + newState);
    }

    public void createSampleNotification() {
        // The id of the channel.
        String CHANNEL_ID = "my_channel_01";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setVibrate(new long[0])
                        .setSmallIcon(R.drawable.cast_ic_notification_small_icon)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!");
// Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);

// The stack builder object will contain an artificial back stack for the
// started Activity.
// This ensures that navigating backward from the Activity leads out of
// your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
// Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
// Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

// mNotificationId is a unique integer your app uses to identify the
// notification. For example, to cancel the notification, you can pass its ID
// number to NotificationManager.cancel().
        int mNotificationId = 1;
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }


}
