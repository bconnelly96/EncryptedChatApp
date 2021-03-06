package edu.temple.chatapplication;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity implements NfcAdapter.CreateNdefMessageCallback, ListFragment.ListSelectListener{
    final String USERNAME_FILE = "username";

    MapFragment mapFragment;
    ListFragment listFragment;
    FragmentManager fragmentManager;
    ArrayList<Partner> partnerList = new ArrayList<>();
    String userName;
    boolean dialogCancelled = false;
    boolean isPortMode;
    LocationManager locationManager;
    LocationListener locationUpdateListener;
    KeyService keyService;
    MyFirebaseInstanceIDService fbIDService;


    String partnerPublicKeyString;
    String userKeyForExchange;

    NfcAdapter nfcAdapter;
    boolean connected;
    BroadcastReceiver broadcastReceiver;

    ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            KeyService.TestBinder binder = (KeyService.TestBinder) service;
            keyService = binder.getService();
            connected = true;

            keyService.getMyKeyPair();
            userKeyForExchange = keyService.getUserPublicForExchange("brendan");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connected = false;
        }
    };

    Handler updateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String httpResponse = (String) msg.obj;
            partnerList = updatePartnerList(httpResponse);
            sortPartnerList();

            Bundle listBundle = new Bundle();
            ArrayList<String> listStrings = new ArrayList<>();
            for (int i = 0; i < partnerList.size(); i++) {
                listStrings.add(partnerList.get(i).getName());
            }

            listBundle.putSerializable("LIST_PARTNERS", listStrings);
            listFragment.setArguments(listBundle);
            fragmentManager.beginTransaction().replace(R.id.port_container, listFragment).commit();
            if (!isPortMode) {
                Bundle mapBundle = new Bundle();
                mapBundle.putSerializable("MAP_PARTNERS", partnerList);
                mapBundle.putString("USER_NAME", userName);
                mapFragment.setArguments(mapBundle);
                fragmentManager.beginTransaction().replace(R.id.land_container, mapFragment).commit();
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();;
            finish();
            return;
        } else {
            nfcAdapter.setNdefPushMessageCallback(this, this);
        }

        locationUpdateListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                HttpPostThread httpPostThread = new HttpPostThread(userName, latitude, longitude);
                httpPostThread.start();
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d("status_changed", "Status Changed");
            }
            @Override
            public void onProviderEnabled(String provider) {
                Log.d("provider", "Provider Enabled");
            }
            @Override
            public void onProviderDisabled(String provider) {
                Log.d("provider", "Provider Disabled");
            }
        };

        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, locationUpdateListener);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, locationUpdateListener);


        /*check if user has previously input a username
         *present dialog to do so if they haven't
         *otherwise Toast the user with welcome message*/
        File file = new File(getFilesDir(), USERNAME_FILE);
        if (file.exists()) {
            if (savedInstanceState == null) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    StringBuilder sb = new StringBuilder();
                    String currLine;
                    while ((currLine = br.readLine()) != null) {
                        sb.append(currLine);
                    }
                    br.close();
                    userName = sb.toString();
                    //send initial location
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationUpdateListener);
                    Toast.makeText(this, getString(R.string.welcome) + " " + userName, Toast.LENGTH_LONG).show();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            displayDialog(file);
            if (!dialogCancelled) {
                //send initial location
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationUpdateListener);
            }
        }

        //start the user retrieval thread
        HttpGetThread thread = new HttpGetThread(updateHandler);
        thread.start();

        //check orientation and create Fragments
        isPortMode = findViewById(R.id.land_container) == null;
        fragmentManager = getSupportFragmentManager();
        mapFragment = new MapFragment();
        listFragment = new ListFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent(), partnerPublicKeyString);
        }

        fbIDService = new MyFirebaseInstanceIDService(userName);
        fbIDService.sendRegistrationToServer(FirebaseInstanceId.getInstance().getToken());

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, new IntentFilter("MESSAGING_EVENT"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent= new Intent(this, KeyService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(serviceConnection);
    }

    public ArrayList<Partner> updatePartnerList(String httpResponse) {
        JSONArray jsonResponse = null;
        ArrayList<Partner> updatedList = new ArrayList<>();
        try {
            jsonResponse = new JSONArray(httpResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < jsonResponse.length(); i++) {
            try {
                JSONObject currPartner = jsonResponse.getJSONObject(i);
                String userName = currPartner.getString("username");
                String latString = currPartner.getString("latitude");
                String longString = currPartner.getString("longitude");
                double latitude = Double.parseDouble(latString);
                double longitude = Double.parseDouble(longString);
                LatLng latLng = new LatLng(latitude, longitude);
                Partner partner = new Partner(userName, latLng);
                updatedList.add(partner);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return updatedList;
    }

    public void sortPartnerList() {
        Collections.sort(partnerList, new Comparator<Partner>() {
            @Override
            public int compare(Partner o1, Partner o2) {
                return o1.compareTo(o2);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, locationUpdateListener);
            }
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent nfcEvent) {
        String messageString = userKeyForExchange;
        NdefRecord ndefRecord = NdefRecord.createMime("text/plain", messageString.getBytes());
        return new NdefMessage(ndefRecord);
    }

    private void processIntent(Intent intent, String partnerName) {
        Parcelable[] rawMessages = intent
                .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMessages[0];
        partnerPublicKeyString = new String(msg.getRecords()[0].getPayload());
        keyService.storePublicKey(partnerName, partnerPublicKeyString);
    }

    /*displays dialog for creation of a username
     *sends current location and username to API*/
    private void displayDialog(final File file) {
        View mView = getLayoutInflater().inflate(R.layout.dialog_add, null);
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
        mBuilder.setView(mView);
        final AlertDialog dialog = mBuilder.create();
        dialog.show();
        final EditText addInput = mView.findViewById(R.id.symbol_add);
        Button cancelButton = mView.findViewById(R.id.cancel_button);
        Button addButton = mView.findViewById(R.id.add_button);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogCancelled = true;
                dialog.dismiss();
            }
        });
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                    userName = addInput.getText().toString();
                    bw.write(userName);
                    bw.close();
                    userName = addInput.getText().toString();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                dialog.dismiss();
            }
        });
    }

    //implement ListFragment.ListSelectListener
    @Override
    public void selectedPartner(String partnerName) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("user_keys", keyService.getMyKeyPair());
        intent.putExtra("partner_key", keyService.getPublicKey(partnerName));
        intent.putExtra("user_name", userName);
        intent.putExtra("partner_name", partnerName);
        startActivity(intent);
    }
}