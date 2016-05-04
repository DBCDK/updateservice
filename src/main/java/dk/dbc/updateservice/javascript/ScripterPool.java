package dk.dbc.updateservice.javascript;

import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
public class ScripterPool {
    private static final XLogger logger = XLoggerFactory.getXLogger(ScripterPool.class);

    private BlockingQueue<ScripterEnvironment> environments;

    /**
     * JNDI settings.
     */
    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    /**
     * Asynchronous EJB to create new engines.
     */
    @EJB
    ScripterEnvironmentFactory scripterEnvironmentFactory;

    private Status status;

    public enum Status {
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
    @PostConstruct
    public void init() {
        logger.entry();
        try {
            logger.debug("Starting creation of javascript environments.");
            status = Status.ST_CREATE_ENVS;

            int poolSize = Integer.valueOf(settings.getProperty(JNDIResources.JAVASCRIPT_POOL_SIZE_KEY));
            logger.debug("Pool size: {}", poolSize);

            this.environments = new LinkedBlockingQueue<>(poolSize);

            for (int i = 0; i < poolSize; i++) {
                logger.debug("Starting javascript environments factory: {}", i + 1);
                try {
                    put(scripterEnvironmentFactory.newEnvironment(settings));
                } catch (InterruptedException | ScripterException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }

            logger.debug("Done creating {} javascript environments", poolSize);
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
    ScripterEnvironment take() throws InterruptedException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();
        int queueSize = -1;

        try {
            if (status == Status.ST_CREATE_ENVS) {
                return null;
            }

            queueSize = environments.size();

            logger.info("Take environment from queue with size: {}", queueSize);
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
            if (status == Status.ST_CREATE_ENVS) {
                int poolSize = Integer.valueOf(settings.getProperty(JNDIResources.JAVASCRIPT_POOL_SIZE_KEY));

                logger.debug("Put new environment into the pool");
                environments.put(environment);

                if (environments.size() == poolSize) {
                    logger.debug("ScripterPool is initialized and ready to be used!");
                    status = Status.ST_OK;
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
        return status;
    }
}
