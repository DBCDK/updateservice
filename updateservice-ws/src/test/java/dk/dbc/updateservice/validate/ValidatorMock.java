//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.javascript.Scripter;

import java.io.IOException;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidatorMock extends Validator {
    public ValidatorMock() {
        super();
    }

    public static Validator newInstance( String scriptResource ) throws IOException {
        Validator service = new ValidatorMock();

        Scripter scripter = new Scripter();
        // service.setScripter( scripter );

        return service;
    }
}
