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

package com.oriondev.moneywallet.model

import java.util.ArrayList

/**
 * Created by andrea on 13/08/18.
 */
class CategoryMoney {

    private val mId: Long
    private val mName: String?
    private val mIcon: Icon?
    private val mMoney: Money
    private val mChildren: MutableList<CategoryMoney> = ArrayList()

    constructor(id: Long, name: String?, icon: Icon?, money: Money) {
        mId = id
        mName = name
        mIcon = icon
        mMoney = money
    }

    fun getChildren(): MutableList<CategoryMoney> {
        return mChildren
    }

    fun addChild(child: CategoryMoney) {
        mChildren.add(child)
    }

    fun getId(): Long {
        return mId
    }

    fun getName(): String? {
        return mName
    }

    fun getIcon(): Icon? {
        return mIcon
    }

    fun getMoney(): Money {
        return mMoney
    }
}
