package dk.dbc.updateservice.update;

import dk.dbc.updateservice.entities.DpkOverride;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
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
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateStore.class);

    @PersistenceContext
    private EntityManager entityManager;

    public String getNewDoubleRecordKey() {
        String uuid = UUID.randomUUID().toString();
        DpkOverride dpkOverride = new DpkOverride();
        dpkOverride.setRequestUuid(uuid);
        dpkOverride.setCreatedDtm(new Date());
        entityManager.persist(dpkOverride);
        logger.info("Inserted updatestore object: " + dpkOverride);
        return uuid;
    }

    public boolean doesDoubleRecordKeyExist(String key) {
        boolean res = false;
        DpkOverride dpkOverride = entityManager.find(DpkOverride.class, key);
        entityManager.refresh(dpkOverride); // This is necessary to make sure we don't get a cached hit
        if (dpkOverride != null) {
            LocalDateTime updatestoreCreateDate = LocalDateTime.ofInstant(dpkOverride.getCreatedDtm().toInstant(), ZoneId.systemDefault());
            if (updatestoreCreateDate.isAfter(LocalDateTime.now().minusDays(1))) {
                logger.info("Found doublerecord frontend key object: " + dpkOverride + ". Object will now be removed");
                res = true;
            } else {
                logger.info("Found old doublerecord frontend key object: " + dpkOverride + ". Object will now be removed");
            }
            entityManager.remove(dpkOverride);
        }
        return res;
    }
}
