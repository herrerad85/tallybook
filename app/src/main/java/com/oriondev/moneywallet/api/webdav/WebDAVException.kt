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

package com.oriondev.moneywallet.api.webdav;

import com.oriondev.moneywallet.api.BackendException;

/**
 * Extends {@link BackendException} so the API layer can let it through untouched.
 *
 * Recoverability is load bearing rather than cosmetic: an unrecoverable exception from a folder
 * listing makes {@code BackupHandlerFragment} disconnect the account. Only a credential rejection
 * earns that. A timeout, a dropped connection or a server side error is recoverable, because a
 * phone that is briefly offline must not silently lose its saved server.
 */
public class WebDAVException extends BackendException {

    public WebDAVException(String message, boolean recoverable) {
        super(message, recoverable);
    }

    public WebDAVException(String message, Throwable cause, boolean recoverable) {
        super(message, cause, recoverable);
    }

    static WebDAVException forStatus(int status, String action) {
        boolean recoverable = status != 401 && status != 403;
        return new WebDAVException(
                String.format("Server refused to %s (HTTP %d)", action, status),
                recoverable
        );
    }
}
