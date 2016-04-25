//-----------------------------------------------------------------------------
package dk.dbc.updateservice.rest;

//-----------------------------------------------------------------------------
import javax.ws.rs.core.Application;
import java.util.Set;

//-----------------------------------------------------------------------------
/**
 * Created by stp on 25/11/15.
 */
@javax.ws.rs.ApplicationPath( "rest" )
public class ApplicationConfig extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new java.util.HashSet<>();
        addRestResourceClasses( resources );
        return resources;
    }

    /**
     * Do not modify addRestResourceClasses() method.
     * It is automatically populated with
     * all resources defined in the project.
     * If required, comment out calling this method in getClasses().
     */
    private void addRestResourceClasses( Set<Class<?>> resources) {
        resources.add( StatusService.class );
    }}
