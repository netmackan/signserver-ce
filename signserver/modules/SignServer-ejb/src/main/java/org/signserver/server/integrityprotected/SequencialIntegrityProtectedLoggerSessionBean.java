/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.server.integrityprotected;

import java.util.Map;
import java.util.Properties;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.apache.log4j.Logger;
import org.cesecore.audit.enums.EventStatus;
import org.cesecore.audit.enums.EventType;
import org.cesecore.audit.enums.ModuleType;
import org.cesecore.audit.enums.ServiceType;
import org.cesecore.audit.impl.integrityprotected.AuditRecordData;
import org.cesecore.audit.log.AuditRecordStorageException;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.time.TrustedTime;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.QueryResultWrapper;

/**
 * IntegrityProtectedLoggerSessionBean implementation but supporting using the provided sequence number.
 *
 * @see SequencialIntegrityProtectedDevice
 * @version $Id$
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class SequencialIntegrityProtectedLoggerSessionBean implements SequencialIntegrityProtectedLoggerSessionLocal {

    private static final Logger log = Logger.getLogger(SequencialIntegrityProtectedLoggerSessionBean.class);

    @PersistenceContext(unitName = CesecoreConfiguration.PERSISTENCE_UNIT)
    private EntityManager entityManager;

    @PostConstruct
    public void postConstruct() {
        CryptoProviderTools.installBCProviderIfNotAvailable();
    }

    /**
     * Initialization of the log sequence number in combination with nodeId should be performed exactly once.
     *
     * This callback will be invoked on the first call to NodeSequenceHolder.getNext(...) to perform this initialization.
     *
     * In this callback implementation, the nodeId is first read from the configuration (which may default to reading
     * the current hostname from the system). This hostname is then passed to the next method to figure out what the
     * highest present sequenceNumber for this nodeId is in the database (e.g. last write before shutting down).
     */
    private final SequencialNodeSequenceHolder.OnInitCallBack sequenceHolderInitialization = new SequencialNodeSequenceHolder.OnInitCallBack() {
        @Override
        public String getNodeId() {
            return CesecoreConfiguration.getNodeIdentifier();
        }
        @Override
        public long getMaxSequenceNumberForNode(final String nodeId) {
            // Get the latest sequenceNumber from last run from the database..
            final Query query = entityManager.createQuery("SELECT MAX(a.sequenceNumber) FROM AuditRecordData a WHERE a.nodeId=:nodeId");
            query.setParameter("nodeId", nodeId);
            return QueryResultWrapper.getSingleResult(query, -1L);
        }
    };

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    // Always persist audit log
    public void log(final TrustedTime trustedTime, final EventType eventType, final EventStatus eventStatus, final ModuleType module,
            final ServiceType service, final String authToken, final String customId, final String searchDetail1, final String searchDetail2,
            final Map<String, Object> additionalDetails, final Properties properties) throws AuditRecordStorageException {
        throw new AuditRecordStorageException("Not supported by " + getClass().getName());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    // Always persist audit log
    public void logWithSequenceNumber(final TrustedTime trustedTime, final EventType eventType, final EventStatus eventStatus, final ModuleType module,
            final ServiceType service, final String authToken, final String customId, final String searchDetail1, final String searchDetail2,
            final Map<String, Object> additionalDetails, final Properties properties, final Long sequenceNumber) throws AuditRecordStorageException {
        if (log.isTraceEnabled()) {
            log.trace(String.format(">log:%s:%s:%s:%s:%s:%s", eventType, eventStatus, module, service, authToken, additionalDetails));
        }
        try {
            // Make sure to use the Node Identifier that this log sequence was initialized with (for example hostnames reported by the system could change)
            final long startSequenceNumber = SequencialNodeSequenceHolder.INSTANCE.getStartSequence(sequenceHolderInitialization);
            if (log.isTraceEnabled()) {
                log.trace("sequencial logging with sequence number: " + (startSequenceNumber + sequenceNumber));
            }
            final String nodeId = SequencialNodeSequenceHolder.INSTANCE.getNodeId();
            final Long timeStamp = trustedTime.getTime().getTime();
            final AuditRecordData auditRecordData = new AuditRecordData(nodeId, startSequenceNumber + sequenceNumber, timeStamp, eventType, eventStatus, authToken,
                    service, module, customId, searchDetail1, searchDetail2, additionalDetails);
            entityManager.persist(auditRecordData);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new AuditRecordStorageException(e.getMessage(), e);
        } finally {
            if (log.isTraceEnabled()) {
                log.trace("<log");
            }
        }
    }
}
