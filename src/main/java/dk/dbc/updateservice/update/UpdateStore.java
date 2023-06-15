package dk.dbc.updateservice.update;

import dk.dbc.updateservice.entities.DpkOverride;
import dk.dbc.updateservice.utils.DeferredLogger;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

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
    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateStore.class);

    @PersistenceContext
    private EntityManager entityManager;

    public String getNewDoubleRecordKey() {
        final String uuid = UUID.randomUUID().toString();
        final DpkOverride dpkOverride = new DpkOverride();
        dpkOverride.setRequestUuid(uuid);
        dpkOverride.setCreatedDtm(new Date());
        entityManager.persist(dpkOverride);
        LOGGER.use(l -> l.info("Inserted updateStore object: {}", dpkOverride));
        return uuid;
    }

    public boolean doesDoubleRecordKeyExist(String key) {
        final DpkOverride dpkOverride = entityManager.find(DpkOverride.class, key, LockModeType.PESSIMISTIC_WRITE);
        return LOGGER.call(log -> {
            log.debug("UpdateStore.doesDoubleRecordKeyExist, entityManager.find: {}", dpkOverride);
            if (dpkOverride != null) {
                entityManager.refresh(dpkOverride); // This is necessary to make sure we don't get a cached hit
                final LocalDateTime updateStoreCreateDate = LocalDateTime.ofInstant(dpkOverride.getCreatedDtm().toInstant(), ZoneId.systemDefault());
                if (updateStoreCreateDate.isAfter(LocalDateTime.now().minusDays(1))) {
                    log.info("Found double record frontend key object: {}. Object will now be removed.", dpkOverride);
                    return true;
                } else {
                    log.info("Found old double record frontend key object: {}. Object will now be removed.", dpkOverride);
                }
                entityManager.remove(dpkOverride);
            }
            return false;
        });
    }
}
