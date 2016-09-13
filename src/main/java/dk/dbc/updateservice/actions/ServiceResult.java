package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.service.api.Entry;
import dk.dbc.updateservice.service.api.Param;
import dk.dbc.updateservice.service.api.Params;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
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

    private UpdateStatusEnum status = null;
    private List<Entry> entries = new ArrayList<>();
    private String doubleRecordKey = null;
    private String type = null;

    public ServiceResult() {
        status = null;
    }

    public UpdateStatusEnum getStatus() {
        return status;
    }

    public void setStatus(UpdateStatusEnum status) {
        this.status = status;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public String getDoubleRecordKey() {
        return doubleRecordKey;
    }

    public void setDoubleRecordKey(String doubleRecordKey) {
        this.doubleRecordKey = doubleRecordKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public void addEntries(ServiceResult serviceResult) {
        entries.addAll(serviceResult.getEntries());
        doubleRecordKey = serviceResult.getDoubleRecordKey();
        type = serviceResult.getType();
    }

    public List<Entry> getServiceErrorList() {
        List<Entry> entryErrors = null;
        for (Entry entry : entries) {
            if (entry.getType() == Type.ERROR) {
                if (entryErrors == null) {
                    entryErrors = new ArrayList<>();
                }
                entryErrors.add(entry);
            }
        }
        return entryErrors;
    }

    public boolean hasErrors() {
        logger.entry();
        try {
            for (Entry entry : entries) {
                if (entry.getType() == Type.ERROR) {
                    return true;
                }
            }
            return false;
        } finally {
            logger.exit();
        }

    }

    public static ServiceResult newOkResult() {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(UpdateStatusEnum.OK);
        return serviceResult;
    }


    public static ServiceResult newStatusResult(UpdateStatusEnum status) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        return serviceResult;
    }

    public static ServiceResult newAuthErrorResult(GlobalActionState globalActionState) {
        return newEntryResult(UpdateStatusEnum.FAILED, Type.ERROR, "Authentication error", globalActionState);
    }

    public static ServiceResult newErrorResult(UpdateStatusEnum status, String message, GlobalActionState globalActionState) {
        return newEntryResult(status, Type.ERROR, message, globalActionState);
    }

    public static ServiceResult newDoubleRecordErrorResult(UpdateStatusEnum status, String message, GlobalActionState globalActionState) {
        return newEntryResult(status, Type.DOUBLE_RECORD, message, globalActionState);
    }

    public static ServiceResult newWarningResult(UpdateStatusEnum status, String message, GlobalActionState globalActionState) {
        return newEntryResult(status, Type.WARNING, message, globalActionState);
    }

    public static ServiceResult newEntryResult(UpdateStatusEnum status, Type type, String message, GlobalActionState globalActionState) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus(status);
        Entry entry = new Entry();
        serviceResult.getEntries().add(entry);
        entry.setType(type);
        Params params = new Params();
        entry.setParams(params);
        Param param = new Param();
        params.getParam().add(param);
        param.setKey("message");
        param.setValue(message);
        if (globalActionState != null) {
            param = new Param();
            params.getParam().add(param);
            param.setKey("pid");
            param.setValue(globalActionState.getRecordPid());
        }
        return serviceResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceResult that = (ServiceResult) o;

        if (status != that.status) return false;
        return entries != null ? entries.equals(that.entries) : that.entries == null;
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (entries != null ? entries.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        String result = "ServiceResult{" +
                "status=" + status +
                ", doubleRecordKey=" + doubleRecordKey +
                ", entries=[";
        if (entries.isEmpty()) {
            result += "null";
        } else {
            boolean outerFirst = true;
            for (Entry entry : entries) {
                if (!outerFirst) {
                    result += ", ";
                }
                result += "Entry{";
                result += "code=" + entry.getCode();
                result += ", type=" + entry.getType();
                result += ", params=[";
                if (entry.getParams() == null) {
                    result += "null";
                } else {
                    boolean innerFirst = true;
                    for (Param param : entry.getParams().getParam()) {
                        if (!innerFirst) {
                            result += ',';
                        }
                        result += "Param{key=" + param.getKey();
                        result += ", value=\'" + param.getValue() + "\'}";
                        innerFirst = false;
                    }
                }
                result += "}";
                outerFirst = false;
            }
        }
        result += "]}";
        return result;
    }
}
