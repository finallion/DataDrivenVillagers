package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFields;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// What a profession goes after, and how hard it hits. A vanilla villager has no attack damage
/// attribute, no `ATTACK_TARGET` memory and no attack task; mixins add all three to every villager,
/// and only a profession with `attacks` gets the piglin's four attack tasks in its core list.
///
/// @param targets  entity types and the distance from which the villager goes for them
/// @param damage   base value of the attack damage attribute
/// @param cooldown ticks between swings
public record Attack(List<EntityRange> targets, double damage, int cooldown) {

    static final String TARGETS = "attacks";
    static final String SETTINGS = "attack";

    /// A zombie does 3, a fist 1: wins against one zombie, loses against two.
    public static final double DEFAULT_DAMAGE = 2.0;
    /// Vanilla's `MeleeAttackTask` cooldown for a piglin.
    public static final int DEFAULT_COOLDOWN = 20;

    public static Optional<Attack> parse(JsonObject root) {
        List<EntityRange> targets = EntityRange.parse(root, TARGETS);
        JsonElement settings = root.get(SETTINGS);
        if (targets.isEmpty()) {
            if (settings != null && !settings.isJsonNull()) {
                throw new DefinitionParseException("\"" + SETTINGS + "\" sets damage and cooldown, but \""
                        + TARGETS + "\" names nothing to use them on");
            }
            return Optional.empty();
        }
        double damage = DEFAULT_DAMAGE;
        int cooldown = DEFAULT_COOLDOWN;
        if (settings != null && !settings.isJsonNull()) {
            if (!settings.isJsonObject()) {
                throw new DefinitionParseException("\"" + SETTINGS + "\" must be an object with \"damage\" "
                        + "and \"cooldown\"");
            }
            JsonObject object = settings.getAsJsonObject();
            damage = positiveDouble(object, "damage", DEFAULT_DAMAGE);
            cooldown = JsonFields.positiveInt(object, "cooldown", DEFAULT_COOLDOWN);
        }
        return Optional.of(new Attack(targets, damage, cooldown));
    }

    private static double positiveDouble(JsonObject object, String field, double fallback) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionParseException("\"" + SETTINGS + "." + field + "\" must be a number");
        }
        double value = element.getAsDouble();
        if (value <= 0) {
            throw new DefinitionParseException("\"" + SETTINGS + "." + field + "\" must be above 0, got " + value);
        }
        return value;
    }

    public Optional<EntityRange> of(Identifier entityType) {
        for (EntityRange target : targets) {
            if (target.entity().equals(entityType)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    public String describe() {
        return targets.size() + " target type(s), " + damage + " damage every " + cooldown + " ticks";
    }
}
