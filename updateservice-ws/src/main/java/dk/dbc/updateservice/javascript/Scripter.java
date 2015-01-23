//-----------------------------------------------------------------------------
package dk.dbc.updateservice.javascript;

//-----------------------------------------------------------------------------

import dk.dbc.jslib.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.mozilla.javascript.JavaScriptException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * Created by stp on 03/12/14.
 */
@Stateless
@LocalBean
public class Scripter {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public Scripter() {
        this.settings = null;
    }

    public Scripter( Properties settings ) {
        this.settings = settings;
    }

    //-------------------------------------------------------------------------
    //              Script interface
    //-------------------------------------------------------------------------

    /**
     *
     *
     * @param fileName
     * @param methodName
     * @param args
     *
     * @return
     *
     * @throws ScripterException
     */
    public Object callMethod( String fileName, String methodName, Object... args ) throws ScripterException {
        logger.entry( fileName, methodName, args );

        Object result = null;
        try {
            if( !environments.containsKey( fileName ) ) {
                environments.put( fileName, createEnvironment( fileName ) );
            }

            Environment envir = environments.get( fileName );
            result = envir.callMethod( methodName, args );

            return result;
        }
        catch( JavaScriptException ex ) {
            throw new ScripterException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

    private Environment createEnvironment( String fileName ) throws ScripterException {
        logger.entry( fileName );

        try {
            String baseDir = settings.getProperty( JNDIResources.JAVASCRIPT_BASEDIR_KEY );
            String installName = settings.getProperty(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY);

            Environment envir = new Environment();
            envir.registerUseFunction( createModulesHandler( baseDir, installName ) );
            envir.evalFile( String.format( ENTRYPOINTS_PATTERN, baseDir, installName, fileName ) );

            return envir;
        }
        catch( IOException ex ) {
            throw new ScripterException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    private ModuleHandler createModulesHandler( String baseDir, String installName ) {
        logger.entry();

        try {
            ModuleHandler handler = new ModuleHandler();
            String modulesDir;

            modulesDir = String.format( MODULES_PATH_PATTERN, baseDir, installName );
            handler.registerHandler( "file", new FileSchemeHandler( modulesDir ) );
            addSearchPathsFromSettingsFile( handler, "file", modulesDir );

            modulesDir = String.format( MODULES_PATH_PATTERN, baseDir, COMMON_INSTALL_NAME );
            handler.registerHandler( COMMON_INSTALL_NAME, new FileSchemeHandler( modulesDir ) );
            addSearchPathsFromSettingsFile( handler, COMMON_INSTALL_NAME, modulesDir );

            handler.registerHandler( "classpath", new ClasspathSchemeHandler( this.getClass().getClassLoader() ) );
            addSearchPathsFromSettingsFile( handler, "classpath", getClass().getResourceAsStream( "jsmodules.settings" ) );

            return handler;
        }
        catch( IOException ex ) {
            logger.warn( "Unable to load properties from resource 'jsmodules.settings'" );
            logger.error( ex.getMessage(), ex );

            return null;
        }
        finally {
            logger.exit();
        }
    }

    private void addSearchPathsFromSettingsFile( ModuleHandler handler, String schemeName, String modulesDir ) {
        logger.entry( handler, schemeName, modulesDir );

        String fileName = modulesDir + "/settings.properties";
        try {
            File file = new File( fileName );

            addSearchPathsFromSettingsFile( handler, schemeName, new FileInputStream( file ) );
        }
        catch( FileNotFoundException ex ) {
            logger.warn( "The file '{}' does not exist.", fileName );
            return;
        }
        catch( IOException ex ) {
            logger.warn( "Unable to load properties from file '{}'", fileName );
            logger.error( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    private void addSearchPathsFromSettingsFile( ModuleHandler handler, String schemeName, InputStream is ) throws IOException {
        logger.entry( handler, schemeName, is );

        try {
            Properties props = new Properties();
            props.load( is );

            if( !props.containsKey( "modules.search.path" ) ) {
                logger.warn( "Search path for modules is not specified" );
                return;
            }

            String moduleSearchPathString = props.getProperty( "modules.search.path" );
            if( moduleSearchPathString != null && !moduleSearchPathString.isEmpty() ) {
                String[] moduleSearchPath = moduleSearchPathString.split( ";" );
                for( String s : moduleSearchPath ) {
                    handler.addSearchPath( new SchemeURI( schemeName + ":" + s ) );
                }
            }
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( Scripter.class );

    private static final String COMMON_INSTALL_NAME = "common";
    private static final String ENTRYPOINTS_PATTERN = "%s/distributions/%s/src/entrypoints/update/%s";
    private static final String MODULES_PATH_PATTERN = "%s/distributions/%s/src";

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;

    /**
     * @brief Map of Environment for our Rhino JavaScript engine.
     */
    private Map<String, Environment> environments = new HashMap<>();
}
