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

package com.oriondev.moneywallet.broadcast

/**
 * Created by andrea on 03/04/18.
 */
object Message {

    const val ITEM_ID = "LocalBroadCast::Message::ItemId"
    const val ITEM_TYPE = "LocalBroadCast::Message::ItemType"

    const val TYPE_TRANSACTION = 1
    const val TYPE_TRANSFER = 2
    const val TYPE_CATEGORY = 3
    const val TYPE_DEBT = 4
    const val TYPE_BUDGET = 5
    const val TYPE_SAVING = 6
    const val TYPE_EVENT = 7
    const val TYPE_RECURRENT_TRANSACTION = 8
    const val TYPE_RECURRENT_TRANSFER = 9
    const val TYPE_TRANSACTION_MODEL = 10
    const val TYPE_TRANSFER_MODEL = 11
}
