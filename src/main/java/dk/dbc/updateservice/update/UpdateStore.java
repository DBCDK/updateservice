/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.updateservice.entities.DpkOverride;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * EJB for handling access to the Updateservice DB
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class UpdateStore {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateStore.class);

    @PersistenceContext
    private EntityManager entityManager;

    public String getNewDoubleRecordKey() {
        final String uuid = UUID.randomUUID().toString();
        final DpkOverride dpkOverride = new DpkOverride();
        dpkOverride.setRequestUuid(uuid);
        dpkOverride.setCreatedDtm(new Date());
        entityManager.persist(dpkOverride);
        LOGGER.info("Inserted updatestore object: {}", dpkOverride);
        return uuid;
    }

    public boolean doesDoubleRecordKeyExist(String key) {
        final DpkOverride dpkOverride = entityManager.find(DpkOverride.class, key, LockModeType.PESSIMISTIC_WRITE);
        LOGGER.debug("UpdateStore.doesDoubleRecordKeyExist, entityManager.find: {}", dpkOverride);
        if (dpkOverride != null) {
            entityManager.refresh(dpkOverride); // This is necessary to make sure we don't get a cached hit
            final LocalDateTime updatestoreCreateDate = LocalDateTime.ofInstant(dpkOverride.getCreatedDtm().toInstant(), ZoneId.systemDefault());
            if (updatestoreCreateDate.isAfter(LocalDateTime.now().minusDays(1))) {
                LOGGER.info("Found doublerecord frontend key object: {}. Object will now be removed.", dpkOverride);
                return true;
            } else {
                LOGGER.info("Found old doublerecord frontend key object: {}. Object will now be removed.", dpkOverride);
            }
            entityManager.remove(dpkOverride);
        }
        return false;
    }
}
