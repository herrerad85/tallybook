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

package com.oriondev.moneywallet.picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.activity.CategoryPickerActivity
import com.oriondev.moneywallet.ui.fragment.dialog.MultiCategoryPickerDialog
import com.oriondev.moneywallet.ui.fragment.dialog.ParentCategoryPickerDialog

/**
 * Created by andrea on 02/02/18.
 */
class CategoryPicker : Fragment(), ParentCategoryPickerDialog.Callback, MultiCategoryPickerDialog.Callback {

    companion object {

        private const val SS_CURRENT_CATEGORY = "ParentCategoryPicker::SavedState::CurrentCategory"
        private const val SS_CURRENT_CATEGORIES = "ParentCategoryPicker::SavedState::CurrentCategories"
        private const val SS_MULTI_CATEGORY = "ParentCategoryPicker::SavedState::MultiCategory"
        private const val ARG_DEFAULT_CATEGORY = "ParentCategoryPicker::Arguments::DefaultCategory"
        private const val ARG_DEFAULT_CATEGORIES = "ParentCategoryPicker::Arguments::DefaultCategories"
        private const val ARG_MULTI_CATEGORY = "ParentCategoryPicker::Arguments::MultiCategory"

        private const val REQUEST_CATEGORY_PICKER = 1

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultCategory: Category?): CategoryPicker {
            var categoryPicker = fragmentManager.findFragmentByTag(tag) as CategoryPicker?
            if (categoryPicker == null) {
                categoryPicker = CategoryPicker()
                val arguments = Bundle()
                arguments.putBoolean(ARG_MULTI_CATEGORY, false)
                arguments.putParcelable(ARG_DEFAULT_CATEGORY, defaultCategory)
                categoryPicker.arguments = arguments
                fragmentManager.beginTransaction().add(categoryPicker, tag).commit()
            }
            return categoryPicker
        }

        /**
         * A picker over any number of categories at once, for a budget that covers more than one.
         * It reports through [MultiCategoryController] and leaves
         * [getCurrentCategory] empty; the single category picker above is untouched and
         * still reports through [Controller].
         *
         * @param fragmentManager manager the picker is added to.
         * @param tag name it is found again by.
         * @param defaultCategories categories chosen already, empty or null on a new item.
         * @return the picker, whether it was created here or found by its tag.
         */
        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, defaultCategories: Array<Category?>?): CategoryPicker {
            var categoryPicker = fragmentManager.findFragmentByTag(tag) as CategoryPicker?
            if (categoryPicker == null) {
                categoryPicker = CategoryPicker()
                val arguments = Bundle()
                arguments.putBoolean(ARG_MULTI_CATEGORY, true)
                arguments.putParcelableArray(ARG_DEFAULT_CATEGORIES, defaultCategories)
                categoryPicker.arguments = arguments
                fragmentManager.beginTransaction().add(categoryPicker, tag).commit()
            }
            return categoryPicker
        }
    }

    private var mController: Controller? = null
    private var mMultiCategoryController: MultiCategoryController? = null

    private var mMultiCategory: Boolean = false
    private var mCurrentCategory: Category? = null
    private var mCurrentCategories: Array<Category?>? = null

    private var mParentCategoryPickerDialog: ParentCategoryPickerDialog? = null
    private var mMultiCategoryPickerDialog: MultiCategoryPickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        } else if (parentFragment is Controller) {
            mController = parentFragment as Controller
        }
        if (context is MultiCategoryController) {
            mMultiCategoryController = context
        } else if (parentFragment is MultiCategoryController) {
            mMultiCategoryController = parentFragment as MultiCategoryController
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mMultiCategory = savedInstanceState.getBoolean(SS_MULTI_CATEGORY)
            mCurrentCategory = savedInstanceState.getParcelable(SS_CURRENT_CATEGORY)
            mCurrentCategories = toCategories(savedInstanceState.getParcelableArray(SS_CURRENT_CATEGORIES))
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mMultiCategory = arguments.getBoolean(ARG_MULTI_CATEGORY)
                mCurrentCategory = arguments.getParcelable(ARG_DEFAULT_CATEGORY)
                mCurrentCategories = toCategories(arguments.getParcelableArray(ARG_DEFAULT_CATEGORIES))
            } else {
                mCurrentCategory = null
                mCurrentCategories = null
            }
        }
        mParentCategoryPickerDialog = childFragmentManager.findFragmentByTag(getDialogTag()) as ParentCategoryPickerDialog?
        if (mParentCategoryPickerDialog == null) {
            mParentCategoryPickerDialog = ParentCategoryPickerDialog.newInstance()
        }
        mParentCategoryPickerDialog!!.setCallback(this)
        mMultiCategoryPickerDialog = childFragmentManager.findFragmentByTag(getMultiDialogTag()) as MultiCategoryPickerDialog?
        if (mMultiCategoryPickerDialog == null) {
            mMultiCategoryPickerDialog = MultiCategoryPickerDialog.newInstance()
        }
        mMultiCategoryPickerDialog!!.setCallback(this)
    }

    /**
     * A parcelable array comes back as `Parcelable[]` on some devices whatever was put in
     * it, so the elements are copied one at a time instead of casting the array itself. This is
     * the same guard `WalletPicker` carries for the same reason.
     *
     * @param parcelables array read out of a bundle, possibly null.
     * @return the categories in it, or null when there were none.
     */
    private fun toCategories(parcelables: Array<Parcelable?>?): Array<Category?>? {
        if (parcelables == null) {
            return null
        }
        val categories = arrayOfNulls<Category>(parcelables.size)
        for (i in parcelables.indices) {
            categories[i] = parcelables[i] as Category?
        }
        return categories
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        if (mMultiCategory) {
            mMultiCategoryController?.onCategoryListChanged(tag, mCurrentCategories)
        } else {
            mController?.onCategoryChanged(tag, mCurrentCategory)
        }
    }

    private fun getDialogTag(): String {
        return tag + "::DialogFragment"
    }

    private fun getMultiDialogTag(): String {
        return tag + "::MultiDialogFragment"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_MULTI_CATEGORY, mMultiCategory)
        outState.putParcelable(SS_CURRENT_CATEGORY, mCurrentCategory)
        outState.putParcelableArray(SS_CURRENT_CATEGORIES, mCurrentCategories)
    }

    fun isSelected(): Boolean {
        if (mMultiCategory) {
            val currentCategories = mCurrentCategories
            return currentCategories != null && currentCategories.size > 0
        }
        return mCurrentCategory != null
    }

    fun getCurrentCategories(): Array<Category?>? {
        return mCurrentCategories
    }

    fun showMultiPicker() {
        mMultiCategoryPickerDialog!!.showPicker(childFragmentManager, getMultiDialogTag(), mCurrentCategories)
    }

    fun setCategory(category: Category?) {
        mCurrentCategory = category
        fireCallbackSafely()
    }

    fun getCurrentCategory(): Category? {
        return mCurrentCategory
    }

    fun showPicker() {
        showPicker(true, false)
    }

    fun showPicker(showSubCategories: Boolean, showSystemCategories: Boolean) {
        val intent = Intent(activity, CategoryPickerActivity::class.java)
        intent.putExtra(CategoryPickerActivity.SHOW_SUB_CATEGORIES, showSubCategories)
        intent.putExtra(CategoryPickerActivity.SHOW_SYSTEM_CATEGORIES, showSystemCategories)
        startActivityForResult(intent, REQUEST_CATEGORY_PICKER)
    }

    /**
     * Offers the categories of one type alone, for a choice that has to follow a direction
     * already made elsewhere on the screen.
     */
    fun showPicker(type: Contract.CategoryType?) {
        val intent = Intent(activity, CategoryPickerActivity::class.java)
        intent.putExtra(CategoryPickerActivity.SHOW_SUB_CATEGORIES, true)
        intent.putExtra(CategoryPickerActivity.SHOW_SYSTEM_CATEGORIES, false)
        intent.putExtra(CategoryPickerActivity.ONLY_TYPE, type)
        startActivityForResult(intent, REQUEST_CATEGORY_PICKER)
    }

    fun showParentPicker(categoryId: Long, type: Contract.CategoryType?) {
        mParentCategoryPickerDialog!!.showPicker(childFragmentManager, getDialogTag(), mCurrentCategory, categoryId, type)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CATEGORY_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                mCurrentCategory = data!!.getParcelableExtra(CategoryPickerActivity.RESULT_CATEGORY)
                fireCallbackSafely()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
        mMultiCategoryController = null
    }

    override fun onCategoriesSelected(categories: Array<Category?>?) {
        mCurrentCategories = categories
        fireCallbackSafely()
    }

    override fun onCategorySelected(category: Category?) {
        mCurrentCategory = category
        fireCallbackSafely()
    }

    interface Controller {

        fun onCategoryChanged(tag: String?, category: Category?)
    }

    interface MultiCategoryController {

        fun onCategoryListChanged(tag: String?, categories: Array<Category?>?)
    }
}
