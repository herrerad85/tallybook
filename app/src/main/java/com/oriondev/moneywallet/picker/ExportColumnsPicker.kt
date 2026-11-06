package com.oriondev.moneywallet.picker

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.oriondev.moneywallet.ui.fragment.dialog.ExportColumnsDialogFragment

/**
 * Created by andrea on 21/12/18.
 */
class ExportColumnsPicker : Fragment(), ExportColumnsDialogFragment.Callback {

    private var mController: Controller? = null

    private var mCurrentIndices: Array<Int?>? = null

    private var mColumnsPickerDialog: ExportColumnsDialogFragment? = null

    companion object {

        private const val SS_CURRENT_INDICES = "ExportColumnsPicker::SavedState::CurrentIndices"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?): ExportColumnsPicker {
            var exportColumnsPicker = fragmentManager.findFragmentByTag(tag) as ExportColumnsPicker?
            if (exportColumnsPicker == null) {
                exportColumnsPicker = ExportColumnsPicker()
                fragmentManager.beginTransaction().add(exportColumnsPicker, tag).commit()
            }
            return exportColumnsPicker
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mCurrentIndices = savedInstanceState.getSerializable(SS_CURRENT_INDICES) as Array<Int?>?
        } else {
            mCurrentIndices = arrayOfNulls<Int>(0)
        }
        mColumnsPickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as ExportColumnsDialogFragment?
        if (mColumnsPickerDialog == null) {
            mColumnsPickerDialog = ExportColumnsDialogFragment.newInstance()
        }
        mColumnsPickerDialog!!.setCallback(this)
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        val controller = mController
        if (controller != null) {
            val columnNames: Array<String?>? = ExportColumnsDialogFragment.getColumnNameFromIndices(activity, mCurrentIndices)
            controller.onExportColumnsChanged(tag, columnNames)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SS_CURRENT_INDICES, mCurrentIndices)
    }

    fun getCurrentServiceColumns(): Array<String?>? {
        return ExportColumnsDialogFragment.getCursorColumnsFromIndices(mCurrentIndices)
    }

    fun showPicker() {
        mColumnsPickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrentIndices)
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onExportColumnsSelected(indices: Array<Int?>?) {
        mCurrentIndices = indices
        fireCallbackSafely()
    }

    interface Controller {

        fun onExportColumnsChanged(tag: String?, columns: Array<String?>?)
    }
}
