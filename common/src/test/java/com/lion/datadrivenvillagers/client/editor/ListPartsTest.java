package com.lion.datadrivenvillagers.client.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListPartsTest {

    private static final String TWO = "minecr, minecraft:crying_obsidian";

    @Test
    void partUnderTheCursorNotTheLastOne() {
        assertEquals("minecr", ListParts.at(TWO, 6));
        assertEquals("minecraft:crying_obsidian", ListParts.at(TWO, TWO.length()));
        assertEquals("minecraft:crying_obsidian", ListParts.at(TWO, 8));
    }

    @Test
    void cursorRightAtACommaBelongsToTheValueBeforeIt() {
        assertEquals("minecr", ListParts.at(TWO, 6));
        assertEquals("minecraft:crying_obsidian", ListParts.at(TWO, 7));
    }

    @Test
    void singleValueAndEmptyBox() {
        assertEquals("stone", ListParts.at("stone", 3));
        assertEquals("", ListParts.at("", 0));
        assertEquals("stone", ListParts.at("stone", 99));
    }

    @Test
    void ghostOnlyInTheLastValue() {
        assertFalse(ListParts.atEnd(TWO, 6));
        assertTrue(ListParts.atEnd(TWO, TWO.length()));
        assertTrue(ListParts.atEnd("stone", 2));
    }

    @Test
    void replaceKeepsTheOtherValues() {
        assertEquals("minecraft:stone, minecraft:crying_obsidian",
                ListParts.replace(TWO, 6, "minecraft:stone"));
        assertEquals("minecr, minecraft:tuff", ListParts.replace(TWO, 10, "minecraft:tuff"));
        assertEquals("minecraft:stone", ListParts.replace("sto", 3, "minecraft:stone"));
        assertEquals("minecraft:stone", ListParts.replace("", 0, "minecraft:stone"));
    }

    @Test
    void cursorLandsAfterWhatWasPutIn() {
        assertEquals("minecraft:stone".length(), ListParts.cursorAfterReplace(TWO, 6, "minecraft:stone"));
        assertEquals("minecr, minecraft:tuff".length(), ListParts.cursorAfterReplace(TWO, 10, "minecraft:tuff"));
    }
}
