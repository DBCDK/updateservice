package dk.dbc.updateservice.javascript;

import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton pool of ScripterEnvironment's
 * <p>
 * This singleton EJB implements Updates pool of JavaScript engines. Is has methods
 * to take and put engines to the pool.
 * </p>
 * <p>
 * Each environment is created at startup of the application in separate threads. The
 * pool does not wait for the engines to be fully created before it returns control back to
 * the Web Application Server. So it is posible to receive requests before an engine is
 * ready. This will cause the request to wait for an engine, before executing any JavaScript.
 * </p>
 * <p>
 * Basic usage of the EJB will be:
 * <code>
 * <pre>
 *             @EJB
 *             ScripterPool pool;
 *
 *             public void method() {
 *                 ScripterEnvironment e = pool.take();
 *                 e.callMethod( "func" );
 *                 pool.put( e );
 *             }
 *         </pre>
 * </code>
 * Remember handling of exceptions so ypu always put the environment back into the pool.
 * </p>
 */
@Singleton
@Startup
@Lock(LockType.READ)

public class ScripterPool {
    private static final XLogger logger = XLoggerFactory.getXLogger(ScripterPool.class);

    private BlockingQueue<ScripterEnvironment> environments;

    // replace with atomic int
    private AtomicInteger initializedEnvironments = new AtomicInteger();

    // replace with atomic int
    private AtomicInteger poolSize = new AtomicInteger();

    /**
     * JNDI settings.
     */
    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    /**
     * Asynchronous EJB to create new engines.
     */
    @SuppressWarnings("EjbEnvironmentInspection")
    @EJB
    ScripterEnvironmentFactory scripterEnvironmentFactory;

    public enum Status {
        ST_NA,
        ST_CREATE_ENVS,
        ST_OK
    }

    /**
     * Constructs engines for a pool in separate threads.
     * <p>
     * The return value from {@link dk.dbc.updateservice.javascript.ScripterEnvironmentFactory.newEnvironment()}
     * is not used since the pool is parsed to {@link dk.dbc.updateservice.javascript.ScripterEnvironmentFactory.newEnvironment()}.
     * {@link dk.dbc.updateservice.javascript.ScripterEnvironmentFactory.newEnvironment()} will add the new engine to the pool then it
     * is created.
     * </p>
     */
    // changed name to postconstruct
    @PreDestroy
    public void postConstruct() {
        logger.entry();
        try {
            logger.debug("Starting creation of javascript environments.");

            poolSize.getAndSet( Integer.valueOf(settings.getProperty(JNDIResources.JAVASCRIPT_POOL_SIZE_KEY)));
            logger.info("Pool size: {}", poolSize);


            logger.info("mvs system hashcode");
            logger.info("System.identityHashCode(this) : ", System.identityHashCode(this));
            logger.info("this.hashCode(): ", this.hashCode());
            logger.info("this : ", this);

            environments = new LinkedBlockingQueue<>(poolSize.intValue());
            logger.error("mvs #0.5");
            try {
                logger.error("mvs #1");
                // This "ugly hack" (the javaee way) is done because initializeJavascriptEnvironments needs to be called asynchnous
                ScripterPool scripterPool = InitialContext.doLookup("java:global/updateservice-1.0-SNAPSHOT/ScripterPool");

                logger.error("mvs #2");
                initializeJavascriptEnvironments();
                logger.error("mvs #3");
                logger.error("mvs hest are we exiting the init ? ");
            } catch (NamingException e) {
                logger.catching(XLogger.Level.ERROR, e);
                throw new EJBException("Updateservice could not initialize Javascript environments", e);
            }
            logger.info("Started creating {} javascript environments", poolSize);
        } finally {
            logger.exit();
        }
    }

    @Asynchronous
    public void initializeJavascriptEnvironments() {
        logger.entry(poolSize);
        try {
            for (int i = 0; i < poolSize.get(); i++) {
                logger.info("Starting javascript environments factory: {}", i + 1);
                try {
                    logger.error ("mvs initializeJavascriptEnvironments 1# ");
                    logger.error ("mvs initializeJavascriptEnvironments settings " , settings);
                    ScripterEnvironment scripterEnvironment = scripterEnvironmentFactory.newEnvironment(settings);
                    logger.error ("mvs initializeJavascriptEnvironments 2# ");
                    put(scripterEnvironment);
                } catch (InterruptedException | ScripterException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
        } finally {
            logger.exit();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary until an element becomes available.
     * <p>
     * <b>Description copied from class:</b> {@link java.util.concurrent.LinkedBlockingQueue}
     * </p>
     *
     * @return the head of this pool
     * @throws InterruptedException if interrupted while waiting
     */
    public ScripterEnvironment take() throws InterruptedException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();
        int queueSize = -1;

        try {
            if (initializedEnvironments.get() == 0) {
                return null;
            }
            queueSize = environments.size();
            logger.info("Take environment from queue with size: {}", queueSize);
            logger.info("initializedEnvironments : ", initializedEnvironments);
            logger.info("environments: ", environments);
            return environments.take();
        } finally {
            watch.stop("javascript.env.take." + queueSize);
            logger.exit();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if necessary for
     * space to become available.
     * <p>
     * <b>Description copied from class:</b> {@link java.util.concurrent.LinkedBlockingQueue}
     * </p>
     *
     * @param environment the element to add
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if the specified element is null
     */
    public void put(ScripterEnvironment environment) throws InterruptedException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch("javascript.env.put");
        try {
            if (initializedEnvironments.get() < poolSize.get()) {
                logger.debug("Put new environment into the pool");
                environments.put(environment);
                initializedEnvironments.getAndAdd(1);

                logger.info("initializedEnvironments : ", initializedEnvironments);
                logger.info("poolsize : ", poolSize);

                logger.info("are we null");
                if (initializedEnvironments.intValue() == 0) {
                    logger.info("initializedEnvironments == 0");
                }
                if (poolSize.intValue() == 0) {
                    logger.info("poolSize == 0 ");
                }

                if (initializedEnvironments.intValue() == poolSize.intValue()) {
                    logger.info("initializedEnvironments : ", initializedEnvironments);
                    logger.info("poolsize : ", poolSize);
                    logger.info("ScripterPool is initialized and ready to be used!");
                }
            } else {
                logger.debug("Put environment back into the pool");
                environments.put(environment);
            }
        } finally {
            watch.stop();
            logger.exit();
        }
    }

    public Status getStatus() {
        if (initializedEnvironments.intValue() == 0) {
            return Status.ST_NA;
        } else if (initializedEnvironments.intValue() < poolSize.intValue()) {
            return Status.ST_CREATE_ENVS;
        } else {
            return Status.ST_OK;
        }
    }
}
