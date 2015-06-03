//-----------------------------------------------------------------------------
use( "UnitTest" );
use( "SafeAssert" );
use( "UpperCaseCheck" );
//-----------------------------------------------------------------------------

UnitTest.addFixture( "UpperCaseCheck", function( ) {
    var bundle = ResourceBundleFactory.getBundle( FieldRules.BUNDLE_NAME );

    var message = '';
    var params = {};
    var record = {};
    var fieldAa = {
        "name" : '003', "indicator" : '00', subfields : [{
            'name' : "A", 'value' : "42"
        }, {
            'name' : "a", 'value' : "42"
        }]
    };

    SafeAssert.equal( "1 FieldRules.upperCaseCheck value", UpperCaseCheck.validateFields( record, fieldAa ), [] );

    var fieldAb = {
        "name" : '003', "indicator" : '00', subfields : [{
            'name' : "A", 'value' : "42"
        }, {
            'name' : "b", 'value' : "42"
        }]
    };

    message = ResourceBundle.getStringFormat( bundle, "uppercase.rule.error", "A", "a", "003" );
    var errNoLowerCaseA = [ValidateErrors.fieldError( 'TODO:fixurl', message )];
    SafeAssert.equal( "2 UpperCaseCheck.validateFields value", UpperCaseCheck.validateFields( record, fieldAb ), errNoLowerCaseA );

    var fieldaA = {
        "name" : '003', "indicator" : '00', subfields : [{
            'name' : "a", 'value' : "42"
        }, {
            'name' : "A", 'value' : "42"
        }]
    };

    message = ResourceBundle.getStringFormat( bundle, "uppercase.rule.error", "A", "a", "003" );
    var errNoTrailingA = [ValidateErrors.fieldError( 'TODO:fixurl', message )];
    SafeAssert.equal( "3 UpperCaseCheck.validateFields value", UpperCaseCheck.validateFields( record, fieldaA ), errNoTrailingA );
} );