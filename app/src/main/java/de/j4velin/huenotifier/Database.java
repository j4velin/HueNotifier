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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Database extends SQLiteOpenHelper {

    final static String PATTERN_DELIMITER = "@";
    private static final int DATABASE_VERSION = 2;
    private static final AtomicInteger openCounter = new AtomicInteger();
    private static Database instance;

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
        db.execSQL(
                "CREATE TABLE apps (name TEXT, package TEXT, person TEXT, lights TEXT, colors TEXT);");
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            db.execSQL("ALTER TABLE apps ADD COLUMN person TEXT;");
        }
    }

    boolean contains(final String pkg) {
        Cursor c = this.getReadableDatabase()
                .rawQuery("SELECT package FROM apps WHERE package = ?", new String[]{pkg});
        boolean re = c.getCount() > 0;
        c.close();
        return re;
    }

    long insert(final String name, final String pkg, final String person, final String lights,
                final String colors) {
        if (contains(pkg)) {
            return -1;
        }
        final ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("package", pkg);
        values.put("person", person);
        values.put("lights", lights);
        values.put("colors", colors);
        return this.getWritableDatabase().insert("apps", null, values);
    }

    String getPattern(final String pkg, final Collection<String> people) {
        Cursor c = this.getReadableDatabase()
                .rawQuery(
                        "SELECT person, lights, colors FROM apps WHERE package = ?",
                        new String[]{pkg});
        String re = null;
        if (c.moveToFirst()) {
            for (int i = 0; i < c.getCount(); i++) {
                String person = c.getString(0);
                if (person == null && re == null) {
                    re = c.getString(1) + PATTERN_DELIMITER + c.getString(2);
                } else if (people.contains(person)) {
                    re = c.getString(1) + PATTERN_DELIMITER + c.getString(2);
                    break;
                }
                c.moveToNext();
            }
        }
        c.close();
        return re;
    }

    void delete(final String pkg, final String person) {
        getWritableDatabase().delete("apps", "package = ? AND person = ?",
                new String[]{pkg, person});
    }

    List<MainActivity.Rule> getRules() {
        Cursor c = this.getReadableDatabase()
                .rawQuery("SELECT name, package, person, lights, colors FROM apps", null);
        List<MainActivity.Rule> rules = new ArrayList<>(c.getCount());
        if (c.moveToFirst()) {
            do {
                rules.add(new MainActivity.Rule(c.getString(0), c.getString(1), c.getString(2),
                        Util.toIntArray(c.getString(3)), Util.toIntArray(c.getString(4))));
            } while (c.moveToNext());
        }
        c.close();
        return rules;
    }

    MainActivity.Rule getRule(final String pkg, final String person) {
        Cursor c = this.getReadableDatabase()
                .rawQuery(
                        "SELECT name, package, person, lights, colors FROM apps WHERE package = ? AND person = ?",
                        new String[]{pkg, person});
        MainActivity.Rule rule = null;
        if (c.moveToFirst()) {
            rule = new MainActivity.Rule(c.getString(0), pkg, person,
                    Util.toIntArray(c.getString(3)), Util.toIntArray(c.getString(4)));
        }
        c.close();
        return rule;
    }

}
