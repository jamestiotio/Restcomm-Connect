/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
	
package org.mobicents.servlet.restcomm.telephony.RestResources;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@Path("/DialAction")
public class DialActionResources {

    private static Logger logger = Logger.getLogger(DialActionResources.class);
    private static MultivaluedMap<String, String> postRequestData = null;
    private static UriInfo getRequestData = null;
    
    @GET
    public Response getRequest(@Context UriInfo info){
        logger.info("Received GET request for Dial Action");
        getRequestData = info; 
        return Response.ok().build();
    }
    
    @POST
    public Response postRequest(final MultivaluedMap<String, String> data){
        logger.info("Received POST request for Dial Action");
        postRequestData = data;
        return Response.ok().build();
    }

    public static MultivaluedMap<String, String> getPostRequestData() {
        return postRequestData;
    }

    public static UriInfo getGetRequestData() {
        return getRequestData;
    }
    
    public static void resetData(){
        postRequestData = null;
        getRequestData = null;
    }
}
