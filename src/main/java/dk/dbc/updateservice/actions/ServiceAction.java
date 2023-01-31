/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.update.UpdateException;

import java.util.List;

/**
 * This interface defines the contract between the ServiceEngine and all the
 * actions that it can execute.
 * <p/>
 * ServiceAction is the root of all actions that can be executed by this web
 * service. It is part the a implementation of an extended command design
 * pattern.
 * <p/>
 * An service action has two responsibilities:
 * <ol>
 *     <li>
 *         Perform the steps in this service action <strong>and</strong> to
 *         add any additional (child) ServiceActions that is required to be executed
 *         before the engine continues to the next action of the parent of
 *         this action.
 *     </li>
 *     <li>
 *         Return the parent of this ServiceAction.
 *         <p/>
 *         The purpose of this responsibility is to let the engine be able to
 *         write a 'path' of this ServiceAction in case of errors.
 *     </li>
 *     <li>
 *         Returns a list of additional actions.
 *     </li>
 * </ol>
 * ServiceActions will basically end up to construct a tree that is proceeded as
 * it is constructed. Normally we construct a tree and then proceed it. This is done
 * by the ServiceEngine. The root of the tree is normally a ServiceAction that takes the
 * hole request from the web service and creates the required child actions to process
 * the request. It is up to the web service EJB to collect the results and produce a
 * valid response.
 *
 * <h3>Implementations</h3>
 *
 * Implementation classes should have any additional parameters that is required to
 * perform its actions parsed in its constructor or as setters. Its implementation of
 * <code>performAction</code> can then refer to these parameters thought class members.
 * <p/>
 * If the implementation has no logic by itself but only creates child actions, then
 * <code>performAction</code> will still be called. In this case <code>performAction</code>
 * should create and add any child actions that is needed to perform the required steps.
 */
public interface ServiceAction {
    String name();

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    ServiceResult performAction() throws UpdateException;

    ServiceResult getServiceResult();
    void setServiceResult( ServiceResult serviceResult );

    List<ServiceAction> children();

    void setupMDCContext();
    long getTimeElapsed();
    void setTimeElapsed( long timeElapsed );
}
