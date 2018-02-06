/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.restcomm.connect.dao.exceptions;

import org.restcomm.connect.commons.exceptions.RestcommRuntimeException;

/**
 * Thrown when an operation needs to process account hierarchies with greater
 * depth then the one allowed.
 *
 * @author orestis.tsakiridis@telestax.com - Orestis Tsakiridis
 */
public class AccountHierarchyDepthCrossed extends RestcommRuntimeException {

    public AccountHierarchyDepthCrossed() {
    }

    public AccountHierarchyDepthCrossed(String message) {
        super(message);
    }

    public AccountHierarchyDepthCrossed(String message, Throwable cause) {
        super(message, cause);
    }

    public AccountHierarchyDepthCrossed(Throwable cause) {
        super(cause);
    }

    public AccountHierarchyDepthCrossed(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
