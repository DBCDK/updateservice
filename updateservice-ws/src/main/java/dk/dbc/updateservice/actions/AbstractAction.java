//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import java.util.ArrayList;
import java.util.List;

//-----------------------------------------------------------------------------
public abstract class AbstractAction implements ServiceAction {
    public AbstractAction( String name ) {
        this( null, name );
    }

    public AbstractAction( ServiceAction parent, String name ) {
        this.name = name;
        this.parent = parent;
        this.children = new ArrayList<>();
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

    @Override
    public ServiceResult getServiceResult() {
        return serviceResult;
    }

    @Override
    public void setServiceResult( ServiceResult serviceResult ) {
        this.serviceResult = serviceResult;
    }

    @Override
    public ServiceAction parent() {
        return this.parent;
    }

    @Override
    public List<ServiceAction> children() {
        return this.children;
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

    protected String name;
    protected ServiceResult serviceResult;
    protected ServiceAction parent;
    protected List<ServiceAction> children;
}
