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

/**
 * Created by andrea on 06/04/18.
 */
class ChangeLog {

    private var type: Int = 0
    private var versionName: String? = null
    private var versionDate: String? = null
    private var changeText: String? = null
    private var changeType: Int = 0

    fun getType(): Int {
        return type
    }

    fun setType(type: Int) {
        this.type = type
    }

    fun getVersionName(): String? {
        return versionName
    }

    fun setVersionName(versionName: String?) {
        this.versionName = versionName
    }

    fun getVersionDate(): String? {
        return versionDate
    }

    fun setVersionDate(versionDate: String?) {
        this.versionDate = versionDate
    }

    fun getChangeText(): String? {
        return changeText
    }

    fun setChangeText(changeText: String?) {
        this.changeText = changeText
    }

    fun getChangeType(): Int {
        return changeType
    }

    fun setChangeType(changeType: Int) {
        this.changeType = changeType
    }

    // BUILDER CLASS

    class HeaderBuilder {

        private var versionName: String? = null
        private var versionDate: String? = null

        fun versionName(versionName: String?): HeaderBuilder {
            this.versionName = versionName
            return this
        }

        fun versionDate(versionDate: String?): HeaderBuilder {
            this.versionDate = versionDate
            return this
        }

        fun build(): ChangeLog {
            val changeLog = ChangeLog()
            changeLog.setType(TYPE_HEADER)
            changeLog.setVersionName(versionName)
            changeLog.setVersionDate(versionDate)
            return changeLog
        }
    }

    class ItemBuilder {

        private var changeText: String? = null

        fun changeText(changeText: String?): ItemBuilder {
            this.changeText = changeText
            return this
        }

        fun build(): ChangeLog {
            val changeLog = ChangeLog()
            changeLog.setType(TYPE_ITEM)
            changeLog.setChangeText(changeText)
            return changeLog
        }
    }

    companion object {

        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}
