package com.sandersmart.townnotify;

import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.util.HashMap;

public class NearbyTownsActivity extends AppCompatActivity {

    HashMap<Location, Bundle> addressMap = new HashMap<>();

    Button populateButton;
    ListView townList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_towns);

        populateButton = (Button) findViewById(R.id.populateButton);
        populateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //populateNearbyTownList();
            }
        });

        townList = (ListView) findViewById(R.id.townList);
    }

    public static Location createOffsetLocation(Location location, int dx, int dy) {
        Location offsetLocation = new Location("");

        double offsetLatitude = location.getLatitude() + (180 / Math.PI) * (dx / 6378137);
        double offsetLongitude = location.getLongitude() + (180 / Math.PI) * (dy / 6378136) * Math.cos(Math.PI/180 * location.getLatitude());

        offsetLocation.setLatitude(offsetLatitude);
        offsetLocation.setLongitude(offsetLongitude);

        return offsetLocation;
    }

    private void populateNearbyTownList(Location location) {
        int X = 5;
        int Y = 5;
        int d = 1000; //1000 meters or 1 KM
        for(int i = 0; i < X; i++) {
            for(int j = 0; j < Y; j++) {
                Location offsetLocation = createOffsetLocation(location, d * i, d * j);
                requestAddressForLocation(offsetLocation);
            }
        }
    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string
            // or an error message sent from the intent service.
            //mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            //displayAddressOutput();
            //Log.d("onReceiveResult", "" + mAddressOutput);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //mMainText.setText("You are at " + mAddressOutput);
                }
            });


            // Show a toast message if an address was found.
//            if (resultCode == Constants.SUCCESS_RESULT) {
//                String town = resultData.getString("Town");
//                if(!town.equals(currentTown)) {
//                    createTownChangeNotification(currentTown, town);
//                    currentTown = town;
//                }
//                String state = resultData.getString("State");
//                if(!state.equals(currentState)) {
//                    createStateChangeNotification(currentState, state);
//                    currentState = state;
//                }
//                //showToast(getString(R.string.address_found));
//            }

        }
    }

    private NearbyTownsActivity.AddressResultReceiver mResultReceiver = new NearbyTownsActivity.AddressResultReceiver(null);

    protected void requestAddressForLocation(Location location) {

        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);
        startService(intent);
    }
}
