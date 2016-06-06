package dk.dbc.updateservice.javascript;

import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by mvs on 6/6/16.
 */

@Singleton
@Startup
@Lock(LockType.READ)
public class MvsScripterPool {

    private static final XLogger logger = XLoggerFactory.getXLogger(MvsScripterPool.class);
    @Resource(lookup = "updateservice/settings")
    private Properties settings;

    public enum Status {
        ST_NA,
        ST_CREATE_ENVS,
        ST_OK
    }

    //asynch
    private BlockingQueue<ScripterEnvironment> environments = new LinkedBlockingQueue<>();

    /**
     * Synchronous EJB to create new engines.
     */

    @SuppressWarnings("EjbEnvironmentInspection")
    @EJB
    ScripterEnvironmentFactory scripterEnvironmentFactory;


    @PostConstruct
    //@PreDestroy
    public void postConstruct() {
        logger.entry();
        try {
            logger.debug("Starting creation of mvs javascript environments.");
            // calling non asynch method to work around ejb proxy naming not being created prior to postconstruct
            // glassfish specific error
            dummyWorkAroundToGetEJBProxySetStraight();
            initializeJavascriptEnvironments();
            logger.info("Started creating {} javascript environments");
            logger.info("mvs size environments", environments.size());
        } finally {
            logger.exit();
        }
    }

    public void dummyWorkAroundToGetEJBProxySetStraight() {
        logger.entry();
        try {
            //https://java.net/projects/glassfish/lists/issues/archive/2013-03/message/2294
            logger.info("mvs dummy is being dumb, dummy");
        } finally {
            logger.exit();
        }
    }

    //@Asynchronous
    public void initializeJavascriptEnvironments() {
        logger.entry();
        try {
            logger.info("Starting javascript environments factory: {}");
            try {
                logger.error("mvs initializeJavascriptEnvironments 1# ");
                environments.put(scripterEnvironmentFactory.newEnvironment(settings));
                logger.error("mvs initializeJavascriptEnvironments 2# ");
                logger.error("mvs environments.size() : ", environments.size());
            } catch (ScripterException ex) {
                logger.error(ex.getMessage(), ex);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            logger.exit();
        }
    }

    public ScripterEnvironment take() throws InterruptedException {
        logger.entry();
        try {
            return environments.take();
        } finally {
            logger.exit();
        }
    }

    public void put(ScripterEnvironment environment) throws InterruptedException {
        logger.entry(environment);
        try {
            boolean result = environments.offer(environment, 1000, TimeUnit.MILLISECONDS);
            logger.error("mvs put : " + result);
            logger.error("mvs environments.size() post put : ", environments.size());
        } finally {
            logger.exit();
        }
    }

    public Status getStatus() {
        logger.entry();
        try {
            if (environments.peek() != null) {
                logger.info("returning st_ok");
                return Status.ST_OK;
            }
            logger.info("returning st_na");
            return Status.ST_NA;
        } finally {
            logger.exit();
        }
    }
}
