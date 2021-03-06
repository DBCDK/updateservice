/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceEngineTest {
    private static final MetricsHandlerBean metricsHandlerBean = mock(MetricsHandlerBean.class);

    @Test
    void testExecuteAction_ActionIsNull() {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        assertThrows(IllegalArgumentException.class, () -> instance.executeAction(null));
    }

    @Test
    void testExecuteAction_ActionThrows() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction action = mock(ServiceAction.class);
        when(action.performAction()).thenThrow(new UpdateException("error"));
        assertThrows(UpdateException.class, () -> instance.executeAction(action));
    }

    @Test
    void testExecuteAction_ActionReturnsNull() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction action = mock(ServiceAction.class);
        when(action.performAction()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> instance.executeAction(action));
    }

    @Test
    void testExecuteAction_ActionReturnsErrors() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction action = mock(ServiceAction.class);
        when(action.performAction()).thenReturn(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error"));
        assertThat(instance.executeAction(action), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error")));
    }

    @Test
    void testExecuteAction_ThreeChildrenNoErrors() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction root = mock(ServiceAction.class);

        ServiceAction c1 = mock(ServiceAction.class);
        when(c1.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c1.children()).thenReturn(null);

        ServiceAction c2 = mock(ServiceAction.class);
        when(c2.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c2.children()).thenReturn(null);

        ServiceAction c3 = mock(ServiceAction.class);
        when(c3.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c3.children()).thenReturn(null);

        when(root.performAction()).thenReturn(ServiceResult.newOkResult());
        when(root.children()).thenReturn(Arrays.asList(c1, c2, c3));

        assertThat(instance.executeAction(root), is(ServiceResult.newOkResult()));

        verify(root).performAction();
        verify(root).children();
        verify(c1).performAction();
        verify(c1).children();
        verify(c2).performAction();
        verify(c2).children();
        verify(c3).performAction();
        verify(c3).children();
    }

    @Test
    void testExecuteAction_ThreeChildren_RootHasErrors() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction root = mock(ServiceAction.class);

        ServiceAction c1 = mock(ServiceAction.class);
        when(c1.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c1.children()).thenReturn(null);

        ServiceAction c2 = mock(ServiceAction.class);
        when(c2.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c2.children()).thenReturn(null);

        ServiceAction c3 = mock(ServiceAction.class);
        when(c3.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c3.children()).thenReturn(null);

        ServiceResult err = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error");
        when(root.performAction()).thenReturn(err);
        when(root.children()).thenReturn(Arrays.asList(c1, c2, c3));

        assertThat(instance.executeAction(root), is(err));

        verify(root).performAction();
        verify(root, never()).children();
        verify(c1, never()).performAction();
        verify(c1, never()).children();
        verify(c2, never()).performAction();
        verify(c2, never()).children();
        verify(c3, never()).performAction();
        verify(c3, never()).children();
    }

    @Test
    void testExecuteAction_ThreeChildren_RootHasWarnings() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction root = mock(ServiceAction.class);

        ServiceAction c1 = mock(ServiceAction.class);
        when(c1.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c1.children()).thenReturn(null);

        ServiceAction c2 = mock(ServiceAction.class);
        when(c2.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c2.children()).thenReturn(null);

        ServiceAction c3 = mock(ServiceAction.class);
        when(c3.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c3.children()).thenReturn(null);

        ServiceResult warn = ServiceResult.newWarningResult(UpdateStatusEnumDTO.OK, "warning");
        when(root.performAction()).thenReturn(warn);
        when(root.children()).thenReturn(Arrays.asList(c1, c2, c3));
        assertThat(instance.executeAction(root), is(warn));
        verify(root).performAction();
        verify(root).children();
        verify(c1).performAction();
        verify(c1).children();
        verify(c2).performAction();
        verify(c2).children();
        verify(c3).performAction();
        verify(c3).children();
    }

    @Test
    void testExecuteAction_ThreeChildren_MiddleChildHasErrors() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction root = mock(ServiceAction.class);

        ServiceAction c1 = mock(ServiceAction.class);
        when(c1.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c1.children()).thenReturn(null);

        ServiceResult err = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error");
        ServiceAction c2 = mock(ServiceAction.class);
        when(c2.performAction()).thenReturn(err);
        when(c2.children()).thenReturn(null);

        ServiceAction c3 = mock(ServiceAction.class);
        when(c3.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c3.children()).thenReturn(null);

        when(root.performAction()).thenReturn(ServiceResult.newOkResult());
        when(root.children()).thenReturn(Arrays.asList(c1, c2, c3));

        assertThat(instance.executeAction(root), is(err));

        verify(root).performAction();
        verify(root).children();
        verify(c1).performAction();
        verify(c1).children();
        verify(c2).performAction();
        verify(c2, never()).children();
        verify(c3, never()).performAction();
        verify(c3, never()).children();
    }

    @Test
    void testExecuteAction_ThreeChildren_FirstChildHasWarnings_SecondChildHasErrors() throws UpdateException, SolrException {
        ServiceEngine instance = new ServiceEngine(metricsHandlerBean);
        ServiceAction root = mock(ServiceAction.class);

        ServiceResult warn = ServiceResult.newWarningResult(UpdateStatusEnumDTO.OK, "warning");
        ServiceAction c1 = mock(ServiceAction.class);
        when(c1.performAction()).thenReturn(warn);
        when(c1.children()).thenReturn(null);

        ServiceResult err = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error");
        ServiceAction c2 = mock(ServiceAction.class);
        when(c2.performAction()).thenReturn(err);
        when(c2.children()).thenReturn(null);

        ServiceAction c3 = mock(ServiceAction.class);
        when(c3.performAction()).thenReturn(ServiceResult.newOkResult());
        when(c3.children()).thenReturn(null);

        when(root.performAction()).thenReturn(ServiceResult.newOkResult());
        when(root.children()).thenReturn(Arrays.asList(c1, c2, c3));

        ServiceResult expected = new ServiceResult();
        expected.setStatus(UpdateStatusEnumDTO.FAILED);
        expected.addServiceResult(warn);
        expected.addServiceResult(err);
        assertThat(instance.executeAction(root), is(expected));

        verify(root).performAction();
        verify(root).children();
        verify(c1).performAction();
        verify(c1).children();
        verify(c2).performAction();
        verify(c2, never()).children();
        verify(c3, never()).performAction();
        verify(c3, never()).children();
    }
}
