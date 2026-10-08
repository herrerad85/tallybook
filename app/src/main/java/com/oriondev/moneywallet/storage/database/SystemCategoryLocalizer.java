/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.storage.database;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.util.Log;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.utils.IconLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The nine system categories are seeded once, at database create, with their names resolved
 * against whatever locale the app happened to be in. Almost everything that shows a category
 * reads that stored column, so those names keep the seeding language forever while the rest of
 * the app follows the current one. The exceptions are the recurrent transfer and transfer model
 * rows, which use the string resource directly and were always correct.
 *
 * This rewrites the stored name, and the icon glyph derived from it, on the rows it still
 * recognises as its own.
 */
public class SystemCategoryLocalizer {

    private static final String TAG = "SystemCategoryLocalizer";

    /**
     * Never throws. Both callers are Application, on the main thread, so anything escaping would
     * take the process down and would do it again on the next launch.
     *
     * Costs one query, and on any run where a stored name does not match, about 47ms to build the
     * name table plus an update per row rewritten.
     */
    public static void relocalize(Context context) {
        try {
            relocalizeSystemCategories(context);
        } catch (Throwable t) {
            Log.e(TAG, "unable to relocalize the system categories", t);
        }
    }

    /**
     * A row is only rewritten when its stored name is still one this app would itself have seeded,
     * in any language it ships. Anything else is a name the user chose, and is left alone.
     * Recognising every shipped language rather than only the previously used one is what repairs
     * an install whose seeding language was never recorded, which is every install predating this
     * code.
     *
     * Two cases are indistinguishable, and they fall opposite ways.
     *
     * A user who renames a category to exactly the string some shipped translation already uses for
     * that same category, or to a name retiredNames lists for it, IS overwritten, because that name
     * is in the table and nothing separates it from a stale one. Renaming Debt to Deuda on an
     * English install reverts on the next launch. This is the one way the class can destroy a
     * rename and it is accepted, since the alternative is storing a flag for a case nobody has
     * reported.
     *
     * A row seeded by an older release whose translation has since been edited is NOT repaired,
     * because the table is built from the current build's strings and no longer contains the one
     * it carries, unless that old name is listed in retiredNames. Otherwise it stays in the old
     * language for good.
     */
    private static void relocalizeSystemCategories(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Map<String, SystemCategory> byTag = new HashMap<>();
        for (SystemCategory category : SystemCategory.mSystemCategories) {
            byTag.put(category.getTag(), category);
        }
        Map<String, Set<String>> seededNames = null;
        for (StoredCategory stored : querySystemCategories(contentResolver)) {
            try {
                SystemCategory category = byTag.get(stored.tag);
                if (category == null) {
                    continue;
                }
                String currentName = category.getName(context);
                if (currentName.equals(stored.name)) {
                    continue;
                }
                if (seededNames == null) {
                    seededNames = seededNamesOrEmpty(context);
                }
                Set<String> names = seededNames.get(stored.tag);
                if (names != null && names.contains(stored.name)) {
                    rewrite(contentResolver, stored, currentName);
                }
            } catch (Throwable t) {
                // per row, so that one unwritable row does not leave the others stale
                Log.e(TAG, "unable to relocalize the system category " + stored.tag, t);
            }
        }
    }

    /**
     * Latches an empty table on failure. Without that the next mismatching row would try again,
     * so one broken build would be paid once per row and repaired nothing either way.
     */
    private static Map<String, Set<String>> seededNamesOrEmpty(Context context) {
        try {
            return seededNames(context);
        } catch (Throwable t) {
            Log.e(TAG, "unable to build the seeded name table", t);
            return Collections.emptyMap();
        }
    }

    /**
     * @return every name this build would seed, plus the retired ones, per category tag.
     */
    private static Map<String, Set<String>> seededNames(Context context) {
        List<Context> languages = shippedLanguages(context);
        Map<String, Set<String>> retiredNames = retiredNames();
        Map<String, Set<String>> seededNames = new HashMap<>();
        for (SystemCategory category : SystemCategory.mSystemCategories) {
            Set<String> names = new HashSet<>();
            Set<String> retired = retiredNames.get(category.getTag());
            if (retired != null) {
                names.addAll(retired);
            }
            for (Context language : languages) {
                names.add(category.getName(language));
            }
            seededNames.put(category.getTag(), names);
        }
        return seededNames;
    }

    /**
     * Names an older release seeded that no current translation uses, so the rows carrying them
     * are still recognized. Add a name here when a translation of a system category changes.
     */
    private static Map<String, Set<String>> retiredNames() {
        Map<String, Set<String>> retiredNames = new HashMap<>();
        // ru and es through 1.13.0
        retiredNames.put(Schema.CategoryTag.TRANSFER_TAX, new HashSet<>(Arrays.asList("Налог на перевод", "Impuestos de la transferencia")));
        retiredNames.put(Schema.CategoryTag.DEBT, Collections.singleton("Долг"));
        retiredNames.put(Schema.CategoryTag.CREDIT, Collections.singleton("Кредит"));
        retiredNames.put(Schema.CategoryTag.PAID_CREDIT, Collections.singleton("Оплата кредита"));
        retiredNames.put(Schema.CategoryTag.TAX, Collections.singleton("Налог на операцию"));
        return retiredNames;
    }

    /**
     * The locale config lists this app's own twenty entries. getAssets().getLocales() lists over
     * a hundred, because it counts every language the bundled libraries were translated into, and
     * those resolve to the default and contribute nothing but cost.
     *
     * Reading fewer languages than intended only ever means fewer rows are recognised as seeded,
     * never that a user name is overwritten, so a partial read is safe to continue from.
     *
     * @return one context per config entry, plus the untranslated default. The config happens to
     *          list en-US and there is no values-en, so today that entry already covers the
     *          default and ROOT is insurance rather than the only source of it.
     */
    private static List<Context> shippedLanguages(Context context) {
        List<Context> contexts = new ArrayList<>();
        XmlResourceParser parser = null;
        try {
            contexts.add(localizedContext(context, Locale.ROOT));
            parser = context.getResources().getXml(R.xml._generated_res_locale_config);
            for (int event = parser.next(); event != XmlResourceParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlResourceParser.START_TAG && "locale".equals(parser.getName())) {
                    String tag = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                    if (tag != null) {
                        contexts.add(localizedContext(context, Locale.forLanguageTag(tag)));
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "stopped reading languages after " + contexts.size() + " contexts", t);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
        return contexts;
    }

    private static Context localizedContext(Context context, Locale locale) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    /**
     * One query rather than one per tag, because the view behind it is a join.
     */
    private static List<StoredCategory> querySystemCategories(ContentResolver contentResolver) {
        List<StoredCategory> categories = new ArrayList<>();
        String[] projection = new String[] {Contract.Category.ID, Contract.Category.NAME, Contract.Category.ICON, Contract.Category.TAG};
        String selection = Contract.Category.TYPE + " = ?";
        String[] selectionArgs = new String[] {String.valueOf(Contract.CategoryType.SYSTEM.getValue())};
        Cursor cursor = contentResolver.query(DataContentProvider.CONTENT_CATEGORIES, projection, selection, selectionArgs, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    categories.add(new StoredCategory(
                            cursor.getLong(cursor.getColumnIndex(Contract.Category.ID)),
                            cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)),
                            cursor.getString(cursor.getColumnIndex(Contract.Category.ICON)),
                            cursor.getString(cursor.getColumnIndex(Contract.Category.TAG))
                    ));
                }
            } finally {
                cursor.close();
            }
        }
        return categories;
    }

    private static void rewrite(ContentResolver contentResolver, StoredCategory stored, String currentName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, currentName);
        String icon = relocalizedIcon(stored.icon, currentName);
        if (icon != null) {
            contentValues.put(Contract.Category.ICON, icon);
        }
        contentResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, stored.id), contentValues, null, null);
    }

    /**
     * The glyph is derived from the name, so it goes stale with it. The colour is random per
     * seeding process and is not recoverable, so it is carried over rather than regenerated.
     *
     * IconLoader returns null on malformed json but throws on an icon type it does not recognise,
     * hence the catch.
     *
     * @return the updated icon, or null to leave the stored one alone, which happens for a vector
     *          icon and for anything unreadable. The name is corrected either way.
     */
    private static String relocalizedIcon(String storedIcon, String currentName) {
        try {
            Icon icon = IconLoader.parse(storedIcon);
            if (!(icon instanceof ColorIcon)) {
                return null;
            }
            return new ColorIcon((ColorIcon) icon, IconLoader.getColorIconString(currentName)).toString();
        } catch (Exception e) {
            Log.e(TAG, "unreadable icon on a system category, keeping the old one", e);
            return null;
        }
    }

    private static class StoredCategory {

        private final long id;
        private final String name;
        private final String icon;
        private final String tag;

        private StoredCategory(long id, String name, String icon, String tag) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.tag = tag;
        }
    }
}
