package dk.dbc.common.records;

import dk.dbc.common.records.MarcSubField;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.notNullValue;

public class MarcSubFieldTest {
    @Test
    public void testCopyConstructor() throws Exception {
        MarcSubField sf1 = new MarcSubField( "a", "v" );
        MarcSubField sf2 = new MarcSubField( sf1 );
        assertThat( sf2, notNullValue() );
        assertThat( sf1, equalTo( sf2 ) );
    }

    @Test
    public void testEquals_EqualName_EqualValue() throws Exception {
        MarcSubField sf1 = new MarcSubField( "a", "v" );
        MarcSubField sf2 = new MarcSubField( "a", "v" );
        assertThat( sf1, equalTo( sf2 ) );
    }

    @Test
    public void testEquals_EqualName_NotEqualValue() throws Exception {
        MarcSubField sf1 = new MarcSubField( "a", "v1" );
        MarcSubField sf2 = new MarcSubField( "a", "v2" );
        assertThat( sf1, not( sf2 ) );
    }

    @Test
    public void testEquals_NotEqualName_EqualValue() throws Exception {
        MarcSubField sf1 = new MarcSubField( "a", "v" );
        MarcSubField sf2 = new MarcSubField( "b", "v" );
        assertThat( sf1, not( sf2 ) );
    }

    @Test
    public void testEquals_NotEqualName_NotEqualValue() throws Exception {
        MarcSubField sf1 = new MarcSubField( "a", "v1" );
        MarcSubField sf2 = new MarcSubField( "b", "v2" );
        assertThat( sf1, not( sf2 ) );
    }
}
