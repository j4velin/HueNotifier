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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Database extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static Database instance;
    final static String PATTERN_DELIMITER = "@";

    private static final AtomicInteger openCounter = new AtomicInteger();

    private Database(final Context context) {
        super(context, "apps", null, DATABASE_VERSION);
    }

    static synchronized Database getInstance(final Context c) {
        if (instance == null) {
            instance = new Database(c.getApplicationContext());
        }
        openCounter.incrementAndGet();
        return instance;
    }

    @Override
    public void close() {
        if (openCounter.decrementAndGet() == 0) {
            super.close();
        }
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE apps (name TEXT, package TEXT, lights TEXT, colors TEXT);");
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    boolean contains(final String pkg) {
        Cursor c = this.getReadableDatabase()
                .rawQuery("SELECT package FROM apps WHERE package = ?", new String[]{pkg});
        boolean re = c.getCount() > 0;
        c.close();
        return re;
    }

    long insert(final String name, final String pkg, final String lights, final String colors) {
        if (contains(pkg)) {
            return -1;
        }
        final ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("package", pkg);
        values.put("lights", lights);
        values.put("colors", colors);
        return this.getWritableDatabase().insert("apps", null, values);
    }

    String getPattern(final String pkg) {
        Cursor c = this.getReadableDatabase()
                .rawQuery("SELECT lights, colors FROM apps WHERE package = ?", new String[]{pkg});
        String re = null;
        if (c.moveToFirst()) {
            re = c.getString(0) + PATTERN_DELIMITER + c.getString(1);
        }
        c.close();
        return re;
    }

    void delete(final String pkg) {
        getWritableDatabase().delete("apps", "package = ?", new String[]{pkg});
    }

    List<MainActivity.Rule> getRules() {
        Cursor c = this.getReadableDatabase()
                .rawQuery("SELECT name, package, lights, colors FROM apps", null);
        List<MainActivity.Rule> rules = new ArrayList<>(c.getCount());
        if (c.moveToFirst()) {
            do {
                rules.add(new MainActivity.Rule(c.getString(0), c.getString(1), Util.toIntArray(c.getString(2)), Util.toIntArray(c.getString(3))));
            } while (c.moveToNext());
        }
        c.close();
        return rules;
    }

    MainActivity.Rule getRule(final String pkg) {
        Cursor c = this.getReadableDatabase()
                .rawQuery("SELECT name, package, lights, colors FROM apps WHERE package = ?", new String[]{pkg});
        MainActivity.Rule rule = null;
        if (c.moveToFirst()) {
            rule = new MainActivity.Rule(c.getString(0), c.getString(1), Util.toIntArray(c.getString(2)), Util.toIntArray(c.getString(3)));
        }
        c.close();
        return rule;
    }

}
