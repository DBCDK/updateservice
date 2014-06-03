/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package dk.dbc.updateservice.ws;

import dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType;
import javax.ejb.Stateless;
import javax.jws.WebService;

/**
 *
 * @author stp
 */
@WebService( serviceName = "CatalogingUpdateServices", portName = "CatalogingUpdatePort", endpointInterface = "dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType", targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate", wsdlLocation = "META-INF/wsdl/catalogingUpdate.wsdl" )
@Stateless
public class UpdateService implements CatalogingUpdatePortType {

    public dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult updateRecord( dk.dbc.oss.ns.catalogingupdate.UpdateRecordRequest updateRecordRequest ) {
        //TODO implement this method
        throw new UnsupportedOperationException( "Not implemented yet." );
    }

    public dk.dbc.oss.ns.catalogingupdate.GetValidateSchemasResult getValidateSchemas( dk.dbc.oss.ns.catalogingupdate.GetValidateSchemasRequest getValidateSchemasRequest ) {
        //TODO implement this method
        throw new UnsupportedOperationException( "Not implemented yet." );
    }
    
}
