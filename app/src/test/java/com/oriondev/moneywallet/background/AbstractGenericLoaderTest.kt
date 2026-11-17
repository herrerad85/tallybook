package com.oriondev.moneywallet.background;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Looper;

import androidx.loader.content.Loader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.model.OverviewSetting;
import com.oriondev.moneywallet.storage.database.DataContentProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The screens behind these loaders are reached from a drawer entry and left for a child activity,
 * which does not rebuild the fragment, so nothing restarts the loader on the way back. What brings
 * a change home is a write on the watched uri, and until one arrives the result already in hand is
 * the right thing to hand back. Both halves are pinned here, because a loader that hears nothing
 * keeps its first answer for as long as the screen lives, and one that hands back an old answer
 * with a load already running shows a screen coming back the old figures and then the new ones a
 * moment later.
 *
 * What each case reads is what reached a listener, which is what a fragment gets, and not merely
 * that a method was entered. The loading itself is stubbed out, so nothing here covers what a
 * real load does on its way back, only which branch the loader takes.
 */
@RunWith(RobolectricTestRunner.class)
public class AbstractGenericLoaderTest {

    private Context mContext;
    private List<String> mReceived;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mReceived = new ArrayList<>();
    }

    @Test
    public void aFirstStartLoadsAndHandsBackNothing() {
        CountingLoader loader = watching(DataContentProvider.CONTENT_ALL);
        loader.startLoading();
        assertEquals(1, loader.mLoads);
        assertEquals(Collections.emptyList(), mReceived);
    }

    @Test
    public void aStartWithNothingWrittenSinceHandsBackWhatItHasAndDoesNotLoadAgain() {
        CountingLoader loader = watching(DataContentProvider.CONTENT_ALL);
        loader.startLoading();
        loader.deliverResult("first");
        loader.stopLoading();
        loader.startLoading();
        assertEquals(1, loader.mLoads);
        // the one the load itself handed over, then the same one again on the way back
        assertEquals(Arrays.asList("first", "first"), mReceived);
    }

    @Test
    public void aWriteWhileTheScreenIsAwayLoadsAgainAndSkipsTheOldAnswer() {
        CountingLoader loader = watching(DataContentProvider.CONTENT_ALL);
        loader.startLoading();
        loader.deliverResult("first");
        loader.stopLoading();
        loader.onContentChanged();
        loader.startLoading();
        assertEquals(2, loader.mLoads);
        // still only the one the load handed over, since the old answer is on its way out
        assertEquals(Collections.singletonList("first"), mReceived);
    }

    /**
     * The whole point of the change, end to end. No write in this app names the uri the loaders
     * watch, they all name a table or a row under it, so this fails if the registration ever stops
     * asking for descendants or the watcher stops being one that forces a load.
     */
    @Test
    public void aWriteOnTheProviderReachesALoaderWhoseScreenIsAway() {
        CountingLoader loader = watching(DataContentProvider.CONTENT_ALL);
        loader.startLoading();
        loader.deliverResult("first");
        loader.stopLoading();
        mContext.getContentResolver().notifyChange(DataContentProvider.CONTENT_TRANSACTIONS, null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        loader.startLoading();
        assertEquals(2, loader.mLoads);
        assertEquals(Collections.singletonList("first"), mReceived);
    }

    @Test
    public void aLoaderWatchesItsUriWhileItLivesAndLetsItGoOnReset() {
        Uri uri = DataContentProvider.CONTENT_ALL;
        // the app puts a watcher of its own on this uri when it starts, so what this reads is the
        // one the loader adds and not the total
        int before = watchersOf(uri).size();
        CountingLoader loader = watching(uri);
        loader.startLoading();
        assertEquals(before + 1, watchersOf(uri).size());
        loader.stopLoading();
        // a stopped loader keeps watching, which is how a write reaches it while the screen is away
        assertEquals(before + 1, watchersOf(uri).size());
        loader.startLoading();
        assertEquals(before + 1, watchersOf(uri).size());
        loader.reset();
        assertEquals(before, watchersOf(uri).size());
    }

    /**
     * Asking for a fresh loader hands the old one to the new one to reset once it has delivered,
     * and a replacement that never delivers takes its predecessor down with it unreset. These
     * screens ask on every view creation, so that is reachable and the watcher would otherwise
     * outlive the screen.
     */
    @Test
    public void anAbandonedLoaderLetsItsUriGoWithoutWaitingForAReset() {
        Uri uri = DataContentProvider.CONTENT_ALL;
        int before = watchersOf(uri).size();
        CountingLoader loader = watching(uri);
        loader.startLoading();
        loader.deliverResult("first");
        assertEquals(before + 1, watchersOf(uri).size());
        loader.abandon();
        assertEquals(before, watchersOf(uri).size());
    }

    /**
     * The watcher binds a handler to whatever thread builds it, and one instrumented case builds a
     * loader off the main one, so building it with the loader would throw there and nowhere here.
     */
    @Test
    public void aLoaderCanBeBuiltOnAThreadWithNoLooper() throws Exception {
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    new CountingLoader(mContext, DataContentProvider.CONTENT_ALL);
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }
        });
        thread.start();
        thread.join();
        assertNull(thrown.get());
    }

    @Test
    public void aLoaderWithNoUriWatchesNothing() {
        Uri uri = DataContentProvider.CONTENT_ALL;
        int before = watchersOf(uri).size();
        CountingLoader loader = watching(null);
        loader.startLoading();
        assertEquals(before, watchersOf(uri).size());
    }

    @Test
    public void everyLoaderOverTheLedgerWatchesItAndTheOthersDoNot() {
        Date start = new Date(0L);
        Date end = new Date(1L);
        OverviewSetting setting = new OverviewSetting(start, end, Group.MONTHLY,
                OverviewSetting.CashFlow.NET_INCOMES);
        assertEquals(DataContentProvider.CONTENT_ALL,
                new OverviewDataLoader(mContext, setting).getObservedUri());
        assertEquals(DataContentProvider.CONTENT_ALL,
                new PeriodDetailFlowLoader(mContext, start, end, false).getObservedUri());
        assertEquals(DataContentProvider.CONTENT_ALL,
                new PeriodDetailSummaryLoader(mContext, start, end,
                        PeriodDetailSummaryLoader.GROUP_BY_MONTH).getObservedUri());
        assertNull(new LicenseLoader(mContext).getObservedUri());
        assertNull(new ChangeLogLoader(mContext).getObservedUri());
        assertNull(new IconGroupLoader(mContext).getObservedUri());
        assertNull(new JsonResourceLoader(mContext, "anything").getObservedUri());
    }

    private CountingLoader watching(Uri uri) {
        CountingLoader loader = new CountingLoader(mContext, uri);
        loader.registerListener(1, new Loader.OnLoadCompleteListener<String>() {

            @Override
            public void onLoadComplete(Loader<String> source, String data) {
                mReceived.add(data);
            }
        });
        return loader;
    }

    private Collection<ContentObserver> watchersOf(Uri uri) {
        return Shadows.shadowOf(mContext.getContentResolver()).getContentObservers(uri);
    }

    /**
     * Counts the loads without running any, so what is read is the branching and not the loading.
     */
    private static class CountingLoader extends AbstractGenericLoader<String> {

        private final Uri mObserved;

        private int mLoads;

        private CountingLoader(Context context, Uri observed) {
            super(context);
            mObserved = observed;
        }

        @Override
        protected Uri getObservedUri() {
            return mObserved;
        }

        @Override
        public String loadInBackground() {
            return "loaded";
        }

        @Override
        public void forceLoad() {
            mLoads++;
        }
    }
}
