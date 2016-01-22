//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

//-----------------------------------------------------------------------------
public abstract class AbstractAction implements ServiceAction {
    public AbstractAction( String name ) {
        this.name = name;
        this.children = new ArrayList<>();
        this.timeElapsed = -1;
    }

    //-------------------------------------------------------------------------
    //              ServiceAction implementation
    //-------------------------------------------------------------------------

    @Override
    public String name() {
        return this.name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    @IgnoreStateChecking
    @Override
    public ServiceResult getServiceResult() {
        return serviceResult;
    }

    @Override
    public void setServiceResult( ServiceResult serviceResult ) {
        this.serviceResult = serviceResult;
    }

    @Override
    public List<ServiceAction> children() {
        return this.children;
    }

    @IgnoreStateChecking
    @Override
    public long getTimeElapsed() {
        return timeElapsed;
    }

    @Override
    public void setTimeElapsed( long timeElapsed ) {
        this.timeElapsed = timeElapsed;
    }

    @Override
    public void checkState() throws UpdateException {
        logger.entry();

        try {
            Method[] methods = getClass().getMethods();

            for( Method method : methods ) {
                String methodName = method.getName();
                if( methodName.startsWith( "get" ) ) {
                    IgnoreStateChecking annotation = method.getAnnotation( IgnoreStateChecking.class );
                    if( annotation == null ) {
                        Object value = method.invoke( this );
                        if( value == null ) {
                            String format = "Illegal state: %s.%s is null";

                            String attrName = method.getName().substring( 3 );
                            attrName = attrName.substring( 0, 1 ).toLowerCase() + attrName.substring( 1 );

                            throw new UpdateException( String.format( format, getClass().getSimpleName(), attrName ) );
                        }
                    }
                }
            }
        }
        catch( Throwable throwable ) {
            throw new UpdateException( throwable.getMessage(), throwable );
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

    /**
     * Finds the service exception that is associated to an exception in the
     * caused chain.
     * </p>
     * This method is not used by this class, but it is placed here to make it accessible
     * to derived classes.
     * <p/>
     * It is used to receive the business exception that is throwed from an EJB.
     *
     * @param ex The exception to receive the business exception from.
     *
     * @return The found business exception or <code>ex</code> if none is found in the
     *         cause chain.
     */
    protected Throwable findServiceException( Exception ex ) {
        Throwable throwable = ex;
        while( throwable != null && throwable.getClass().getPackage().getName().startsWith( "javax.ejb" ) ) {
            throwable = throwable.getCause();
        }

        return throwable;
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( AbstractAction.class );

    protected String name;
    protected ServiceResult serviceResult;
    protected List<ServiceAction> children;
    protected long timeElapsed;
}
