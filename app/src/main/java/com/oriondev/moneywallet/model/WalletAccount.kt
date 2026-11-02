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

package com.oriondev.moneywallet.model;

import androidx.annotation.NonNull;

import com.oriondev.moneywallet.utils.IconLoader;

/**
 * One entry of the wallet switcher in the navigation drawer: a wallet, or the total of them.
 */
public class WalletAccount {

    private final long mId;
    private final String mName;
    private final Icon mIcon;
    private final Money mMoney;
    private final String mGroup;

    public WalletAccount(long id, String name, Icon icon, Money money) {
        this(id, name, icon, money, null);
    }

    public WalletAccount(long id, String name, Icon icon, Money money, String group) {
        mId = id;
        mName = name;
        mIcon = icon != null ? icon : IconLoader.UNKNOWN;
        mMoney = money;
        mGroup = group;
    }

    public long getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    @NonNull
    public Icon getIcon() {
        return mIcon;
    }

    public Money getMoney() {
        return mMoney;
    }

    /**
     * @return the name of the group this wallet belongs to, or null when it is in none.
     */
    public String getGroup() {
        return mGroup;
    }
}