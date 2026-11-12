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

package com.oriondev.moneywallet.service;

import android.app.Notification;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.os.Build;

/*package-local*/ class ForegroundServices {

    private ForegroundServices() {
    }

    /**
     * An app targeting API 34 or later must declare a type on any foreground service it starts,
     * and starting one without a type throws rather than being ignored. The typed overload of
     * startForeground exists from API 29, so anything older keeps the two argument call.
     *
     * <p>The caller's own service entry in the manifest must declare dataSync as well. The
     * runtime type has to be a subset of the manifest declaration, and that check is not gated on
     * a target version: it has thrown since API 29. So this is not safe to call from a service
     * that has not been given the declaration.
     *
     * @param service      the service asking to run in the foreground.
     * @param id           notification id, which must not be zero.
     * @param notification the notification to show while it runs.
     */
    /*package-local*/ static void startDataSync(Service service, int id, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            service.startForeground(id, notification);
        }
    }
}
