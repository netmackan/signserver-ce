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
package org.signserver.statusrepo;

import jakarta.ejb.Remote;

/**
 * Interface towards the status repository session bean.
 * 
 * @author Markus Kilås
 * @version $Id$
 */
@Remote
public interface StatusRepositorySessionRemote extends StatusRepositorySession {
}
