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
 * Created by andre on 22/03/2018.
 */
object LocalAction {

    const val ACTION_BACKUP_SERVICE_STARTED = "LocalBroadCast::BackupServiceStarted"
    const val ACTION_BACKUP_SERVICE_RUNNING = "LocalBroadCast::BackupServiceRunning"
    const val ACTION_BACKUP_SERVICE_FINISHED = "LocalBroadCast::BackupServiceFinished"
    const val ACTION_BACKUP_SERVICE_FAILED = "LocalBroadCast::BackupServiceFailed"
    const val ACTION_BACKEND_SERVICE_STARTED = "LocalBroadCast::BackendServiceStarted"
    const val ACTION_BACKEND_SERVICE_RUNNING = "LocalBroadCast::BackendServiceRunning"
    const val ACTION_BACKEND_SERVICE_FINISHED = "LocalBroadCast::BackendServiceFinished"
    const val ACTION_BACKEND_SERVICE_FAILED = "LocalBroadCast::BackendServiceFailed"
    const val ACTION_EXCHANGE_RATES_UPDATED = "LocalBroadCast::ExchangeRatesUpdated"
    const val ACTION_ATTACHMENT_OP_STARTED = "LocalBroadCast::AttachmentOperationStarted"
    const val ACTION_ATTACHMENT_OP_FINISHED = "LocalBroadCast::AttachmentOperationFinished"
    const val ACTION_ATTACHMENT_OP_FAILED = "LocalBroadCast::AttachmentOperationFailed"
    const val ACTION_LEGACY_EDITION_UPGRADE_STARTED = "LocalBroadCast::LegacyEditionUpgradeStarted"
    const val ACTION_LEGACY_EDITION_UPGRADE_FINISHED = "LocalBroadCast::LegacyEditionUpgradeFinished"
    const val ACTION_LEGACY_EDITION_UPGRADE_FAILED = "LocalBroadCast::LegacyEditionUpgradeFailed"
    const val ACTION_IMPORT_SERVICE_STARTED = "LocalBroadCast::ImportServiceStarted"
    const val ACTION_IMPORT_SERVICE_FINISHED = "LocalBroadCast::ImportServiceFinished"
    const val ACTION_IMPORT_SERVICE_FAILED = "LocalBroadCast::ImportServiceFailed"
    const val ACTION_EXPORT_SERVICE_STARTED = "LocalBroadCast::ExportServiceStarted"
    const val ACTION_EXPORT_SERVICE_FINISHED = "LocalBroadCast::ExportServiceFinished"
    const val ACTION_EXPORT_SERVICE_FAILED = "LocalBroadCast::ExportServiceFailed"

    const val ACTION_ITEM_CLICK = "LocalBroadCast::ItemClicked"

    const val ACTION_CURRENT_WALLET_CHANGED = "LocalBroadCast::CurrentWalletChanged"
    const val ARGUMENT_WALLET_ID = "LocalBroadCast::WalletId"
    const val ARGUMENT_WALLET_NAME = "LocalBroadCast::WalletName"
}
