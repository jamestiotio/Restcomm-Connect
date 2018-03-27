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
package org.restcomm.connect.commons.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@ThreadSafe
public final class DigestAuthentication {
    private DigestAuthentication() {
        super();
    }

    private static String A1(final String algorithm, final String user, final String realm, final String password,
            final String nonce, final String cnonce) {
        if (algorithm == null || algorithm.trim().length() == 0 || algorithm.trim().equalsIgnoreCase("MD5")) {
            return user + ":" + realm + ":" + password;
        } else {
            if (cnonce == null || cnonce.length() == 0) {
                throw new NullPointerException("The cnonce parameter may not be null.");
            }
            return H(user + ":" + realm + ":" + password, algorithm) + ":" + nonce + ":" + cnonce;
        }
    }

    private static String A2(String algorithm, final String method, final String uri, String body, final String qop) {
        if (qop == null || qop.trim().length() == 0 || qop.trim().equalsIgnoreCase("auth")) {
            return method + ":" + uri;
        } else {
            if (body == null)
                body = "";
            return method + ":" + uri + ":" + H(body, algorithm);
        }
    }

    public static String response(final String algorithm, final String user, final String realm, final String password,
    final String nonce, final String nc, final String cnonce, final String method, final String uri, String body,
    final String qop) {
        return response(algorithm, user, realm, password, "", nonce, nc, cnonce, method, uri, body, qop);
    }

    /**
     * @param algorithm
     * @param user
     * @param realm
     * @param password
     * @param password2
     * @param nonce
     * @param nc
     * @param cnonce
     * @param method
     * @param uri
     * @param body
     * @param qop
     * @return
     */
    public static String response(final String algorithm, final String user, final String realm, final String password,
            String password2, final String nonce, final String nc, final String cnonce, final String method, final String uri,
            String body, final String qop) {
        validate(user, realm, password, nonce, method, uri, algorithm);
        String ha1;

        if(!password2.isEmpty()){
            ha1 = password2;
        }else{
            final String a1 = A1(algorithm, user, realm, password, nonce, cnonce);
            ha1 = H(a1, algorithm);
        }

        final String a2 = A2(algorithm, method, uri, body, qop);
        if (cnonce != null && qop != null && nc != null && (qop.equalsIgnoreCase("auth") || qop.equalsIgnoreCase("auth-int"))) {
            return KD(ha1, nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + H(a2, algorithm), algorithm);
        } else {
            return KD(ha1, nonce + ":" + H(a2, algorithm), algorithm);
        }
    }

    private static String H(final String data, String algorithm) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            final byte[] result = digest.digest(data.getBytes());
            final char[] characters = HexadecimalUtils.toHex(result);
            return new String(characters);
        } catch (final NoSuchAlgorithmException exception) {
            return null;
        }
    }

    private static String KD(final String secret, final String data, String algorithm) {
        return H(secret + ":" + data, algorithm);
    }

    private static void validate(final String user, final String realm, final String password, final String nonce,
            final String method, final String uri, String algorithm) {
        if (user == null) {
            throw new NullPointerException("The user parameter may not be null.");
        } else if (realm == null) {
            throw new NullPointerException("The realm parameter may not be null.");
        } else if (password == null) {
            throw new NullPointerException("The password parameter may not be null.");
        } else if (method == null) {
            throw new NullPointerException("The method parameter may not be null.");
        } else if (uri == null) {
            throw new NullPointerException("The uri parameter may not be null.");
        } else if (nonce == null) {
            throw new NullPointerException("The nonce parameter may not be null.");
        } else if (algorithm == null) {
            throw new NullPointerException("The algorithm parameter may not be null.");
        }
    }

    /**
     * @param username
     * @param realm
     * @param password
     * @return
     */
    public static String HA1(String username, String realm, String password){
        String algorithm = RestcommConfiguration.getInstance().getMain().getClientAlgorithm();
        String ha1 = "";
        ha1 = DigestAuthentication.H(username+":"+realm+":"+password, algorithm);
        return ha1;
    }

    public static void main(String [] str){
        String username="alice";
        String realm="127.0.0.1";
        String password="1234";
        System.out.println(DigestAuthentication.HA1(username, realm, password));
    }
}
