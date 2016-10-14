package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.MessageEntryDto;
import dk.dbc.updateservice.dto.TypeEnumDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.update.DoubleRecordFrontendContent;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the result from a ServiceAction.
 * <p/>
 * The purpose of this class is to collect results from ServiceAction's so it
 * is possible to construct a valid webservice response for the updateRecord()
 * web service operation.
 * <p/>
 * Data entires:
 * <ol>
 * <li>Status</li>
 * <li>Service entries</li>
 * </ol>
 */
public class ServiceResult {
    private static final XLogger logger = XLoggerFactory.getXLogger(ServiceResult.class);

    private UpdateStatusEnumDto status = UpdateStatusEnumDto.OK;
    private List<MessageEntryDto> entries = null;
    private String doubleRecordKey = null;

    public UpdateStatusEnumDto getStatus() {
        return status;
    }

    public void setStatus(UpdateStatusEnumDto status) {
        this.status = status;
    }

    public List<MessageEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<MessageEntryDto> messageEntryDtos) {
        this.entries = messageEntryDtos;
    }

    public String getDoubleRecordKey() {
        return doubleRecordKey;
    }

    public void setDoubleRecordKey(String doubleRecordKey) {
        this.doubleRecordKey = doubleRecordKey;
    }

    public void addServiceResult(ServiceResult serviceResult) {
        if (serviceResult != null) {
            if (serviceResult.getEntries() != null) {
                if (entries == null) {
                    entries = new ArrayList<>();
                }
                entries.addAll(serviceResult.getEntries());
            }
            doubleRecordKey = serviceResult.getDoubleRecordKey();
            calculateAndAddUpdateStatusEnumDtoValue(serviceResult.getStatus());
            calculateAndAddUpdateStatusEnumDtoValue(serviceResult.getStatus());
        }
    }

    public void calculateAndAddUpdateStatusEnumDtoValue(UpdateStatusEnumDto updateStatusEnumDto) {
        if (updateStatusEnumDto != UpdateStatusEnumDto.OK) {
            status = updateStatusEnumDto;
        }
    }

    public void addMessageEntryDto(MessageEntryDto messageEntryDto) {
        if (messageEntryDto != null) {
            if (entries == null) {
                entries = new ArrayList<>();
            }
            entries.add(messageEntryDto);
        }
    }

    public void addMessageEntryDtos(List<MessageEntryDto> messageEntryDtos) {
        if (messageEntryDtos != null && !messageEntryDtos.isEmpty()) {
            if (this.entries == null) {
                this.entries = new ArrayList<>();
            }
            this.entries.addAll(messageEntryDtos);
        }
    }

    public List<MessageEntryDto> getServiceErrorList() {
        List<MessageEntryDto> entryErrors = null;
        if (entries != null) {
            for (MessageEntryDto entry : entries) {
                if (entry.getType() == TypeEnumDto.ERROR) {
                    if (entryErrors == null) {
                        entryErrors = new ArrayList<>();
                    }
                    entryErrors.add(entry);
                }
            }
        }
        return entryErrors;
    }

    public boolean hasErrors() {
        boolean res = false;
        if (entries != null) {
            for (MessageEntryDto entry : entries) {
                if (entry.getType() == TypeEnumDto.ERROR) {
                    res = true;
                }
            }
        }
        return res;
    }

    public static ServiceResult newOkResult() {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(UpdateStatusEnumDto.OK);
        return serviceResult;
    }


    public static ServiceResult newStatusResult(UpdateStatusEnumDto status) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        return serviceResult;
    }

    public static ServiceResult newAuthErrorResult(GlobalActionState globalActionState) {
        return newEntryResult(UpdateStatusEnumDto.FAILED, TypeEnumDto.ERROR, "Authentication error", globalActionState);
    }

    public static ServiceResult newAuthErrorResult(GlobalActionState globalActionState, String message) {
        return newEntryResult(UpdateStatusEnumDto.FAILED, TypeEnumDto.ERROR, message, globalActionState);
    }

    public static ServiceResult newErrorResult(UpdateStatusEnumDto status, String message, GlobalActionState globalActionState) {
        return newEntryResult(status, TypeEnumDto.ERROR, message, globalActionState);
    }

    public static ServiceResult newFatalResult(UpdateStatusEnumDto status, String message, GlobalActionState globalActionState) {
        return newEntryResult(status, TypeEnumDto.FATAL, message, globalActionState);
    }

    public static ServiceResult newWarningResult(UpdateStatusEnumDto status, String message, GlobalActionState globalActionState) {
        return newEntryResult(status, TypeEnumDto.WARNING, message, globalActionState);
    }

    public static ServiceResult newEntryResult(UpdateStatusEnumDto status, TypeEnumDto type, String message, GlobalActionState globalActionState) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        MessageEntryDto messageEntryDto = new MessageEntryDto();
        serviceResult.addMessageEntryDto(messageEntryDto);
        messageEntryDto.setType(type);
        messageEntryDto.setMessage(message);
        return serviceResult;
    }

    public static ServiceResult newDoubleRecordErrorResult(UpdateStatusEnumDto status, DoubleRecordFrontendContent doubleRecordFrontendContent, GlobalActionState globalActionState) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        MessageEntryDto messageEntryDto = new MessageEntryDto();
        serviceResult.addMessageEntryDto(messageEntryDto);
        messageEntryDto.setMessage(doubleRecordFrontendContent.getMessage());
        messageEntryDto.setPid(doubleRecordFrontendContent.getPid());
        return serviceResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceResult that = (ServiceResult) o;

        if (status != that.status) return false;
        if (entries != null ? !entries.equals(that.entries) : that.entries != null)
            return false;
        return doubleRecordKey != null ? doubleRecordKey.equals(that.doubleRecordKey) : that.doubleRecordKey == null;

    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (entries != null ? entries.hashCode() : 0);
        result = 31 * result + (doubleRecordKey != null ? doubleRecordKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServiceResult{" +
                "status=" + status +
                ", entries=" + entries +
                ", doubleRecordKey='" + doubleRecordKey + '\'' +
                '}';
    }
}
