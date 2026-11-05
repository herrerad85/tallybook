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
 * Created by andrea on 31/03/18.
 */
class License {

    private val mName: String?
    private val mAuthor: String?
    private val mWebsite: String?
    private val mVersion: String?
    private val mCopyright: String?
    private val mType: Type

    constructor(name: String?, author: String?, website: String?, version: String?, copyright: String?, type: String?) {
        mName = name
        mAuthor = author
        mWebsite = website
        mVersion = version
        mCopyright = copyright
        mType = parse(type)
    }

    fun getName(): String? {
        return mName
    }

    fun getAuthor(): String? {
        return mAuthor
    }

    fun getUrl(): String? {
        return mWebsite
    }

    fun getVersion(): String? {
        return mVersion
    }

    fun getCopyright(): String? {
        return mCopyright
    }

    fun getType(): Type {
        return mType
    }

    fun getTypeName(): String {
        when (mType) {
            Type.APACHE ->
                return "Apache License"
            Type.MIT ->
                return "MIT License"
            Type.ISC ->
                return "ISC License"
            Type.GPL3 ->
                return "GNU GPLv3"
            Type.GPL2 ->
                return "GNU GPLv2"
            Type.AGPL ->
                return "GNU Affero GPL"
            Type.CUSTOM ->
                return "Custom License"
            else ->
                return "Unknown license"
        }
    }

    enum class Type {
        UNKNOWN,
        CUSTOM,
        APACHE,
        GPL2,
        GPL3,
        AGPL,
        MIT,
        ISC
    }

    companion object {

        private fun parse(type: String?): Type {
            if (type != null) {
                when (type) {
                    "MIT" ->
                        return Type.MIT
                    "ISC" ->
                        return Type.ISC
                    "APACHE" ->
                        return Type.APACHE
                    "GPL2" ->
                        return Type.GPL2
                    "GPL3" ->
                        return Type.GPL3
                    "AGPL" ->
                        return Type.AGPL
                    "CUSTOM" ->
                        return Type.CUSTOM
                }
            }
            return Type.UNKNOWN
        }
    }
}
