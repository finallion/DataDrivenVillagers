package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionParser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldTradesTest {

    private static ProfessionDefinition parse(String name, String raw) {
        return ProfessionParser.parse(name, JsonParser.parseString(raw).getAsJsonObject());
    }

    @Test
    void tradesPieceTargetsTheVtpPath() {
        ProfessionDefinition definition = parse("baker", """
                { "workstation": "minecraft:smoker" }
                """);
        Scaffold.Piece piece = Scaffold.tradesPiece(definition);

        assertEquals("datapack/data/datadrivenvillagers/default_villager_trades/baker.json",
                piece.destination());
        JsonObject content = JsonParser.parseString(piece.content()).getAsJsonObject();
        assertEquals("datadrivenvillagers:baker", content.get("profession").getAsString());
        assertTrue(content.has("trades"));
    }

    @Test
    void anOverrideNamesTheProfessionItOverrides() {
        ProfessionDefinition definition = parse("my_farmer", """
                { "overrides": "minecraft:farmer" }
                """);
        JsonObject content = JsonParser.parseString(Scaffold.tradesPiece(definition).content())
                .getAsJsonObject();
        assertEquals("minecraft:farmer", content.get("profession").getAsString());
    }

    @Test
    void theSamePieceGoesIntoScaffoldAndButton() {
        ProfessionDefinition definition = parse("baker", """
                { "workstation": "minecraft:smoker" }
                """);
        assertEquals(Scaffold.tradesPiece(definition), Scaffold.pieces(definition).get(0));
    }
}
