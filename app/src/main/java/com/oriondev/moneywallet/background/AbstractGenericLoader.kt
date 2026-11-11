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

package com.oriondev.moneywallet.background;

import android.content.Context;
import android.net.Uri;
import androidx.loader.content.AsyncTaskLoader;

/**
 * Created by andrea on 06/04/18.
 */
public abstract class AbstractGenericLoader<T> extends AsyncTaskLoader<T> {

    private ForceLoadContentObserver mObserver;
    private T mGenericData;

    public AbstractGenericLoader(Context context) {
        super(context);
    }

    @Override
    public abstract T loadInBackground();

    /**
     * The uri to watch for writes, or null for a loader whose data cannot change while the app
     * runs. Without one nothing ever marks the content changed, so the reload below can never
     * happen and the screen keeps whatever it read the first time.
     */
    protected Uri getObservedUri() {
        return null;
    }

    /* Runs on the UI thread */
    @Override
    public void deliverResult(T genericData) {
        mGenericData = genericData;
        if (isStarted()) {
            super.deliverResult(genericData);
        }
    }

    /**
     * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately.
     * <p/>
     * Must be called from the UI thread
     */
    @Override
    protected void onStartLoading() {
        Uri observed = getObservedUri();
        if (observed != null && mObserver == null) {
            // built here and not in a field, because the observer binds a Handler to the thread
            // that builds it and a loader is only ever started on the main one. The loaders that
            // watch nothing then build nothing.
            mObserver = new ForceLoadContentObserver();
            // descendants, because no write names the uri above and an observer that did not ask
            // for them would never hear one
            getContext().getContentResolver().registerContentObserver(observed, true, mObserver);
        }
        // takeContentChanged clears the flag, so it is read once. The cached result goes out only
        // when nothing is about to replace it, otherwise a screen coming back to a write that
        // landed while it was away shows the old figures and then the new ones a moment later. A
        // write that lands while the screen is showing forces a load through the observer instead,
        // and the two orders race, so that case can still show both.
        boolean reload = takeContentChanged() || mGenericData == null;
        if (mGenericData != null && !reload) {
            deliverResult(mGenericData);
        }
        if (reload) {
            forceLoad();
        }
    }

    /**
     * Must be called from the UI thread
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * These screens ask for a fresh loader every time their view is created, and that hands the
     * old one to the new one to reset once it has delivered. A replacement that never delivers,
     * because the screen was left or built again first, takes its predecessor down with it and
     * nothing resets it, so an unregister that lived in onReset alone would leave a watcher
     * behind for the life of the process. An abandoned loader's result is thrown away, so it has
     * no use for one either way.
     */
    @Override
    protected void onAbandon() {
        super.onAbandon();
        stopWatching();
    }

    @Override
    protected void onReset() {
        super.onReset();
        // Ensure the loader is stopped
        onStopLoading();
        stopWatching();
        mGenericData = null;
    }

    private void stopWatching() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
    }
}