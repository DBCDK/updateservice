package dk.dbc.updateservice.javascript;

import dk.dbc.jslib.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * JavaScript engine factory to construct a new engine asynchronous.
 */
@Stateless
public class ScripterEnvironmentFactory {
    private static final XLogger logger = XLoggerFactory.getXLogger( ScripterEnvironmentFactory.class );

    private static final String COMMON_INSTALL_NAME = "common";
    private static final String ENTRYPOINTS_PATTERN = "%s/distributions/%s/src/entrypoints/update/%s";
    private static final String MODULES_PATH_PATTERN = "%s/distributions/%s/src";
    private static final String ENTRYPOINT_FILENAME = "entrypoint.js";

    /**
     * JNDI settings.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;

    /**
     * Constructs a new engine and adds is to the parsed pool.
     * <p>
     *    All errors are written to the log.
     * </p>
     *
     * @param settings JNDI-settings required to construct the engine.
     *
     * @return Future with a boolean value. <code>true</code> - the engine is created and added to the pool.
     * <code>false</code> - some error occurred.
     *
     */
    public ScripterEnvironment newEnvironment(Properties settings) throws ScripterException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch( "javascript.env.create" );

        ScripterEnvironment result = null;
        try {
            Environment environment = createEnvironment( settings );
            ScripterEnvironment scripterEnvironment = new ScripterEnvironment( environment );
            initTemplates( scripterEnvironment );

            return result = scripterEnvironment;
        }
        finally {
            watch.stop();
            logger.exit( result );
        }
    }

    private Environment createEnvironment( Properties settings ) throws ScripterException {
        logger.entry();

        try {
            String baseDir = settings.getProperty( JNDIResources.JAVASCRIPT_BASEDIR_KEY );
            String installName = settings.getProperty(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY);

            Environment envir = new Environment();
            envir.registerUseFunction( createModulesHandler( baseDir, installName ) );
            envir.evalFile( String.format( ENTRYPOINTS_PATTERN, baseDir, installName, ENTRYPOINT_FILENAME ) );

            return envir;
        }
        catch( Exception ex ) {
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

    void initTemplates( ScripterEnvironment environment ) throws ScripterException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch( "javascript.env.create.templates" );

        try {
            environment.callMethod( "initTemplates", settings );
        }
        finally {
            watch.stop();
            logger.exit();
        }
    }
}
