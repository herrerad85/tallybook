package com.oriondev.moneywallet.ui.fragment.base

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

import com.google.android.material.snackbar.Snackbar
import com.oriondev.moneywallet.R

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class AppBarHost : MultiPanelAppBarFragment() {

    override fun onCreatePrimaryAppBar(inflater: LayoutInflater, primaryAppBar: ViewGroup, savedInstanceState: Bundle?) {
    }

    override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
    }

    override fun onCreateSecondaryPanel(inflater: LayoutInflater, secondaryPanel: ViewGroup, savedInstanceState: Bundle?) {
    }

    override fun getTitleRes(): Int = R.string.app_name
}

object DeletedItemScreen {

    private const val MARKER_TIMEOUT_SECONDS = 5L

    fun addHost(activity: FragmentActivity): AppBarHost {
        val host = AppBarHost()
        activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, host)
                .commitNow()
        return host
    }

    fun addPanel(host: AppBarHost, panel: SecondaryPanelFragment, itemId: Long) {
        host.childFragmentManager.beginTransaction()
                .add(R.id.secondary_panel_container, panel)
                .commitNow()
        host.showSecondaryPanel()
        panel.showItemId(itemId)
        idleMainLooper()
    }

    fun removeHost(activity: FragmentActivity, host: Fragment) {
        activity.supportFragmentManager.beginTransaction()
                .remove(host)
                .commitNow()
    }

    fun executorOf(owner: Class<*>, field: String = "sDeleteExecutor"): Executor =
            owner.getDeclaredField(field).apply { isAccessible = true }.get(null) as Executor

    fun parkWorker(executor: Executor): CountDownLatch {
        val release = CountDownLatch(1)
        executor.execute {
            try {
                release.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return release
    }

    fun awaitWorker(executor: Executor): Boolean {
        val reached = CountDownLatch(1)
        executor.execute { reached.countDown() }
        return try {
            reached.await(MARKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun whileParked(executor: Executor, action: () -> Unit) {
        val release = parkWorker(executor)
        val reached: Boolean
        try {
            action()
        } finally {
            release.countDown()
            reached = awaitWorker(executor)
        }
        assertTrue("the worker did not reach the marker within 5 seconds", reached)
        idleMainLooper()
    }

    fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun idleMainLooperFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    fun snackbarsUnder(view: View): List<View> = when (view) {
        is Snackbar.SnackbarLayout -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { snackbarsUnder(view.getChildAt(it)) }
        else -> emptyList()
    }

    fun snackbarsOf(activity: FragmentActivity): List<View> = snackbarsUnder(activity.window.decorView)

    fun snackbarText(snackbar: View): String =
            snackbar.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).text.toString()

    fun undoOf(snackbar: View): Button {
        val action = snackbar.findViewById<Button>(com.google.android.material.R.id.snackbar_action)
        assertNotNull(action)
        assertEquals(View.VISIBLE, action.visibility)
        return action
    }

    fun shownDialogMessages(): List<String> = ShadowDialog.getShownDialogs()
            .filterIsInstance<AlertDialog>()
            .filter { it.isShowing }
            .map { it.findViewById<TextView>(android.R.id.message)!!.text.toString() }
}
