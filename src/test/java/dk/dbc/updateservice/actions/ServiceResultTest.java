package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.DoubleRecordFrontendDto;
import dk.dbc.updateservice.dto.MessageEntryDto;
import dk.dbc.updateservice.dto.TypeEnumDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * This unittest tests that the ServiceReult class and methods can correctly merge different results
 */
public class ServiceResultTest {
    @Test
    public void ServiceResult_okResultAndNoDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult dpkServiceResult = ServiceResult.newOkResult();
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.OK));
        assertThat(result.getDoubleRecordKey(), is(nullValue()));
        assertThat(result.getEntries(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDtos(), is(nullValue()));
    }

    @Test
    public void ServiceResult_okResultAndDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        DoubleRecordFrontendDto doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsg);
        String pid = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        dpkServiceResult.setDoubleRecordKey(dpkKey);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));
        assertThat(result.getEntries(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDtos(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDtos().size(), is(1));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getMessage(), is(errMsg));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getPid(), is(pid));
    }

    @Test
    public void ServiceResult_okResultAndDoubleRecordFrontendErrors() {
        ServiceResult result = ServiceResult.newOkResult();

        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        result.setDoubleRecordKey(dpkKey);

        DoubleRecordFrontendDto doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg1 = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsg1);
        String pid1 = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid1);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg2 = "Double record for record 5 158 076 1, reason: 021z";
        doubleRecordFrontendContent.setMessage(errMsg2);
        String pid2 = "3 158 076 2:870970";
        doubleRecordFrontendContent.setPid(pid2);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg3 = "Double record for record 5 158 076 1, reason: 042x";
        doubleRecordFrontendContent.setMessage(errMsg3);
        String pid3 = "3 158 076 3:870970";
        doubleRecordFrontendContent.setPid(pid3);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));
        assertThat(result.getEntries(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDtos(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDtos().size(), is(3));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getMessage(), is(errMsg1));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getPid(), is(pid1));
        assertThat(result.getDoubleRecordFrontendDtos().get(1).getMessage(), is(errMsg2));
        assertThat(result.getDoubleRecordFrontendDtos().get(1).getPid(), is(pid2));
        assertThat(result.getDoubleRecordFrontendDtos().get(2).getMessage(), is(errMsg3));
        assertThat(result.getDoubleRecordFrontendDtos().get(2).getPid(), is(pid3));
    }

    @Test
    public void ServiceResult_validateErrorAndNoDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        MessageEntryDto messageEntryDto = new MessageEntryDto();
        List<MessageEntryDto> messageEntryDtos = new ArrayList<>();
        String errMsgVal = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDto.setMessage(errMsgVal);
        messageEntryDto.setType(TypeEnumDto.ERROR);
        messageEntryDto.setOrdinalPositionInSubfield(1);
        messageEntryDto.setOrdinalPositionOfField(2);
        messageEntryDto.setOrdinalPositionOfSubfield(3);
        messageEntryDtos.add(messageEntryDto);
        validateServiceResult.addMessageEntryDtos(messageEntryDtos);
        result.addServiceResult(validateServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.FAILED));
        assertThat(result.getDoubleRecordKey(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDtos(), is(nullValue()));
        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(1));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDto.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));
    }

    @Test
    public void ServiceResult_validateErrorsAndNoDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        MessageEntryDto messageEntryDto = new MessageEntryDto();
        List<MessageEntryDto> messageEntryDtos = new ArrayList<>();
        String errMsgVal1 = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDto.setMessage(errMsgVal1);
        messageEntryDto.setType(TypeEnumDto.ERROR);
        messageEntryDto.setOrdinalPositionInSubfield(1);
        messageEntryDto.setOrdinalPositionOfField(2);
        messageEntryDto.setOrdinalPositionOfSubfield(3);
        messageEntryDtos.add(messageEntryDto);

        String errMsgVal2 = "Følgende felt er til stede: '100' sammen med '110'";
        messageEntryDto = new MessageEntryDto();
        messageEntryDto.setMessage(errMsgVal2);
        messageEntryDto.setType(TypeEnumDto.ERROR);
        messageEntryDto.setOrdinalPositionInSubfield(4);
        messageEntryDto.setOrdinalPositionOfField(5);
        messageEntryDto.setOrdinalPositionOfSubfield(6);
        messageEntryDtos.add(messageEntryDto);

        String errMsgVal3 = "Den er jo helt gal, er du stiv!";
        messageEntryDto = new MessageEntryDto();
        messageEntryDto.setMessage(errMsgVal3);
        messageEntryDto.setType(TypeEnumDto.FATAL);
        messageEntryDto.setOrdinalPositionInSubfield(7);
        messageEntryDto.setOrdinalPositionOfField(8);
        messageEntryDto.setOrdinalPositionOfSubfield(9);
        messageEntryDtos.add(messageEntryDto);
        validateServiceResult.addMessageEntryDtos(messageEntryDtos);
        result.addServiceResult(validateServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.FAILED));
        assertThat(result.getDoubleRecordKey(), is(nullValue()));
        assertThat(result.getDoubleRecordFrontendDtos(), is(nullValue()));
        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(3));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal1));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDto.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));

        assertThat(result.getEntries().get(1).getMessage(), is(errMsgVal2));
        assertThat(result.getEntries().get(1).getType(), is(TypeEnumDto.ERROR));
        assertThat(result.getEntries().get(1).getOrdinalPositionInSubfield(), is(4));
        assertThat(result.getEntries().get(1).getOrdinalPositionOfField(), is(5));
        assertThat(result.getEntries().get(1).getOrdinalPositionOfSubfield(), is(6));

        assertThat(result.getEntries().get(2).getMessage(), is(errMsgVal3));
        assertThat(result.getEntries().get(2).getType(), is(TypeEnumDto.FATAL));
        assertThat(result.getEntries().get(2).getOrdinalPositionInSubfield(), is(7));
        assertThat(result.getEntries().get(2).getOrdinalPositionOfField(), is(8));
        assertThat(result.getEntries().get(2).getOrdinalPositionOfSubfield(), is(9));
    }

    @Test
    public void ServiceResult_validationErrorAndDoubleRecordFrontendError() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        MessageEntryDto messageEntryDto = new MessageEntryDto();
        List<MessageEntryDto> messageEntryDtos = new ArrayList<>();
        String errMsgVal = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDto.setMessage(errMsgVal);
        messageEntryDto.setType(TypeEnumDto.ERROR);
        messageEntryDto.setOrdinalPositionInSubfield(1);
        messageEntryDto.setOrdinalPositionOfField(2);
        messageEntryDto.setOrdinalPositionOfSubfield(3);
        messageEntryDtos.add(messageEntryDto);
        validateServiceResult.addMessageEntryDtos(messageEntryDtos);
        result.addServiceResult(validateServiceResult);

        DoubleRecordFrontendDto doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsgDpk = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsgDpk);
        String pid = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        dpkServiceResult.setDoubleRecordKey(dpkKey);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));
        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(1));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDto.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));
        assertThat(result.getDoubleRecordFrontendDtos(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDtos().size(), is(1));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getMessage(), is(errMsgDpk));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getPid(), is(pid));
    }

    @Test
    public void ServiceResult_validationErrorsAndDoubleRecordFrontendErrors() {
        ServiceResult result = ServiceResult.newOkResult();

        ServiceResult validateServiceResult = new ServiceResult();
        validateServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        MessageEntryDto messageEntryDto = new MessageEntryDto();
        List<MessageEntryDto> messageEntryDtos = new ArrayList<>();
        String errMsgVal1 = "Følgende felt er til stede: '110' sammen med '100'";
        messageEntryDto.setMessage(errMsgVal1);
        messageEntryDto.setType(TypeEnumDto.ERROR);
        messageEntryDto.setOrdinalPositionInSubfield(1);
        messageEntryDto.setOrdinalPositionOfField(2);
        messageEntryDto.setOrdinalPositionOfSubfield(3);
        messageEntryDtos.add(messageEntryDto);

        String errMsgVal2 = "Følgende felt er til stede: '100' sammen med '110'";
        messageEntryDto = new MessageEntryDto();
        messageEntryDto.setMessage(errMsgVal2);
        messageEntryDto.setType(TypeEnumDto.ERROR);
        messageEntryDto.setOrdinalPositionInSubfield(4);
        messageEntryDto.setOrdinalPositionOfField(5);
        messageEntryDto.setOrdinalPositionOfSubfield(6);
        messageEntryDtos.add(messageEntryDto);

        String errMsgVal3 = "Den er jo helt gal, er du stiv!";
        messageEntryDto = new MessageEntryDto();
        messageEntryDto.setMessage(errMsgVal3);
        messageEntryDto.setType(TypeEnumDto.FATAL);
        messageEntryDto.setOrdinalPositionInSubfield(7);
        messageEntryDto.setOrdinalPositionOfField(8);
        messageEntryDto.setOrdinalPositionOfSubfield(9);
        messageEntryDtos.add(messageEntryDto);
        validateServiceResult.addMessageEntryDtos(messageEntryDtos);
        result.addServiceResult(validateServiceResult);

        String dpkKey = "35bcb78b-7309-4aee-800a-8a62930309b6";
        result.setDoubleRecordKey(dpkKey);

        DoubleRecordFrontendDto doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg1 = "Double record for record 5 158 076 1, reason: 021e, 021e";
        doubleRecordFrontendContent.setMessage(errMsg1);
        String pid1 = "3 158 076 1:870970";
        doubleRecordFrontendContent.setPid(pid1);
        ServiceResult dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg2 = "Double record for record 5 158 076 1, reason: 021z";
        doubleRecordFrontendContent.setMessage(errMsg2);
        String pid2 = "3 158 076 2:870970";
        doubleRecordFrontendContent.setPid(pid2);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        result.addServiceResult(dpkServiceResult);

        doubleRecordFrontendContent = new DoubleRecordFrontendDto();
        String errMsg3 = "Double record for record 5 158 076 1, reason: 042x";
        doubleRecordFrontendContent.setMessage(errMsg3);
        String pid3 = "3 158 076 3:870970";
        doubleRecordFrontendContent.setPid(pid3);
        dpkServiceResult = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendContent, null);
        dpkServiceResult.setStatus(UpdateStatusEnumDto.FAILED);
        result.addServiceResult(dpkServiceResult);

        assertThat(result.getStatus(), is(UpdateStatusEnumDto.FAILED));
        assertThat(result.getDoubleRecordKey(), is(dpkKey));

        assertThat(result.getEntries(), is(notNullValue()));
        assertThat(result.getEntries().size(), is(3));
        assertThat(result.getEntries().get(0).getMessage(), is(errMsgVal1));
        assertThat(result.getEntries().get(0).getType(), is(TypeEnumDto.ERROR));
        assertThat(result.getEntries().get(0).getOrdinalPositionInSubfield(), is(1));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfField(), is(2));
        assertThat(result.getEntries().get(0).getOrdinalPositionOfSubfield(), is(3));

        assertThat(result.getEntries().get(1).getMessage(), is(errMsgVal2));
        assertThat(result.getEntries().get(1).getType(), is(TypeEnumDto.ERROR));
        assertThat(result.getEntries().get(1).getOrdinalPositionInSubfield(), is(4));
        assertThat(result.getEntries().get(1).getOrdinalPositionOfField(), is(5));
        assertThat(result.getEntries().get(1).getOrdinalPositionOfSubfield(), is(6));

        assertThat(result.getEntries().get(2).getMessage(), is(errMsgVal3));
        assertThat(result.getEntries().get(2).getType(), is(TypeEnumDto.FATAL));
        assertThat(result.getEntries().get(2).getOrdinalPositionInSubfield(), is(7));
        assertThat(result.getEntries().get(2).getOrdinalPositionOfField(), is(8));
        assertThat(result.getEntries().get(2).getOrdinalPositionOfSubfield(), is(9));

        assertThat(result.getDoubleRecordFrontendDtos(), is(notNullValue()));
        assertThat(result.getDoubleRecordFrontendDtos().size(), is(3));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getMessage(), is(errMsg1));
        assertThat(result.getDoubleRecordFrontendDtos().get(0).getPid(), is(pid1));
        assertThat(result.getDoubleRecordFrontendDtos().get(1).getMessage(), is(errMsg2));
        assertThat(result.getDoubleRecordFrontendDtos().get(1).getPid(), is(pid2));
        assertThat(result.getDoubleRecordFrontendDtos().get(2).getMessage(), is(errMsg3));
        assertThat(result.getDoubleRecordFrontendDtos().get(2).getPid(), is(pid3));
    }
}
