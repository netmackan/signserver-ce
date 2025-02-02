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
package org.signserver.ejb.interfaces;

import jakarta.ejb.Local;
import jakarta.ejb.Remote;
import org.signserver.common.GlobalConfiguration;

import org.signserver.common.ResyncException;
import org.signserver.server.log.AdminInfo;

/**
 * Common interface containing all the session bean methods.
 *
 * @version $Id$
 */
public interface GlobalConfigurationSession {

    String LOG_OPERATION = "GLOBALCONFIG_OPERATION";
    String LOG_PROPERTY = "GLOBALCONFIG_PROPERTY";
    String LOG_VALUE = "GLOBALCONFIG_VALUE";

    /**
     * Method setting a global configuration property. For node. prefix will the
     * node id be appended.
     * @param scope one of the GlobalConfiguration.SCOPE_ constants
     * @param key of the property should not have any scope prefix, never null
     * @param value the value, never null.
     */
    void setProperty(String scope, String key, String value);

    /**
     * Method used to remove a property from the global configuration.
     * @param scope one of the GlobalConfiguration.SCOPE_ constants
     * @param key of the property should start with either glob. or node.,
     * never null
     * @return true if removal was successful, othervise false.
     */
    boolean removeProperty(String scope, String key);

    /**
     * Method that returns all the global properties with Global Scope and Node
     * scopes properties for this node.
     * @return A GlobalConfiguration Object, never null
     */
    GlobalConfiguration getGlobalConfiguration();

    /**
     * Method that is used after a database crash to restore all cached data to
     * database.
     * @throws ResyncException if resync was unsuccessfull
     */
    void resync() throws ResyncException;

    /**
     * Method to reload all data from database.
     */
    void reload();

}
