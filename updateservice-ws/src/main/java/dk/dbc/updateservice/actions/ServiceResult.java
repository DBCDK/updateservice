//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.service.api.Error;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.service.api.ValidateEntry;
import dk.dbc.updateservice.service.api.ValidateWarningOrErrorEnum;
import dk.dbc.updateservice.ws.ValidationError;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
 *     <li>Status</li>
 *     <li>Service Errors</li>
 *     <li>Validation entries</li>
 * </ol>
 */
public class ServiceResult {
    public ServiceResult() {
        this.status = null;
        this.serviceError = null;
        this.entries = new ArrayList<>();
    }

    public ServiceResult( ServiceResult other ) {
        this.status = other.status;
        this.serviceError = other.serviceError;

        this.entries = new ArrayList<>();
        for( int i = 0; i < other.entries.size(); i++ ) {
            ValidateEntry otherEntry = other.entries.get( i );

            ValidateEntry entry = new ValidateEntry();
            entry.setWarningOrError( otherEntry.getWarningOrError() );
            entry.setUrlForDocumentation( otherEntry.getUrlForDocumentation() );
            entry.setOrdinalPositionOfField( otherEntry.getOrdinalPositionOfField() );
            entry.setOrdinalPositionOfSubField( otherEntry.getOrdinalPositionOfSubField() );
            entry.setMessage( otherEntry.getMessage() );

            this.entries.add( entry );
        }
    }

    //-------------------------------------------------------------------------
    //              Properties
    //-------------------------------------------------------------------------

    public UpdateStatusEnum getStatus() {
        return status;
    }

    public void setStatus( UpdateStatusEnum status ) {
        this.status = status;
    }

    public Error getServiceError() {
        return serviceError;
    }

    public void setServiceError( Error serviceError ) {
        this.serviceError = serviceError;
    }

    public List<ValidateEntry> getEntries() {
        return entries;
    }

    //-------------------------------------------------------------------------
    //              Validation entries
    //-------------------------------------------------------------------------

    public void addEntries( ServiceResult serviceResult ) {
        this.entries.addAll( serviceResult.getEntries() );
    }

    public void addEntries( List<ValidationError> entries ) {
        logger.entry();

        try {
            for( ValidationError validationError : entries ) {
                addEntry( validationError );
            }
        }
        finally {
            logger.exit();
        }
    }

    public void addEntry( ValidateEntry entry ) {
        this.entries.add( entry );
    }

    public void addEntry( ValidationError entry ) {
        logger.entry();

        try {
            ValidateEntry validateEntry = new ValidateEntry();

            HashMap<String, Object> params = entry.getParams();
            Object value;

            validateEntry.setWarningOrError( entry.getType() );

            value = params.get("url");
            if (value != null) {
                validateEntry.setUrlForDocumentation( value.toString() );
            }

            value = params.get("message");
            if (value != null) {
                validateEntry.setMessage( value.toString() );
            }

            value = params.get("fieldno");
            if (value != null) {
                validateEntry.setOrdinalPositionOfField( new BigDecimal( value.toString() ).toBigInteger() );
            }

            value = params.get("subfieldno");
            if (value != null) {
                validateEntry.setOrdinalPositionOfSubField( new BigDecimal( value.toString() ).toBigInteger() );
            }

            this.entries.add( validateEntry );
        }
        finally {
            logger.exit();
        }
    }

    public boolean hasErrors() {
        logger.entry();

        try {
            for( ValidateEntry entry : this.entries ) {
                if( entry.getWarningOrError() == ValidateWarningOrErrorEnum.ERROR ) {
                    return true;
                }
            }

            return false;
        }
        finally {
            logger.exit();
        }

    }

    //-------------------------------------------------------------------------
    //              Object
    //-------------------------------------------------------------------------

    @Override
    public boolean equals( Object o ) {
        if( this == o ) {
            return true;
        }
        if( o == null || getClass() != o.getClass() ) {
            return false;
        }

        ServiceResult that = (ServiceResult) o;

        if( status != that.status) {
            return false;
        }
        if( serviceError != null ? !serviceError.equals( that.serviceError ) : that.serviceError != null ) {
            return false;
        }

        //---------------------------------------------------------------------
        //              Compare entries
        //---------------------------------------------------------------------

        if( that.entries == null ) {
            return false;
        }

        if( entries.getClass() != that.entries.getClass() ) {
            return false;
        }

        if( entries.size() != that.entries.size() ) {
            return false;
        }

        for( int i = 0; i < entries.size(); i++ ) {
            ValidateEntry a = entries.get( i );
            ValidateEntry b = that.entries.get( i );

            if( a == null ) {
                if( b == null ) {
                    continue;
                }

                return false;
            }

            if( !validateEntryEquals( a, b ) ) {
                return false;
            }
        }

        return true;
    }

    private boolean validateEntryEquals( ValidateEntry a, ValidateEntry b ) {
        if( a == b ) {
            return true;
        }
        if( b == null || a.getClass() != b.getClass() ) {
            return false;
        }

        if( a.getWarningOrError() != b.getWarningOrError() ) {
            return false;
        }
        if( a.getUrlForDocumentation() != null ? !a.getUrlForDocumentation().equals( b.getUrlForDocumentation() ) : b.getUrlForDocumentation() != null ) {
            return false;
        }
        if( a.getOrdinalPositionOfField() != null ? !a.getOrdinalPositionOfField().equals( b.getOrdinalPositionOfField() ) : b.getOrdinalPositionOfField() != null ) {
            return false;
        }
        if( a.getOrdinalPositionOfSubField() != null ? !a.getOrdinalPositionOfSubField().equals( b.getOrdinalPositionOfSubField() ) : b.getOrdinalPositionOfSubField() != null ) {
            return false;
        }
        if( a.getMessage() != null ? !a.getMessage().equals( b.getMessage() ) : b.getMessage() != null ) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + ( serviceError != null ? serviceError.hashCode() : 0 );
        result = 31 * result + ( entries != null ? entries.hashCode() : 0 );
        return result;
    }

    @Override
    public String toString() {
        String result = "ServiceResult{" +
                "status=" + status +
                ", serviceError=" + serviceError +
                ", entries=[";

        boolean first = true;
        for( ValidateEntry entry : entries ) {
            if( !first ) {
                result += ',';
            }
            result += "ValidateEntry{" +
                        "warningOrError=" + entry.getWarningOrError() +
                        ", urlForDocumentation='" + entry.getUrlForDocumentation() + '\'' +
                        ", ordinalPositionOfField=" + entry.getOrdinalPositionOfField() +
                        ", ordinalPositionOfSubField=" + entry.getOrdinalPositionOfSubField() +
                        ", message='" + entry.getMessage() + '\'' +
                        '}';

            first = false;
        }
        result += "]}";
        return result;
    }

    //-------------------------------------------------------------------------
    //              Factory methods
    //-------------------------------------------------------------------------

    public static ServiceResult newOkResult() {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus( UpdateStatusEnum.OK );

        return serviceResult;
    }

    public static ServiceResult newValidateOnlyResult() {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus( UpdateStatusEnum.VALIDATE_ONLY );

        return serviceResult;
    }

    public static ServiceResult newStatusResult( UpdateStatusEnum status ) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus( status );

        return serviceResult;
    }

    public static ServiceResult newAuthErrorResult() {
        ServiceResult serviceResult = newOkResult();
        serviceResult.setServiceError( Error.AUTHENTICATION_ERROR );

        return serviceResult;
    }

    public static ServiceResult newErrorResult( UpdateStatusEnum status, String message ) {
        return newEntryResult( status, ValidateWarningOrErrorEnum.ERROR, message );
    }

    public static ServiceResult newWarningResult( UpdateStatusEnum status, String message ) {
        return newEntryResult( status, ValidateWarningOrErrorEnum.WARNING, message );
    }

    public static ServiceResult newEntryResult( UpdateStatusEnum status, ValidateWarningOrErrorEnum entryType, String message ) {
        ServiceResult serviceResult = new ServiceResult();
        serviceResult.setStatus( status );

        ValidateEntry entry = new ValidateEntry();
        entry.setWarningOrError( entryType );
        entry.setMessage( message );
        serviceResult.addEntry( entry );

        return serviceResult;
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( ServiceResult.class );

    private UpdateStatusEnum status;
    private Error serviceError;
    private List<ValidateEntry> entries;
}
