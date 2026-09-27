package com.lion.datadrivenvillagers.profession;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;

/// Makes `ticket_count > 1` work. Vanilla reserves `ticketCount` places per point of interest, but
/// `WorkStationCompetitionTask` (takes the job from the less experienced of two villagers on one
/// block) and `TakeJobSiteTask` (hands a spotted block to a neighbour already working there) assume
/// one worker per block. Every vanilla job site has `ticketCount = 1`, so vanilla never hits this.
/// Both tasks are skipped on a shared station of ours; nothing is released. The competition loser
/// holds no ticket, so releasing one would free the winner's place instead.
public final class JobSiteTickets {

    private JobSiteTickets() {
    }

    /// One of ours with more than one place. Runs on the villager tick; `isOurs` is an identity lookup.
    public static boolean isShared(RegistryEntry<PointOfInterestType> poi) {
        return poi.value().ticketCount() > 1 && ProfessionRegistry.isOurs(poi);
    }
}
