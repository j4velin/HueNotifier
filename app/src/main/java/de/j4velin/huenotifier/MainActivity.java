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
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHBridgeSearchManager;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHHueParsingError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.j4velin.lib.colorpicker.ColorPickerDialog;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static de.j4velin.huenotifier.R.layout.rule;

public class MainActivity extends AppCompatActivity {

    private final PHHueSDK phHueSDK = PHHueSDK.getInstance();
    private final Handler handler = new Handler();
    private Map<String, Light> lights;
    private List<Rule> rules;
    private RecyclerView.Adapter ruleAdapter;
    private RecyclerView ruleList;
    private boolean isConnected = false;
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
                    final ProgressBar pg = (ProgressBar) connectDialog
                            .findViewById(R.id.progressBar);
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

    private static void fadeView(final boolean show, final View v) {
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

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addRule();
            }
        });

        ruleList = (RecyclerView) findViewById(R.id.list);
        ruleList.setHasFixedSize(false);
        ruleList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        ruleAdapter = new RuleAdapter();
        ruleList.setAdapter(ruleAdapter);
    }

    private void addRule() {
        if (lights == null) {
            getLights();
            Snackbar.make(findViewById(android.R.id.content),
                    "No lights found yet - please wait...",
                    Snackbar.LENGTH_LONG).show();
        } else {
            new AppPicker(MainActivity.this, new AppPicker.AppPickListener() {
                @Override
                public void appSelected(final AppPicker.AppData app) {

                    final List<CheckBox> checkBoxes = new ArrayList<CheckBox>(lights.size());

                    View v = getLayoutInflater().inflate(R.layout.rule_add, null);
                    TextView appName = (TextView) v.findViewById(R.id.app);
                    appName.setText(app.name);

                    TextView person = (TextView) v.findViewById(R.id.person);
                    Spinner people = (Spinner) v.findViewById(R.id.people);
                    if (Build.VERSION.SDK_INT >= 19 && BuildConfig.DEBUG) {
                        // TODO: fill spinner
                    } else {
                        person.setVisibility(View.GONE);
                        people.setVisibility(View.GONE);
                    }

                    LinearLayout linearLayout = (LinearLayout) v.findViewById(R.id.lights);

                    for (Map.Entry<String, Light> entry : lights.entrySet()) {
                        Light light = entry.getValue();
                        final CheckBox cb = new CheckBox(MainActivity.this);
                        cb.setText(light.name);
                        final int[] tag = new int[2];
                        tag[0] = Integer.valueOf(entry.getKey());
                        cb.setTag(tag);
                        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton compoundButton,
                                                         boolean isChecked) {
                                if (isChecked) {
                                    ColorPickerDialog dialog = new ColorPickerDialog(
                                            MainActivity.this,
                                            Color.WHITE);
                                    dialog.setOnColorChangedListener(
                                            new ColorPickerDialog.OnColorChangedListener() {
                                                @Override
                                                public void onColorChanged(int color) {
                                                    cb.setTextColor(color);
                                                    tag[1] = color;
                                                    cb.setTag(tag);
                                                    startService(new Intent(MainActivity.this,
                                                            ColorFlashService.class)
                                                            .putExtra("lights", new int[]{tag[0]})
                                                            .putExtra("colors", new int[]{color}));
                                                }
                                            });
                                    dialog.show();
                                } else {
                                    cb.setTextColor(Color.WHITE);
                                }
                            }
                        });
                        linearLayout.addView(cb);
                        checkBoxes.add(cb);
                    }
                    new AlertDialog.Builder(MainActivity.this).setView(v).
                            setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface,
                                                            int i) {
                                            String lights = null;
                                            String colors = null;
                                            for (CheckBox cb : checkBoxes) {
                                                if (cb.isChecked()) {
                                                    if (lights == null) {
                                                        lights = String
                                                                .valueOf(((int[]) cb.getTag())[0]);
                                                        colors = String
                                                                .valueOf(((int[]) cb.getTag())[1]);
                                                    } else {
                                                        lights += "," + ((int[]) cb.getTag())[0];
                                                        colors += "," + ((int[]) cb.getTag())[1];
                                                    }
                                                }
                                            }
                                            dialogInterface.dismiss();
                                            Database db = Database.getInstance(MainActivity.this);
                                            db.insert(app.name, app.pkg, null, lights, colors);
                                            rules.add(db.getRule(app.pkg, null));
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
            }).execute();
        }
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
            if (!phHueSDK.isAccessPointConnected(accessPoint) || !isConnected) {
                phHueSDK.connect(accessPoint);
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

    static class Rule {
        private final int[] lights, colors;
        private final String appName, appPkg, person;

        Rule(String appName, String appPkg, String person, int[] lights, int[] colors) {
            this.lights = lights;
            this.colors = colors;
            this.appName = appName;
            this.appPkg = appPkg;
            this.person = person;
        }
    }

    private class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.ViewHolder> {

        private final LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        private final PackageManager pm = getPackageManager();
        private final View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View edit = view.findViewById(R.id.edit);
                edit.setMinimumHeight(view.findViewById(R.id.lights).getHeight());
                fadeView(true, edit);
            }
        };
        private final View.OnClickListener testClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View editView = (View) view.getParent();
                if (isConnected) {
                    View cardView = (View) editView.getParent();
                    final int itemPosition = ruleList.getChildLayoutPosition(cardView);
                    startService(new Intent(MainActivity.this, ColorFlashService.class).
                            putExtra("lights", rules.get(itemPosition).lights)
                            .putExtra("colors", rules.get(itemPosition).colors));
                } else {
                    Snackbar.make(findViewById(android.R.id.content), R.string.not_connected,
                            Snackbar.LENGTH_SHORT).show();
                }
                fadeView(false, editView);
            }
        };
        private final View.OnClickListener deleteClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View cardView = (View) view.getParent().getParent();
                final int itemPosition = ruleList.getChildLayoutPosition(cardView);
                Rule r = rules.remove(itemPosition);
                ruleAdapter.notifyItemRemoved(itemPosition);
                Database db = Database.getInstance(MainActivity.this);
                db.delete(r.appPkg, r.person);
                db.close();
            }
        };
        private final View.OnClickListener cancelClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View editView = (View) view.getParent();
                fadeView(false, editView);
            }
        };

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = inflater.inflate(rule, parent, false);
            v.setOnClickListener(clickListener);
            ViewHolder holder = new ViewHolder(v);
            holder.edit.findViewById(R.id.test).setOnClickListener(testClickListener);
            holder.edit.findViewById(R.id.delete).setOnClickListener(deleteClickListener);
            holder.edit.findViewById(R.id.cancel).setOnClickListener(cancelClickListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Rule rule = rules.get(position);
            holder.text.setText(rule.appName);
            holder.edit.setVisibility(View.GONE);
            Drawable appIcon;
            try {
                appIcon = pm.getApplicationIcon(rule.appPkg);
                appIcon.setBounds(0, 0, Util.dpToPx(MainActivity.this, 25),
                        Util.dpToPx(MainActivity.this, 25));
            } catch (PackageManager.NameNotFoundException e) {
                appIcon = null;
            }
            holder.text.setCompoundDrawables(appIcon, null, null, null);
            holder.linearLayout.removeAllViews();
            for (int i = 0; i < rule.lights.length; i++) {
                TextView light = (TextView) inflater
                        .inflate(R.layout.light, holder.linearLayout, false);
                int lightIcon;
                if (lights != null && lights.containsKey(String.valueOf(rule.lights[i]))) {
                    Light lightObject = lights.get(String.valueOf(rule.lights[i]));
                    light.setText(lightObject.name);
                    lightIcon = Util.getLightIcon(lightObject.modelid);
                } else {
                    light.setText("Light #" + rule.lights[i]);
                    lightIcon = R.drawable.ic_light;
                }
                light.setCompoundDrawablesWithIntrinsicBounds(lightIcon, 0, 0, 0);
                if (Build.VERSION.SDK_INT >= 23) {
                    API23Wrapper.setCompoundDrawableTintList(light, rule.colors[i]);
                }
                light.setTextColor(rule.colors[i]);
                holder.linearLayout.addView(light);
            }
        }

        @Override
        public int getItemCount() {
            return rules.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final LinearLayout linearLayout;
            final View edit;

            private ViewHolder(View itemView) {
                super(itemView);
                text = (TextView) itemView.findViewById(R.id.app);
                linearLayout = (LinearLayout) itemView.findViewById(R.id.lights);
                edit = itemView.findViewById(R.id.edit);
            }
        }
    }
}
