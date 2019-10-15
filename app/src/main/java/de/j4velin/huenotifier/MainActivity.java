/*
 * Copyright 2017 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.huenotifier;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHBridgeSearchManager;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.hue.sdk.exception.PHHueException;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHHueParsingError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.PermissionChecker;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.j4velin.lib.colorpicker.ColorPickerDialog;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private final PHHueSDK phHueSDK = PHHueSDK.getInstance();
    private final Handler handler = new Handler();
    Map<String, Light> lights;
    List<Rule> rules;
    RecyclerView.Adapter ruleAdapter;
    RecyclerView ruleList;
    boolean isConnected = false;
    private Dialog connectDialog; // for search & pushlink
    private final PHSDKListener listener = new PHSDKListener() {

        @Override
        public void onAccessPointsFound(final List<PHAccessPoint> accessPoint) {
            // Handle your bridge search results here.  Typically if multiple results are returned you will want to display them in a list
            // and let the user select their bridge.   If one is found you may opt to connect automatically to that bridge.
            if (BuildConfig.DEBUG)
                Logger.log("onAccessPointsFound");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    connectDialog.dismiss();
                    if (isConnected) return; // no idea how that can happen, but it seems to do
                    if (accessPoint.isEmpty()) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.no_bridge_found,
                                Snackbar.LENGTH_LONG).show();
                    } else if (accessPoint.size() == 1) {
                        if (BuildConfig.DEBUG)
                            Logger.log("connecting to " + accessPoint.get(0).getIpAddress());
                        phHueSDK.connect(accessPoint.get(0));
                    } else {
                        LinearLayout linearLayout = new LinearLayout(MainActivity.this);
                        linearLayout.setOrientation(LinearLayout.VERTICAL);
                        final Dialog d =
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(R.string.select_bridge).setView(linearLayout)
                                        .create();
                        int padding = Util.dpToPx(MainActivity.this, 10);
                        for (final PHAccessPoint ap : accessPoint) {
                            TextView tv = new TextView(MainActivity.this);
                            tv.setPadding(padding, padding, padding, padding);
                            tv.setText(ap.getMacAddress() + " (" + ap.getIpAddress() + ")");
                            tv.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    phHueSDK.connect(ap);
                                    d.dismiss();
                                }
                            });
                            linearLayout.addView(tv);
                        }
                        d.show();
                    }
                }
            });
        }

        @Override
        public void onCacheUpdated(List<Integer> cacheNotificationsList, PHBridge bridge) {
        }

        @Override
        public void onBridgeConnected(final PHBridge b, final String username) {
            phHueSDK.setSelectedBridge(b);
            // Here it is recommended to set your connected bridge in your sdk object (as above) and start the heartbeat.
            // At this point you are connected to a bridge so you should pass control to your main program/activity.
            // The username is generated randomly by the bridge.
            // Also it is recommended you store the connected IP Address/ Username in your app here.  This will allow easy automatic connection on subsequent use.
            SharedPreferences.Editor edit = getSharedPreferences("HueNotifier", MODE_PRIVATE)
                    .edit();
            edit.putString("bridge_ip",
                    b.getResourceCache().getBridgeConfiguration().getIpAddress());
            edit.putString("username", username);
            edit.apply();
            if (BuildConfig.DEBUG) Logger.log("Connected to: " + b);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (connectDialog != null && connectDialog.isShowing()) {
                        connectDialog.dismiss();
                    }
                    setConnected(true);
                    ((TextView) findViewById(R.id.bridgeinfo))
                            .setText(b.getResourceCache().getBridgeConfiguration().getName()
                                    + " (" + b.getResourceCache().getBridgeConfiguration()
                                    .getIpAddress() + ")");
                }
            });
        }

        @Override
        public void onAuthenticationRequired(PHAccessPoint accessPoint) {
            phHueSDK.startPushlinkAuthentication(accessPoint);
            // Arriving here indicates that Pushlinking is required (to prove the User has physical access to the bridge).  Typically here
            // you will display a pushlink image (with a timer) indicating to to the user they need to push the button on their bridge within 30 seconds.
            handler.post(new Runnable() {
                @Override
                public void run() {
                    connectDialog = new Dialog(MainActivity.this);
                    connectDialog.setCancelable(false);
                    connectDialog.setContentView(R.layout.pushlink);
                    final ProgressBar pg = connectDialog.findViewById(R.id.progressBar);
                    connectDialog.show();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (connectDialog.isShowing()) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (pg.getProgress() < pg.getMax()) {
                                    pg.incrementProgressBy(1);
                                } else {
                                    connectDialog.dismiss();
                                }
                            }
                        }
                    }).start();
                }
            });
        }

        @Override
        public void onConnectionResumed(final PHBridge b) {
            if (BuildConfig.DEBUG) Logger.log("Connection resumed to: " + b);
        }

        @Override
        public void onConnectionLost(PHAccessPoint accessPoint) {
            if (BuildConfig.DEBUG) Logger.log("connection lost");
            // Here you would handle the loss of connection to your bridge.
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Snackbar.make(findViewById(R.id.root), R.string.connection_lost,
                            Snackbar.LENGTH_SHORT)
                            .show();
                    setConnected(false);
                }
            });
        }

        @Override
        public void onError(int code, final String message) {
            // Here you can handle events such as Bridge Not Responding, Authentication Failed and Bridge Not Found
            if (BuildConfig.DEBUG) Logger.log("Error: " + message + " - " + code);
        }

        @Override
        public void onParsingErrors(List<PHHueParsingError> parsingErrorsList) {
            // Any JSON parsing errors are returned here.  Typically your program should never return these.
        }
    };

    static void fadeView(final boolean show, final View v) {
        if (show) {
            v.setAlpha(0);
            v.setVisibility(View.VISIBLE);
        }
        v.animate().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                if (!show) v.setVisibility(View.GONE);
            }
        }).setDuration(500).alpha(show ? 1 : 0);
    }

    private void setConnected(boolean connected) {
        if (BuildConfig.DEBUG)
            Logger.log("setConnected: " + connected);
        isConnected = connected;
        if (!connected) {
            ((TextView) findViewById(R.id.bridgeinfo)).setText(R.string.not_connected);
            fadeView(false, findViewById(R.id.fab));
        } else {
            getLights();
        }
    }

    private void getLights() {
        HueAPI api = APIHelper.getAPI(
                getSharedPreferences("HueNotifier", MODE_PRIVATE));
        api.getLights().enqueue(
                new Callback<Map<String, Light>>() {
                    @Override
                    public void onResponse(Call<Map<String, Light>> call,
                                           Response<Map<String, Light>> response) {
                        lights = response.body();
                        fadeView(true, findViewById(R.id.fab));
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                ruleAdapter.notifyDataSetChanged();
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<Map<String, Light>> call, Throwable t) {
                        if (BuildConfig.DEBUG) Logger.log(t);
                        Snackbar.make(findViewById(android.R.id.content),
                                getString(R.string.could_not_get_lights, t.getMessage()),
                                Snackbar.LENGTH_LONG).show();
                        fadeView(false, findViewById(R.id.fab));
                    }
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 23 && PermissionChecker
                .checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PermissionChecker.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        phHueSDK.setAppName("HueNotifier");
        phHueSDK.setDeviceName(android.os.Build.MODEL);
        phHueSDK.getNotificationManager().registerSDKListener(listener);

        connectToBridge();

        Database db = Database.getInstance(this);
        rules = db.getRules();
        db.close();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addRule();
            }
        });

        ruleList = findViewById(R.id.list);
        ruleList.setHasFixedSize(false);
        ruleList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        ruleAdapter = new RuleAdapter(this);
        ruleList.setAdapter(ruleAdapter);
    }

    void editRule(Rule rule) {
        if (lights == null) {
            getLights();
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.no_light_please_wait,
                    Snackbar.LENGTH_LONG).show();
        } else {
            showEditRuleDialog(rule);
        }
    }

    private void addRule() {
        if (lights == null) {
            getLights();
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.no_light_please_wait,
                    Snackbar.LENGTH_LONG).show();
        } else {
            new AppPicker(MainActivity.this, new AppPicker.AppPickListener() {
                @Override
                public void appSelected(final AppPicker.AppData app) {
                    Rule rule = new Rule(app.name, app.pkg, null, new LightSettings());
                    showEditRuleDialog(rule);
                }
            }).execute();
        }
    }

    private void showEditRuleDialog(final Rule rule) {
        final List<CheckBox> checkBoxes = new ArrayList<CheckBox>(lights.size());

        View v = getLayoutInflater().inflate(R.layout.rule_add, null);
        TextView appName = v.findViewById(R.id.app);
        appName.setText(rule.appName);

        TextView person = v.findViewById(R.id.person);
        Spinner people = v.findViewById(R.id.people);
        if (Build.VERSION.SDK_INT >= 19 && BuildConfig.DEBUG) {
            // TODO: fill spinner
        } else {
            person.setVisibility(View.GONE);
            people.setVisibility(View.GONE);
        }

        LinearLayout linearLayout = v.findViewById(R.id.lights);

        for (Map.Entry<String, Light> entry : lights.entrySet()) {
            Light light = entry.getValue();
            final int[] tag = new int[2];
            tag[0] = Integer.valueOf(entry.getKey());
            tag[1] = rule.getColor(tag[0]);

            final LinearLayout cbLayout = new LinearLayout(linearLayout.getContext());
            final CheckBox cb = new CheckBox(MainActivity.this);
            final TextView tv = new TextView(MainActivity.this);
            tv.setText(light.name);
            tv.setTextColor(tag[1]);
            cb.setTag(tag);
            cb.setChecked(rule.contains(tag[0]));
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton,
                                             boolean isChecked) {
                    if (isChecked) {
                        showColorPickerDialog(cb, tv, tag);
                    } else {
                        tv.setTextColor(tag[1]);
                    }
                }
            });
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!cb.isChecked()) {
                        cb.setChecked(true);
                    } else {
                        showColorPickerDialog(cb, tv, tag);
                    }
                }
            });
            cbLayout.addView(cb);
            cbLayout.addView(tv);
            linearLayout.addView(cbLayout);
            checkBoxes.add(cb);
        }

        v.findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (isConnected) {
                    LightSettings lightSettings = new LightSettings(checkBoxes);
                    startService(new Intent(MainActivity.this, ColorFlashService.class).
                            putExtra("lights", lightSettings.lights)
                            .putExtra("colors", lightSettings.colors)
                            .putExtra("flashOnlyIfLightsOn", false));
                } else {
                    Snackbar.make(findViewById(android.R.id.content), R.string.not_connected,
                            Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        new AlertDialog.Builder(MainActivity.this).setView(v).
                setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                LightSettings lightSettings = new LightSettings(checkBoxes);
                                if (lightSettings.lights.length == 0 || lightSettings.colors.length == 0) {
                                    Toast.makeText(MainActivity.this,
                                            "No lights or colors selected!", Toast.LENGTH_SHORT)
                                            .show();
                                    return;
                                }
                                dialogInterface.dismiss();
                                Database db = Database.getInstance(MainActivity.this);
                                if (db.contains(rule.appPkg)) {
                                    db.delete(rule.appPkg, rule.person);
                                    rules.remove(rule);
                                }
                                if (db.insert(rule.appName, rule.appPkg, null,
                                        lightSettings) >= 0) {
                                    rules.add(db.getRule(rule.appPkg, null));
                                }
                                db.close();
                                ruleAdapter.notifyDataSetChanged();
                            }
                        }).setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                }).create().show();
    }

    private void showColorPickerDialog(final CheckBox cb, final TextView tv, final int[] tag) {
        ColorPickerDialog dialog = new ColorPickerDialog(
                MainActivity.this,
                tag[1]);
        dialog.setOnColorChangedListener(
                new ColorPickerDialog.OnColorChangedListener() {
                    @Override
                    public void onColorChanged(int color) {
                        tv.setTextColor(color);
                        tag[1] = color;
                        cb.setTag(tag);
                        startService(new Intent(MainActivity.this,
                                ColorFlashService.class)
                                .putExtra("lights", new int[]{tag[0]})
                                .putExtra("colors", new int[]{color})
                                .putExtra("flashOnlyIfLightsOn", false));
                    }
                });
        dialog.show();
    }

    private void connectToBridge() {
        SharedPreferences prefs = getSharedPreferences("HueNotifier", MODE_PRIVATE);
        if (!prefs.contains("bridge_ip") || !prefs.contains("username")) {
            // setup
            connectDialog = new ProgressDialog(this);
            connectDialog.setTitle(R.string.searching_bridge);
            connectDialog.show();
            PHBridgeSearchManager sm = (PHBridgeSearchManager) phHueSDK
                    .getSDKService(PHHueSDK.SEARCH_BRIDGE);
            sm.search(true, true);
            if (BuildConfig.DEBUG)
                Logger.log("searching for bridges");
        } else {
            PHAccessPoint accessPoint = new PHAccessPoint();
            accessPoint.setIpAddress(prefs.getString("bridge_ip", null));
            accessPoint.setUsername(prefs.getString("username", null));
            if (BuildConfig.DEBUG)
                Logger.log("connecting to " + accessPoint.getIpAddress() + " as " + accessPoint
                        .getUsername());
            if (!phHueSDK.isAccessPointConnected(accessPoint) || !isConnected) {
                try {
                    phHueSDK.connect(accessPoint);
                } catch (PHHueException e) {
                    if (BuildConfig.DEBUG)
                        Logger.log(e);
                    Snackbar.make(findViewById(R.id.root),
                            "Can't connect to bridge: " + e.getMessage(),
                            Snackbar.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean listenerEnabled =
                getSharedPreferences("NotificationListener", Context.MODE_PRIVATE)
                        .getBoolean("listenerEnabled", false);
        if (!listenerEnabled) {
            Snackbar.make(findViewById(R.id.root), R.string.permission_required,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.grant_access, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            try {
                                startActivity(new Intent(
                                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                            } catch (ActivityNotFoundException anf) {
                                Toast.makeText(MainActivity.this,
                                        "Notification Listener setting not found, please manually search in the Android settings apps",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        phHueSDK.getNotificationManager().unregisterSDKListener(listener);
        phHueSDK.destroySDK();
    }
}
