package dk.dbc.updateservice.dto;

import java.util.List;

public class RecordDataDto {
    private List<Object> content;

    public List<Object> getContent() {
        return content;
    }

    public void setContent(List<Object> content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RecordDataDto that = (RecordDataDto) o;

        return content != null ? content.equals(that.content) : that.content == null;

    }

    @Override
    public int hashCode() {
        return content != null ? content.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RecordDataDto{" +
                "content=" + content +
                '}';
    }
}
