package com.oriondev.moneywallet.background

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Looper

import androidx.loader.content.Loader
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.storage.database.DataContentProvider

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.util.ReflectionHelpers

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

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
@RunWith(RobolectricTestRunner::class)
class AbstractGenericLoaderTest {

    private lateinit var mContext: Context
    private lateinit var mReceived: MutableList<String?>

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        mReceived = ArrayList()
    }

    @Test
    fun aFirstStartLoadsAndHandsBackNothing() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        assertEquals(1, loader.mLoads)
        assertEquals(Collections.emptyList<String>(), mReceived)
    }

    @Test
    fun aStartWithNothingWrittenSinceHandsBackWhatItHasAndDoesNotLoadAgain() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        loader.deliverResult("first")
        loader.stopLoading()
        loader.startLoading()
        assertEquals(1, loader.mLoads)
        // the one the load itself handed over, then the same one again on the way back
        assertEquals(Arrays.asList("first", "first"), mReceived)
    }

    @Test
    fun aWriteWhileTheScreenIsAwayLoadsAgainAndSkipsTheOldAnswer() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        loader.deliverResult("first")
        loader.stopLoading()
        loader.onContentChanged()
        loader.startLoading()
        assertEquals(2, loader.mLoads)
        // still only the one the load handed over, since the old answer is on its way out
        assertEquals(Collections.singletonList("first"), mReceived)
    }

    /**
     * The whole point of the change, end to end. No write in this app names the uri the loaders
     * watch, they all name a table or a row under it, so this fails if the registration ever stops
     * asking for descendants or the watcher stops being one that forces a load.
     */
    @Test
    fun aWriteOnTheProviderReachesALoaderWhoseScreenIsAway() {
        val loader = watching(DataContentProvider.CONTENT_ALL)
        loader.startLoading()
        loader.deliverResult("first")
        loader.stopLoading()
        mContext.contentResolver.notifyChange(DataContentProvider.CONTENT_TRANSACTIONS, null)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        loader.startLoading()
        assertEquals(2, loader.mLoads)
        assertEquals(Collections.singletonList("first"), mReceived)
    }

    @Test
    fun aLoaderWatchesItsUriWhileItLivesAndLetsItGoOnReset() {
        val uri = DataContentProvider.CONTENT_ALL
        // the app puts a watcher of its own on this uri when it starts, so what this reads is the
        // one the loader adds and not the total
        val before = watchersOf(uri).size
        val loader = watching(uri)
        loader.startLoading()
        assertEquals(before + 1, watchersOf(uri).size)
        loader.stopLoading()
        // a stopped loader keeps watching, which is how a write reaches it while the screen is away
        assertEquals(before + 1, watchersOf(uri).size)
        loader.startLoading()
        assertEquals(before + 1, watchersOf(uri).size)
        loader.reset()
        assertEquals(before, watchersOf(uri).size)
    }

    /**
     * Asking for a fresh loader hands the old one to the new one to reset once it has delivered,
     * and a replacement that never delivers takes its predecessor down with it unreset. These
     * screens ask on every view creation, so that is reachable and the watcher would otherwise
     * outlive the screen.
     */
    @Test
    fun anAbandonedLoaderLetsItsUriGoWithoutWaitingForAReset() {
        val uri = DataContentProvider.CONTENT_ALL
        val before = watchersOf(uri).size
        val loader = watching(uri)
        loader.startLoading()
        loader.deliverResult("first")
        assertEquals(before + 1, watchersOf(uri).size)
        loader.abandon()
        assertEquals(before, watchersOf(uri).size)
    }

    /**
     * The watcher binds a handler to whatever thread builds it, and one instrumented case builds a
     * loader off the main one, so building it with the loader would throw there and nowhere here.
     */
    @Test
    @Throws(Exception::class)
    fun aLoaderCanBeBuiltOnAThreadWithNoLooper() {
        val thrown = AtomicReference<Throwable>()
        val thread = Thread(object : Runnable {

            override fun run() {
                try {
                    CountingLoader(mContext, DataContentProvider.CONTENT_ALL)
                } catch (t: Throwable) {
                    thrown.set(t)
                }
            }
        })
        thread.start()
        thread.join()
        assertNull(thrown.get())
    }

    @Test
    fun aLoaderWithNoUriWatchesNothing() {
        val uri = DataContentProvider.CONTENT_ALL
        val before = watchersOf(uri).size
        val loader = watching(null)
        loader.startLoading()
        assertEquals(before, watchersOf(uri).size)
    }

    @Test
    fun everyLoaderOverTheLedgerWatchesItAndTheOthersDoNot() {
        val start = Date(0L)
        val end = Date(1L)
        val setting = OverviewSetting(start, end, Group.MONTHLY,
                OverviewSetting.CashFlow.NET_INCOMES)
        assertEquals(DataContentProvider.CONTENT_ALL,
                ReflectionHelpers.callInstanceMethod<Uri>(OverviewDataLoader(mContext, setting), "getObservedUri"))
        assertEquals(DataContentProvider.CONTENT_ALL,
                ReflectionHelpers.callInstanceMethod<Uri>(PeriodDetailFlowLoader(mContext, start, end, false), "getObservedUri"))
        assertEquals(DataContentProvider.CONTENT_ALL,
                ReflectionHelpers.callInstanceMethod<Uri>(PeriodDetailSummaryLoader(mContext, start, end,
                        PeriodDetailSummaryLoader.GROUP_BY_MONTH), "getObservedUri"))
        assertNull(ReflectionHelpers.callInstanceMethod<Uri>(LicenseLoader(mContext), "getObservedUri"))
        assertNull(ReflectionHelpers.callInstanceMethod<Uri>(ChangeLogLoader(mContext), "getObservedUri"))
        assertNull(ReflectionHelpers.callInstanceMethod<Uri>(IconGroupLoader(mContext), "getObservedUri"))
        assertNull(ReflectionHelpers.callInstanceMethod<Uri>(JsonResourceLoader(mContext, "anything"), "getObservedUri"))
    }

    private fun watching(uri: Uri?): CountingLoader {
        val loader = CountingLoader(mContext, uri)
        loader.registerListener(1, object : Loader.OnLoadCompleteListener<String> {

            override fun onLoadComplete(source: Loader<String>, data: String?) {
                mReceived.add(data)
            }
        })
        return loader
    }

    private fun watchersOf(uri: Uri): Collection<ContentObserver> {
        return Shadows.shadowOf(mContext.contentResolver).getContentObservers(uri)
    }

    /**
     * Counts the loads without running any, so what is read is the branching and not the loading.
     */
    private class CountingLoader : AbstractGenericLoader<String> {

        private val mObserved: Uri?

        var mLoads = 0

        constructor(context: Context, observed: Uri?) : super(context) {
            mObserved = observed
        }

        override fun getObservedUri(): Uri? {
            return mObserved
        }

        override fun loadInBackground(): String {
            return "loaded"
        }

        override fun forceLoad() {
            mLoads++
        }
    }
}
