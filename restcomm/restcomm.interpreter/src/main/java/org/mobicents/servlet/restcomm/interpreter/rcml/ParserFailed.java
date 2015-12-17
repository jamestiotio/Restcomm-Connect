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
package org.mobicents.servlet.restcomm.interpreter.rcml;

/**
 * Created by gvagenas on 02/12/15.
 */
public class ParserFailed {

    final private Exception exception;
    final private String xml;

    public ParserFailed(final Exception exception, final String xml) {
        this.exception = exception;
        this.xml = xml;
    }

    public Exception getException() {
        return exception;
    }

    public String getXml() {
        return xml;
    }
}