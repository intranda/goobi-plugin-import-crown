package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AuthorityDataTest {

    @Test
    public void testBlankValue() {
        assertNull(AuthorityData.resolve(null, null));
        assertNull(AuthorityData.resolve("", "gnd"));
        assertNull(AuthorityData.resolve("   ", "gnd"));
        assertNull(AuthorityData.resolve("/", "gnd"));
    }

    @Test
    public void testDetectGnd() {
        AuthorityData fixture = AuthorityData.resolve("http://d-nb.info/gnd/118540238", null);
        assertEquals("gnd", fixture.getName());
        assertEquals("http://d-nb.info/gnd/", fixture.getUri());
        assertEquals("http://d-nb.info/gnd/118540238", fixture.getValue());
    }

    @Test
    public void testDetectGndWithCheckDigit() {
        AuthorityData fixture = AuthorityData.resolve("https://d-nb.info/gnd/4074335-4", null);
        assertEquals("gnd", fixture.getName());
        assertEquals("https://d-nb.info/gnd/4074335-4", fixture.getValue());
    }

    @Test
    public void testDetectGeonames() {
        AuthorityData fixture = AuthorityData.resolve("http://www.geonames.org/2950159", null);
        assertEquals("geonames", fixture.getName());
        assertEquals("http://www.geonames.org/", fixture.getUri());
        assertEquals("http://www.geonames.org/2950159", fixture.getValue());
    }

    @Test
    public void testDetectGeonamesWithPlaceName() {
        AuthorityData fixture = AuthorityData.resolve("https://www.geonames.org/2950159/muenchen.html", null);
        assertEquals("geonames", fixture.getName());
        assertEquals("https://www.geonames.org/2950159/muenchen.html", fixture.getValue());
    }

    @Test
    public void testDetectViaf() {
        AuthorityData fixture = AuthorityData.resolve("http://viaf.org/viaf/12345", null);
        assertEquals("viaf", fixture.getName());
        assertEquals("http://viaf.org/viaf/", fixture.getUri());
        assertEquals("http://viaf.org/viaf/12345", fixture.getValue());
    }

    @Test
    public void testSurroundingWhitespaceIsRemoved() {
        AuthorityData fixture = AuthorityData.resolve("  http://viaf.org/viaf/12345  ", null);
        assertEquals("viaf", fixture.getName());
        assertEquals("http://viaf.org/viaf/12345", fixture.getValue());
    }

    @Test
    public void testPlainIdentifierUsesGndAsDefault() {
        AuthorityData fixture = AuthorityData.resolve("118540238", null);
        assertEquals("gnd", fixture.getName());
        assertEquals("118540238", fixture.getValue());
    }

    @Test
    public void testPlainIdentifierWithConfiguredType() {
        AuthorityData fixture = AuthorityData.resolve("2950159", "geonames");
        assertEquals("geonames", fixture.getName());
        assertEquals("2950159", fixture.getValue());
    }

    @Test
    public void testUnknownUriIsReducedToIdentifier() {
        AuthorityData fixture = AuthorityData.resolve("http://example.org/authority/12345/", "gnd");
        assertEquals("gnd", fixture.getName());
        assertEquals("12345", fixture.getValue());
    }

    @Test
    public void testConfiguredTypeIsCaseInsensitive() {
        assertEquals("geonames", AuthorityData.resolve("2950159", " GeoNames ").getName());
        assertEquals("viaf", AuthorityData.resolve("12345", "VIAF").getName());
    }

    @Test
    public void testConfiguredTypeOverridesDetection() {
        AuthorityData fixture = AuthorityData.resolve("http://viaf.org/viaf/12345", "gnd");
        assertEquals("gnd", fixture.getName());
        assertEquals("http://d-nb.info/gnd/", fixture.getUri());
        assertEquals("http://viaf.org/viaf/12345", fixture.getValue());
    }

    @Test
    public void testUnknownConfiguredTypeFallsBackToDetection() {
        AuthorityData fixture = AuthorityData.resolve("http://d-nb.info/gnd/118540238", "something");
        assertEquals("gnd", fixture.getName());
        assertEquals("http://d-nb.info/gnd/118540238", fixture.getValue());
    }
}
