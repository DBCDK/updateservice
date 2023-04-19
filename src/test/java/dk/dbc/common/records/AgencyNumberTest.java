package dk.dbc.common.records;

import dk.dbc.common.records.AgencyNumber;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class AgencyNumberTest {
    @Test
    public void testIntegerNumbers() {
        AgencyNumber instance = new AgencyNumber(100);
        assertThat(instance.getAgencyId(), equalTo(100));
        assertThat(instance.toString(), equalTo("000100"));

        instance.setAgencyId(716800);
        assertThat(instance.getAgencyId(), equalTo(716800));
        assertThat(instance.toString(), equalTo("716800"));
    }

    @Test
    public void testStringNumbers() {
        AgencyNumber instance = new AgencyNumber("100");
        assertThat(instance.getAgencyId(), equalTo(100));
        assertThat(instance.toString(), equalTo("000100"));

        instance.setAgencyId("716800");
        assertThat(instance.getAgencyId(), equalTo(716800));
        assertThat(instance.toString(), equalTo("716800"));
    }

    @Test
    public void testShortNumber() {
        AgencyNumber instance = new AgencyNumber(100);
        assertThat(instance.toString(), equalTo("000100"));
    }
}
