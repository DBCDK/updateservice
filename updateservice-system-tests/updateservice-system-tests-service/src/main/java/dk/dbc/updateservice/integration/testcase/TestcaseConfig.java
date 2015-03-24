package dk.dbc.updateservice.integration.testcase;

import dk.dbc.updateservice.service.api.Authentication;

import java.util.Map;

/**
 * Created by stp on 24/02/15.
 */
public class TestcaseConfig {
    public TestcaseConfig() {
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName( String templateName ) {
        this.templateName = templateName;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication( Authentication authentication ) {
        this.authentication = authentication;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders( Map<String, Object> headers ) {
        this.headers = headers;
    }

    private String templateName;
    private Authentication authentication;
    private Map<String, Object> headers;
}
