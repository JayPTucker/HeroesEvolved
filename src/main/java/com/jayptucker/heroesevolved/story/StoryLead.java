package com.jayptucker.heroesevolved.story;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The Journal's current objective, read from the same server state as the relay controls. */
public final class StoryLead {
    private StoryLead() {}

    public static String next(ServerPlayer player) {
        return switch (LastSignalService.phase(player)) {
            case UNHEARD -> LastSignalService.hasBunker(player)
                    ? "Look for a Company relay ruin in the Overworld. The receiver in the bunker's records room can give you a bearing once main power returns."
                    : "Look for a Company relay ruin in the Overworld. Follow the yellow markings to the relay console.";
            case RECEIVED -> "Two occupied pods are calling from an old Company site. Find the relay ruins in the Overworld.";
            case SEARCHING -> "Find the relay console in the ruins. Bring it online to open the way to the stasis annex.";
            case ISOLATE -> "Use the isolator to cut the failing feed to the stasis relay.";
            case COOL -> "Use the coolant control to stabilize life support and open the pods.";
            case RESCUE -> LastSignalService.rescued(player) == 2
                    ? "Mara and Ivo reached the platform. Use the extraction console to finish the rescue."
                    : "Talk to Mara and Ivo and lead them to extraction. Watch the service route for Gray.";
            case COMPLETE -> LastSignalService.atSite(player)
                    ? "Mara and Ivo are safe. Follow the stairs out of the relay station."
                    : LastSignalService.hasBunker(player)
                            ? "Mara and Ivo made it out. Speak to them in the bunker for their account of the rescue."
                            : "Mara and Ivo made it out. The rescue is complete.";
        };
    }

    /** The key the player has bound to the Journal, shown as their own key on their screen. */
    public static Component journalKey() {
        return Component.keybind("key.heroesevolved.open_journal");
    }
}
