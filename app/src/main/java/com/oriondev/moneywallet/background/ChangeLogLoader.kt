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

package com.oriondev.moneywallet.background

import android.content.Context

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ChangeLog

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import java.io.IOException

/**
 * Created by andrea on 06/04/18.
 */
class ChangeLogLoader : AbstractGenericLoader<MutableList<ChangeLog>> {

    companion object {

        private const val VERSION = "version"
        private const val CHANGE_TEXT = "change"
        private const val VERSION_NAME = "versionName"
        private const val VERSION_DATE = "versionDate"
    }

    constructor(context: Context) : super(context)

    override fun loadInBackground(): MutableList<ChangeLog>? {
        val changeLogs: MutableList<ChangeLog> = ArrayList()
        val parser: XmlPullParser = context.resources.getXml(R.xml.changelog)
        try {
            var eventType = parser.eventType
            var buffer: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals(VERSION)) {
                            val changeLog = ChangeLog.HeaderBuilder()
                                    .versionName(parser.getAttributeValue(null, VERSION_NAME))
                                    .versionDate(parser.getAttributeValue(null, VERSION_DATE))
                                    .build()
                            changeLogs.add(changeLog)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        buffer = parser.text
                        if (buffer != null) {
                            buffer = buffer.replace(Regex("\\["), "<").replace(Regex("\\]"), ">")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals(CHANGE_TEXT)) {
                            val changeLog = ChangeLog.ItemBuilder()
                                    .changeText(buffer)
                                    .build()
                            changeLogs.add(changeLog)
                            buffer = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            // Log.e("ChangeLogLoader", "Exception while parsing change log xml file", e);
        } catch (e: IOException) {
            // Log.e("ChangeLogLoader", "I/O Exception while parsing change log xml file", e);
        }
        return changeLogs
    }
}
