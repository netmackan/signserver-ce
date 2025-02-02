/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.common;

import jakarta.xml.ws.WebFault;

/**
 * Class thrown if a resync to data base failed. 
 * 
 * @author Philip Vendil
 * @version $Id$
 */
@WebFault
public class ResyncException extends Exception {

    private static final long serialVersionUID = 1L;

    public ResyncException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public ResyncException(String arg0) {
        super(arg0);
    }
}
