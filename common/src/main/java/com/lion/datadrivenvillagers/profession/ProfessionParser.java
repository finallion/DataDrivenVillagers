package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFields;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// Turns one json file into a {@link ProfessionDefinition}. No registry access, so the format is
/// unit testable without booting Minecraft.
public final class ProfessionParser {

    /// Public for the editor's placeholders and for the loader, which compares against them to tell a
    /// set value from a defaulted one.
    public static final int DEFAULT_TICKET_COUNT = 1;
    public static final int DEFAULT_SEARCH_DISTANCE = 1;

    private ProfessionParser() {
    }

    /// @param fileName file name without extension, becomes the registry path
    public static ProfessionDefinition parse(String fileName, JsonObject root) {
        String path = JsonFields.idPathFromFileName(fileName);

        Optional<Identifier> overrides =
                JsonFields.optionalString(root, "overrides").map(JsonFields::identifier);

        // "workstation" creates a job site, "add_workstations" extends an existing one; never both.
        List<Identifier> workstations = JsonFields.identifiers(root, "workstation", !overrides.isPresent());
        List<Identifier> addWorkstations = JsonFields.identifiers(root, "add_workstations", false);

        if (overrides.isPresent() && !workstations.isEmpty()) {
            throw new DefinitionParseException(
                    "\"workstation\" creates a new job site and cannot be used with \"overrides\", "
                            + "use \"add_workstations\" to give the overridden profession more blocks");
        }
        if (overrides.isEmpty() && !addWorkstations.isEmpty()) {
            throw new DefinitionParseException(
                    "\"add_workstations\" only means something with \"overrides\", "
                            + "a new profession names its blocks in \"workstation\"");
        }
        if (overrides.isEmpty() && workstations.isEmpty()) {
            throw new DefinitionParseException("\"workstation\" must name at least one block");
        }

        Optional<String> displayName = JsonFields.optionalString(root, "display_name");
        HatKind hat = JsonFields.optionalString(root, "hat").map(HatKind::parse).orElse(HatKind.NONE);
        Optional<Identifier> workSound =
                JsonFields.optionalString(root, "work_sound").map(JsonFields::identifier);

        // Absent means vanilla's unemployed gift; a derived path would log a miss for every profession
        // without a loot table.
        Optional<Identifier> gift = JsonFields.optionalString(root, "gift").map(JsonFields::identifier);

        // Schedule and the per-villager fields below are looked up by profession id at runtime, so an
        // override reads them too.
        Optional<ScheduleDefinition> schedule = ScheduleParser.parse(root, "schedule");

        JsonFields.TextureSource texture = JsonFields.texture(root, "texture");
        JsonFields.TextureSource zombie = JsonFields.texture(root, "zombie_texture");

        WorkBehaviour workBehaviour = JsonFields.optionalString(root, "work_behaviour")
                .map(WorkBehaviour::parse)
                .orElse(WorkBehaviour.STATION);
        Fears fears = Fears.parse(root);
        Optional<Attack> attack = Attack.parse(root);
        Optional<Double> health = JsonFields.positiveDouble(root, "health");
        List<Identifier> villages = JsonFields.identifiers(root, "villages", false);

        List<Identifier> gatherable = JsonFields.identifiers(root, "gatherable_items", false);
        List<Identifier> secondarySites = JsonFields.identifiers(root, "secondary_job_sites", false);
        if (workBehaviour == WorkBehaviour.FARM && overrides.isPresent()
                && !overrides.get().equals(Identifier.ofVanilla("farmer"))) {
            // The farm task needs farmland in SECONDARY_JOB_SITE, and the secondary sites of the
            // overridden profession are frozen in its record.
            throw new DefinitionParseException("\"work_behaviour\": \"farm\" cannot be given to an override: "
                    + overrides.get() + " does not know farmland as a secondary job site, and that list "
                    + "was handed to vanilla once at registration. Create a profession of your own instead.");
        }
        if (workBehaviour == WorkBehaviour.FARM && !overrides.isPresent()) {
            // Without seeds and farmland the farm task does nothing; vanilla's farmer values are the defaults.
            if (gatherable.isEmpty()) {
                gatherable = FARM_GATHERABLE;
            }
            if (secondarySites.isEmpty()) {
                secondarySites = FARM_SECONDARY_SITES;
            }
        }

        return new ProfessionDefinition(
                DataDrivenVillagers.id(path),
                overrides,
                workstations,
                addWorkstations,
                displayName,
                texture.identifier(),
                texture.file(),
                zombie.identifier(),
                zombie.file(),
                hat,
                workSound,
                gatherable,
                secondarySites,
                gift,
                schedule,
                workBehaviour,
                fears,
                attack,
                health,
                villages,
                JsonFields.positiveInt(root, "ticket_count", DEFAULT_TICKET_COUNT),
                JsonFields.positiveInt(root, "search_distance", DEFAULT_SEARCH_DISTANCE));
    }

    /// `minecraft:farmer`'s values in 1.21.1 (`VillagerProfession.register`): four
    /// items, not the six newer versions have.
    public static final List<Identifier> FARM_GATHERABLE = List.of(
            Identifier.ofVanilla("wheat"), Identifier.ofVanilla("wheat_seeds"),
            Identifier.ofVanilla("beetroot_seeds"), Identifier.ofVanilla("bone_meal"));
    public static final List<Identifier> FARM_SECONDARY_SITES = List.of(Identifier.ofVanilla("farmland"));
}
