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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class AppPicker extends AsyncTask<Void, Integer, Void> {

    private final Context a;
    private final AppPickListener listener;
    private ProgressDialog progress;
    private List<AppData> apps;
    private Dialog dialog;

    AppPicker(final Context c, final AppPickListener l) {
        if (c == null || l == null)
            throw new IllegalArgumentException("Arguments must not be null");
        a = c;
        listener = l;
    }

    @Override
    protected void onPreExecute() {
        progress = ProgressDialog.show(a, "", a.getString(R.string.loading_apps), true);
        progress.setMax(1);
        progress.setCancelable(false);
    }

    protected void onPostExecute(Void blub) {
        try {
            progress.dismiss();
        } catch (Exception e) { // activity already closed?

        }
        if (apps == null) {
            Toast.makeText(a, "Error loading apps", Toast.LENGTH_SHORT).show();
            return;
        }
        dialog = new Dialog(a);
        RecyclerView mRecyclerView = new RecyclerView(a);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView
                .setLayoutManager(new LinearLayoutManager(a, LinearLayoutManager.VERTICAL, false));
        RecyclerView.Adapter mAdapter = new AppAdapter();
        mRecyclerView.setAdapter(mAdapter);
        dialog.setContentView(mRecyclerView);
        dialog.show();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (progress != null && progress.isShowing()) {
            try {
                progress.dismiss();
            } catch (Exception e) { // activity already closed?
            }
        }
        Toast.makeText(a, "Not enough available memory to load all apps", Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    protected Void doInBackground(Void... params) {
        final PackageManager pm = a.getPackageManager();
        List<PackageInfo> apps = pm.getInstalledPackages(0);

        Database db = Database.getInstance(a);
        Iterator<PackageInfo> it = apps.iterator();
        PackageInfo app;
        while (it.hasNext()) {
            app = it.next();
            if (db.contains(app.packageName)) it.remove();
        }

        try {
            this.apps = new ArrayList<>(apps.size());
            Drawable d;
            try {
                for (int i = 0; i < apps.size(); i++) {
                    PackageInfo pi = apps.get(i);
                    d = apps.get(i).applicationInfo.loadIcon(pm);
                    d.setBounds(0, 0, Util.dpToPx(a, 25), Util.dpToPx(a, 25));
                    this.apps.add(new AppData(pi.applicationInfo.loadLabel(pm).toString(),
                            pi.packageName, d));
                }
            } catch (OutOfMemoryError oom) {
            }
            Collections.sort(this.apps);

        } catch (OutOfMemoryError oom) {
            publishProgress(1);
        }
        db.close();
        return null;
    }

    interface AppPickListener {
        void appSelected(AppData app);
    }

    public class AppData implements Comparable<AppData> {
        final String name, pkg;
        public final Drawable icon;

        private AppData(String name, String pkg, Drawable icon) {
            this.name = name;
            this.pkg = pkg;
            this.icon = icon;
        }

        @Override
        public int compareTo(AppData appData) {
            return name.toLowerCase().compareTo(appData.name.toLowerCase());
        }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final AppData app = apps.get(position);
            holder.text.setText(app.name);
            holder.text.setCompoundDrawables(app.icon, null, null, null);
            holder.text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.appSelected(app);
                    dialog.dismiss();
                }
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView text;

            ViewHolder(final View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
    }
}
