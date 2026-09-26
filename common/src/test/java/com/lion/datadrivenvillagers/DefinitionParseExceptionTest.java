package com.lion.datadrivenvillagers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Covers `readableReason`: the Gson prefix, control characters, the section sign, and length.
class DefinitionParseExceptionTest {

    @Test
    void stripsTheGsonExceptionPrefix() {
        Exception e = new com.google.gson.JsonSyntaxException("java.lang.IllegalStateException: bad token");
        assertEquals("bad token", DefinitionParseException.readableReason(e));
    }

    @Test
    void controlCharactersAndTheSectionSignBecomeSpaces() {
        String reason = DefinitionParseException.readableReason(
                new DefinitionParseException("got \"x\r\n[Server] fake log line§cred\""));
        assertFalse(reason.contains("\r"));
        assertFalse(reason.contains("\n"));
        assertFalse(reason.contains("§"));
        assertTrue(reason.contains("fake log line"));
    }

    @Test
    void c1ControlsAndUnicodeLineSeparatorsBecomeSpaces() {
        String reason = DefinitionParseException.readableReason(
                new DefinitionParseException("a\u0085b\u2028c\u2029d"));
        assertEquals("a b c d", reason);
    }

    @Test
    void onlyAVeryLongRawValueIsCutOff() {
        String reason = DefinitionParseException.readableReason(
                new DefinitionParseException("x".repeat(600)));
        assertTrue(reason.length() <= 503);
        assertTrue(reason.endsWith("..."));
    }

    @Test
    void theLongestExistingParserMessageStaysComplete() {
        String message = "\"work_behaviour\": \"farm\" cannot be given to an override: "
                + "datadrivenvillagers:example_profession does not know farmland as a secondary job site, "
                + "and that list was handed to vanilla once at registration. Create a profession of your "
                + "own instead.";
        assertEquals(message, DefinitionParseException.readableReason(new DefinitionParseException(message)));
    }
}
