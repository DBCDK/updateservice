/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAction implements ServiceAction {
    protected String name;
    protected ServiceResult serviceResult;
    protected List<ServiceAction> children;
    protected long timeElapsed;
    protected GlobalActionState state;

    protected AbstractAction(String actionName, GlobalActionState globalActionState) {
        name = actionName;
        children = new ArrayList<>();
        timeElapsed = -1;
        state = globalActionState;
    }

    @Override
    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ServiceResult getServiceResult() {
        return serviceResult;
    }

    @Override
    public void setServiceResult(ServiceResult serviceResult) {
        this.serviceResult = serviceResult;
    }

    @Override
    public List<ServiceAction> children() {
        return this.children;
    }

    @Override
    public long getTimeElapsed() {
        return timeElapsed;
    }

    @Override
    public void setTimeElapsed(long timeElapsed) {
        this.timeElapsed = timeElapsed;
    }

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
     * @return The found business exception or <code>ex</code> if none is found in the
     * cause chain.
     */
    protected Throwable findServiceException(Exception ex) {
        Throwable throwable = ex;
        while (throwable != null && throwable.getClass().getPackage().getName().startsWith("javax.ejb")) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
