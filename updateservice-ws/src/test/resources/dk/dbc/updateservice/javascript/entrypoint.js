use( "Print" );
use( "System" );
use( "UnitTest" );
use( "WriteFile" );

UnitTest.doTests = function() {
    return true;
};

// Include iScrum modules
use( "ClassificationData_UnitTests" );

// Including unittests modules

function main() {
	UnitTest.outputXml = true;		
	System.writeFile( "target/surefire-reports/TEST-JavaScriptTest.xml", UnitTest.report() + "\n" );		
	UnitTest.outputXml = false;
	
	print( UnitTest.report() + "\n" );	
	return UnitTest.totalFailed();
}
