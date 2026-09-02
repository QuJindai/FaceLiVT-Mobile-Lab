package com.qujindai.facelivtlab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionCsvTest {
    @Test public void escapesCommaQuoteAndNewline() {
        assertEquals("plain", SessionCsv.escape("plain"));
        assertEquals("\"a,b\"", SessionCsv.escape("a,b"));
        assertEquals("\"a\"\"b\"", SessionCsv.escape("a\"b"));
        assertEquals("\"a\nb\"", SessionCsv.escape("a\nb"));
    }

    @Test public void nullBecomesEmptyField() {
        assertEquals("", SessionCsv.escape(null));
    }
}
