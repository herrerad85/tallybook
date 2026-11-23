package com.oriondev.moneywallet.ui.adapter.recycler;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TransactionBackgroundTest {

    private static RecyclerView.ViewHolder transactionHolder() {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(new RecordsNothing(), true);
        FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        return adapter.onCreateViewHolder(parent, TransactionHeaderCursor.TYPE_ITEM);
    }

    @Test
    public void theBackgroundOfARowLaysTheSelectionUnderTheRipple() {
        Drawable background = transactionHolder().itemView.getBackground();
        assertSame(LayerDrawable.class, background.getClass());
        LayerDrawable layers = (LayerDrawable) background;
        assertEquals(2, layers.getNumberOfLayers());
        assertTrue(layers.getDrawable(0) instanceof StateListDrawable);
        assertTrue(layers.getDrawable(1) instanceof RippleDrawable);
    }

    @Test
    public void theRippleOfARowRedrawsThroughTheLayers() {
        RecyclerView.ViewHolder holder = transactionHolder();
        LayerDrawable layers = (LayerDrawable) holder.itemView.getBackground();
        Drawable ripple = layers.getDrawable(1);
        assertNotNull(ripple.getCallback());
        assertSame(layers, ripple.getCallback());
        assertSame(holder.itemView, layers.getCallback());
    }

    @Test
    public void anActivatedRowShowsTheRippleColorUnderTheRipple() {
        RecyclerView.ViewHolder holder = transactionHolder();
        Drawable selected = ((LayerDrawable) holder.itemView.getBackground()).getDrawable(0);
        holder.itemView.setActivated(true);
        Drawable current = selected.getCurrent();
        assertTrue(current instanceof ColorDrawable);
        assertEquals(ThemeEngine.getTheme().getColorRipple(), ((ColorDrawable) current).getColor());
        holder.itemView.setActivated(false);
        assertNull(selected.getCurrent());
    }

    private static class RecordsNothing implements TransactionCursorAdapter.ActionListener {

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
        }

        @Override
        public void onTransactionClick(long id) {
        }

        @Override
        public void onSelectionChanged(int count) {
        }
    }
}
