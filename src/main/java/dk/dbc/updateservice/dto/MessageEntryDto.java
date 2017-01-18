package dk.dbc.updateservice.dto;

public class MessageEntryDto {
    private TypeEnumDto type;
    private String code;
    private String urlForDocumentation;
    private Integer ordinalPositionOfField;
    private Integer ordinalPositionOfSubfield;
    private Integer ordinalPositionInSubfield;
    private String message;

    public TypeEnumDto getType() {
        return type;
    }

    public void setType(TypeEnumDto typeEnumDto) {
        this.type = typeEnumDto;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUrlForDocumentation() {
        return urlForDocumentation;
    }

    public void setUrlForDocumentation(String urlForDocumentation) {
        this.urlForDocumentation = urlForDocumentation;
    }

    public Integer getOrdinalPositionOfField() {
        return ordinalPositionOfField;
    }

    public void setOrdinalPositionOfField(Integer ordinalPositionOfField) {
        this.ordinalPositionOfField = ordinalPositionOfField;
    }

    public Integer getOrdinalPositionOfSubfield() {
        return ordinalPositionOfSubfield;
    }

    public void setOrdinalPositionOfSubfield(Integer ordinalPositionOfSubfield) {
        this.ordinalPositionOfSubfield = ordinalPositionOfSubfield;
    }

    public Integer getOrdinalPositionInSubfield() {
        return ordinalPositionInSubfield;
    }

    public void setOrdinalPositionInSubfield(Integer ordinalPositionInSubfield) {
        this.ordinalPositionInSubfield = ordinalPositionInSubfield;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageEntryDto that = (MessageEntryDto) o;

        if (type != that.type) return false;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        if (urlForDocumentation != null ? !urlForDocumentation.equals(that.urlForDocumentation) : that.urlForDocumentation != null)
            return false;
        if (ordinalPositionOfField != null ? !ordinalPositionOfField.equals(that.ordinalPositionOfField) : that.ordinalPositionOfField != null)
            return false;
        if (ordinalPositionOfSubfield != null ? !ordinalPositionOfSubfield.equals(that.ordinalPositionOfSubfield) : that.ordinalPositionOfSubfield != null)
            return false;
        if (ordinalPositionInSubfield != null ? !ordinalPositionInSubfield.equals(that.ordinalPositionInSubfield) : that.ordinalPositionInSubfield != null)
            return false;
        return message != null ? message.equals(that.message) : that.message == null;

    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (urlForDocumentation != null ? urlForDocumentation.hashCode() : 0);
        result = 31 * result + (ordinalPositionOfField != null ? ordinalPositionOfField.hashCode() : 0);
        result = 31 * result + (ordinalPositionOfSubfield != null ? ordinalPositionOfSubfield.hashCode() : 0);
        result = 31 * result + (ordinalPositionInSubfield != null ? ordinalPositionInSubfield.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MessageEntryDto{" +
                "type=" + type +
                ", code='" + code + '\'' +
                ", urlForDocumentation='" + urlForDocumentation + '\'' +
                ", ordinalPositionOfField=" + ordinalPositionOfField +
                ", ordinalPositionOfSubfield=" + ordinalPositionOfSubfield +
                ", ordinalPositionInSubfield=" + ordinalPositionInSubfield +
                ", message='" + message + '\'' +
                '}';
    }
}
