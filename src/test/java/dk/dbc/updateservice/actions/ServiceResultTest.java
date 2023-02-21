
package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This unittest tests that the ServiceReult class and methods can correctly merge different results
 */
class ServiceResultTest {

    @Test
    void ServiceResult_okResultAndNoDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult dpkServiceResult = ServiceResult.newOkResult();
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.OK));
        assertThat(result.getDoubleRecordKey(), is(nullValue()));
        assertThat(result.getEntries(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS(), is(nullValue()));
    }

    @Test
    void ServiceResult_okResultAndDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        DoubleRecordFrontendDTO doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsg);
        String pid = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        dpkServiceResult.setDoubleRecordKey(dpkKey);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));
        assertThat(result.getEntries(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS().size(), is(1));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getMessage(), is(errMsg));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getPid(), is(pid));
    }

    @Test
    void ServiceResult_okResultAndDoubleRecordFrontendErrors() {
        ServiceResult result = ServiceResult.newOkResult();

        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        result.setDoubleRecordKey(dpkKey);

        DoubleRecordFrontendDTO doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg1 = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsg1);
        String pid1 = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid1);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg2 = "Double record for record 5 158 076 1, reason: 021z";
        doubleRecordFrontendContent.setMessage(errMsg2);
        String pid2 = "3 158 076 2:870970";
        doubleRecordFrontendContent.setPid(pid2);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg3 = "Double record for record 5 158 076 1, reason: 042x";
        doubleRecordFrontendContent.setMessage(errMsg3);
        String pid3 = "3 158 076 3:870970";
        doubleRecordFrontendContent.setPid(pid3);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));
        assertThat(result.getEntries(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS().size(), is(3));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getMessage(), is(errMsg1));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getPid(), is(pid1));
        assertThat(result.getDoubleRecordFrontendDTOS().get(1).getMessage(), is(errMsg2));
        assertThat(result.getDoubleRecordFrontendDTOS().get(1).getPid(), is(pid2));
        assertThat(result.getDoubleRecordFrontendDTOS().get(2).getMessage(), is(errMsg3));
        assertThat(result.getDoubleRecordFrontendDTOS().get(2).getPid(), is(pid3));
    }

    @Test
    void ServiceResult_validateErrorAndNoDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        List<MessageEntryDTO> messageEntryDTOS = new ArrayList<>();
        String errMsgVal = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDTO.setMessage(errMsgVal);
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setOrdinalPositionInSubfield(1);
        messageEntryDTO.setOrdinalPositionOfField(2);
        messageEntryDTO.setOrdinalPositionOfSubfield(3);
        messageEntryDTOS.add(messageEntryDTO);
        validateServiceResult.addMessageEntryDtos(messageEntryDTOS);
        result.addServiceResult(validateServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(result.getDoubleRecordKey(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS(), is(nullValue()));
        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(1));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDTO.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));
    }

    @Test
    void ServiceResult_validateErrorsAndNoDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        List<MessageEntryDTO> messageEntryDTOS = new ArrayList<>();
        String errMsgVal1 = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDTO.setMessage(errMsgVal1);
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setOrdinalPositionInSubfield(1);
        messageEntryDTO.setOrdinalPositionOfField(2);
        messageEntryDTO.setOrdinalPositionOfSubfield(3);
        messageEntryDTOS.add(messageEntryDTO);

        String errMsgVal2 = "Følgende felt er til stede: '100' sammen med '110'";
        messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setMessage(errMsgVal2);
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setOrdinalPositionInSubfield(4);
        messageEntryDTO.setOrdinalPositionOfField(5);
        messageEntryDTO.setOrdinalPositionOfSubfield(6);
        messageEntryDTOS.add(messageEntryDTO);

        String errMsgVal3 = "Den er jo helt gal, er du stiv!";
        messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setMessage(errMsgVal3);
        messageEntryDTO.setType(TypeEnumDTO.FATAL);
        messageEntryDTO.setOrdinalPositionInSubfield(7);
        messageEntryDTO.setOrdinalPositionOfField(8);
        messageEntryDTO.setOrdinalPositionOfSubfield(9);
        messageEntryDTOS.add(messageEntryDTO);
        validateServiceResult.addMessageEntryDtos(messageEntryDTOS);
        result.addServiceResult(validateServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(result.getDoubleRecordKey(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS(), is(nullValue()));
        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(3));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal1));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDTO.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));

        assertThat(result.getEntries().get(1).getMessage(), is(errMsgVal2));
        assertThat(result.getEntries().get(1).getType(), is(TypeEnumDTO.ERROR));
        assertThat(result.getEntries().get(1).getOrdinalPositionInSubfield(), is(4));
        assertThat(result.getEntries().get(1).getOrdinalPositionOfField(), is(5));
        assertThat(result.getEntries().get(1).getOrdinalPositionOfSubfield(), is(6));

        assertThat(result.getEntries().get(2).getMessage(), is(errMsgVal3));
        assertThat(result.getEntries().get(2).getType(), is(TypeEnumDTO.FATAL));
        assertThat(result.getEntries().get(2).getOrdinalPositionInSubfield(), is(7));
        assertThat(result.getEntries().get(2).getOrdinalPositionOfField(), is(8));
        assertThat(result.getEntries().get(2).getOrdinalPositionOfSubfield(), is(9));
    }

    @Test
    void ServiceResult_validationErrorAndDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        List<MessageEntryDTO> messageEntryDTOS = new ArrayList<>();
        String errMsgVal = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDTO.setMessage(errMsgVal);
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setOrdinalPositionInSubfield(1);
        messageEntryDTO.setOrdinalPositionOfField(2);
        messageEntryDTO.setOrdinalPositionOfSubfield(3);
        messageEntryDTOS.add(messageEntryDTO);
        validateServiceResult.addMessageEntryDtos(messageEntryDTOS);
        result.addServiceResult(validateServiceResult);

        DoubleRecordFrontendDTO doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsgDpk = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsgDpk);
        String pid = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        dpkServiceResult.setDoubleRecordKey(dpkKey);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));
        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(1));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDTO.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));
        assertThat(result.getDoubleRecordFrontendDTOS(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS().size(), is(1));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getMessage(), is(errMsgDpk));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getPid(), is(pid));
    }

    @Test
    void ServiceResult_validationErrorsAndDoubleRecordFrontendErrors() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        List<MessageEntryDTO> messageEntryDTOS = new ArrayList<>();
        String errMsgVal1 = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDTO.setMessage(errMsgVal1);
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setOrdinalPositionInSubfield(1);
        messageEntryDTO.setOrdinalPositionOfField(2);
        messageEntryDTO.setOrdinalPositionOfSubfield(3);
        messageEntryDTOS.add(messageEntryDTO);

        String errMsgVal2 = "Følgende felt er til stede: '100' sammen med '110'";
        messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setMessage(errMsgVal2);
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setOrdinalPositionInSubfield(4);
        messageEntryDTO.setOrdinalPositionOfField(5);
        messageEntryDTO.setOrdinalPositionOfSubfield(6);
        messageEntryDTOS.add(messageEntryDTO);

        String errMsgVal3 = "Den er jo helt gal, er du stiv!";
        messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setMessage(errMsgVal3);
        messageEntryDTO.setType(TypeEnumDTO.FATAL);
        messageEntryDTO.setOrdinalPositionInSubfield(7);
        messageEntryDTO.setOrdinalPositionOfField(8);
        messageEntryDTO.setOrdinalPositionOfSubfield(9);
        messageEntryDTOS.add(messageEntryDTO);
        validateServiceResult.addMessageEntryDtos(messageEntryDTOS);
        result.addServiceResult(validateServiceResult);

        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        result.setDoubleRecordKey(dpkKey);

        DoubleRecordFrontendDTO doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg1 = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsg1);
        String pid1 = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid1);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg2 = "Double record for record 5 158 076 1, reason: 021z";
        doubleRecordFrontendContent.setMessage(errMsg2);
        String pid2 = "3 158 076 2:870970";
        doubleRecordFrontendContent.setPid(pid2);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDTO();
        String errMsg3 = "Double record for record 5 158 076 1, reason: 042x";
        doubleRecordFrontendContent.setMessage(errMsg3);
        String pid3 = "3 158 076 3:870970";
        doubleRecordFrontendContent.setPid(pid3);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendContent);
        dpkServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));

        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(3));

        assertMessageEntryDTO(result.getEntries().get(0), errMsgVal1, TypeEnumDTO.ERROR, 1,2,3);
        assertMessageEntryDTO(result.getEntries().get(1), errMsgVal2, TypeEnumDTO.ERROR, 4,5,6);
        assertMessageEntryDTO(result.getEntries().get(2), errMsgVal3, TypeEnumDTO.FATAL, 7,8,9);

        assertThat(result.getDoubleRecordFrontendDTOS(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDTOS().size(), is(3));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getMessage(), is(errMsg1));
        assertThat(result.getDoubleRecordFrontendDTOS().get(0).getPid(), is(pid1));
        assertThat(result.getDoubleRecordFrontendDTOS().get(1).getMessage(), is(errMsg2));
        assertThat(result.getDoubleRecordFrontendDTOS().get(1).getPid(), is(pid2));
        assertThat(result.getDoubleRecordFrontendDTOS().get(2).getMessage(), is(errMsg3));
        assertThat(result.getDoubleRecordFrontendDTOS().get(2).getPid(), is(pid3));
    }

    private void assertMessageEntryDTO(MessageEntryDTO messageEntryDTO,
                      String message,
                      TypeEnumDTO typeEnumDTO,
                      int ordinalPositionInSubfield,
                      int ordinalPositionOfField,
                      int ordinalPositionOfSubfield) {
        assertThat(messageEntryDTO.getMessage(), is(message));
        assertThat(messageEntryDTO.getType(), is(typeEnumDTO));
        assertThat(messageEntryDTO.getOrdinalPositionInSubfield(), is(ordinalPositionInSubfield));
        assertThat(messageEntryDTO.getOrdinalPositionOfField(), is(ordinalPositionOfField));
        assertThat(messageEntryDTO.getOrdinalPositionOfSubfield(), is(ordinalPositionOfSubfield));
    }
}
