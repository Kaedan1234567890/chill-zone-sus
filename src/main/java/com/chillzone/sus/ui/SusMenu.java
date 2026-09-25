package com.chillzone.sus.ui;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.permission.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;

public final class SusMenu extends AbstractContainerMenu {
    private static final int SIZE = 54;
    private static final int LIST_SLOTS = 45;

    private final SimpleContainer container;
    private final ServerPlayer viewer;
    private final SusStore store;
    private final UUID focused;
    private final String focusedName;
    private final int page;

    private final Map<Integer, UUID> playerSlots = new HashMap<>();
    private final Map<Integer, Integer> evidenceSlots = new HashMap<>();

    private SusMenu(int syncId, Inventory inv, ServerPlayer viewer, SusStore store,
                    UUID focused, String focusedName, int page) {
        super(MenuType.GENERIC_9x6, syncId);
        this.viewer = viewer;
        this.store = store;
        this.focused = focused;
        this.focusedName = focusedName;
        this.page = Math.max(0, page);
        this.container = new SimpleContainer(SIZE);

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = col + row * 9;
                addSlot(new Slot(container, slot, 8 + col * 18, 18 + row * 18));
            }
        }

        build();
    }

    public static void open(ServerPlayer player, SusStore store) {
        open(player, store, 0);
    }

    private static void open(ServerPlayer player, SusStore store, int page) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, player, store, null, null, page),
            Component.literal("SUS - Most Suspicious")
        ));
    }

    public static void openPlayer(ServerPlayer staff, UUID targetUuid, String targetName, SusStore store) {
        staff.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, staff, store, targetUuid, targetName, 0),
            Component.literal("SUS - " + targetName)
        ));
    }

    private void build() {
        // Upper five rows are intentionally empty unless they contain real content.
        // Glass is ONLY used on the bottom navigation/control row.
        ItemStack filler = named(
            new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:gray_stained_glass_pane"))),
            Component.literal(" ")
        );
        for (int i = 45; i < SIZE; i++) container.setItem(i, filler.copy());

        if (focused != null) buildFocused();
        else buildList();
    }

    private void buildList() {
        List<SusRecord> entries = new ArrayList<>();
        for (SusRecord r : store.all()) {
            if (r != null && r.uuid != null && r.hasActiveEvidence()) entries.add(r);
        }

        entries.sort((a, b) -> {
            int byScore = Integer.compare(b.highestActiveScore(), a.highestActiveScore());
            if (byScore != 0) return byScore;
            return Long.compare(lastFlag(b), lastFlag(a));
        });

        int start = page * LIST_SLOTS;
        int end = Math.min(entries.size(), start + LIST_SLOTS);
        for (int i = start; i < end; i++) {
            SusRecord r = entries.get(i);
            int slot = i - start;
            ItemStack head = named(new ItemStack(Items.PLAYER_HEAD), Component.literal(safeName(r)));
            head.set(DataComponents.LORE, new ItemLore(playerSummaryLore(r)));
            container.setItem(slot, head);
            playerSlots.put(slot, r.uuid);
        }

        if (entries.isEmpty()) {
            ItemStack good = named(new ItemStack(Items.LIME_DYE), Component.literal("No active SUS flags"));
            good.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("No active mining/X-ray or movement evidence is recorded.")
            )));
            container.setItem(22, good);
        }

        int maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / LIST_SLOTS;
        if (page > 0) {
            container.setItem(45, named(new ItemStack(Items.ARROW), Component.literal("Previous Page")));
        }
        if (page < maxPage) {
            container.setItem(53, named(new ItemStack(Items.ARROW), Component.literal("Next Page")));
        }

        ItemStack info = named(new ItemStack(Items.BOOK), Component.literal("How SUS works"));
        info.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("SUS is an investigation signal, not proof."),
            Component.literal("Mining/X-ray flags may save evidence coordinates."),
            Component.literal("Fly, Speed and Elytra activity never save teleport locations."),
            Component.literal("Only the bottom row uses glass filler."),
            Component.literal("Page " + (page + 1) + " / " + (maxPage + 1))
        )));
        container.setItem(49, info);
    }

    private void buildFocused() {
        SusRecord r = store.get(focused);
        String name = r == null ? (focusedName == null ? "Unknown Player" : focusedName) : safeName(r);

        ItemStack head = named(new ItemStack(Items.PLAYER_HEAD), Component.literal(name));
        if (r != null) head.set(DataComponents.LORE, new ItemLore(playerSummaryLore(r)));
        container.setItem(4, head);

        // CONTROLS moved UP one row.
        if (Permissions.has(viewer, Permissions.TELEPORT)) {
            ItemStack tp = named(new ItemStack(Items.ENDER_PEARL), Component.literal("Teleport to Player"));
            tp.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Teleports to the player's current location."),
                Component.literal("Player must be online.")
            )));
            container.setItem(10, tp);
        }
        if (Permissions.has(viewer, Permissions.SPECTATE)) {
            ItemStack spec = named(new ItemStack(Items.ENDER_EYE), Component.literal("Spectate Player"));
            spec.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Uses normal spectator mode for reliability."),
                Component.literal("Player must be online.")
            )));
            container.setItem(13, spec);
        }
        if (Permissions.has(viewer, Permissions.CLEAR)) {
            ItemStack clear = named(new ItemStack(Items.BUCKET), Component.literal("Clear /sus"));
            clear.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Clears this player's active SUS evidence."),
                Component.literal("Also clears all saved mining teleport locations.")
            )));
            container.setItem(16, clear);
        }

        // ACTIVITY cards moved DOWN one row so long hover lore has more room.
        if (r != null) {
            ItemStack mining = named(new ItemStack(Items.DIAMOND_PICKAXE), Component.literal("Mining / X-Ray Activity"));
            mining.set(DataComponents.LORE, new ItemLore(miningLore(r)));
            container.setItem(19, mining);

            ItemStack fly = named(new ItemStack(Items.FEATHER), Component.literal("Fly Activity"));
            fly.set(DataComponents.LORE, new ItemLore(flyLore(r)));
            container.setItem(21, fly);

            ItemStack speed = named(new ItemStack(Items.SUGAR), Component.literal("Speed Activity"));
            speed.set(DataComponents.LORE, new ItemLore(speedLore(r)));
            container.setItem(23, speed);

            ItemStack elytra = named(new ItemStack(Items.ELYTRA), Component.literal("Elytra Activity"));
            elytra.set(DataComponents.LORE, new ItemLore(elytraLore(r)));
            container.setItem(25, elytra);

            // Exactly 18 evidence slots: two full rows, including the newly freed
            // far-left and far-right slots.
            int count = Math.min(SusStore.MAX_MINING_EVIDENCE, r.miningEvidence.size());
            for (int i = 0; i < count; i++) {
                SusRecord.EvidenceLocation e = r.miningEvidence.get(i);
                int slot = 27 + i;
                ItemStack evidence = named(new ItemStack(Items.COMPASS),
                    Component.literal("Mining Evidence #" + (i + 1)));
                evidence.set(DataComponents.LORE, new ItemLore(evidenceLore(e)));
                container.setItem(slot, evidence);
                evidenceSlots.put(slot, i);
            }
        }

        container.setItem(49, named(new ItemStack(Items.ARROW), Component.literal("Back")));
    }

    private static List<Component> playerSummaryLore(SusRecord r) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Highest SUS Score: " + r.highestActiveScore()));
        lore.add(Component.literal("Diamond Score: " + r.diamond.suspicionScore));
        lore.add(Component.literal("Ancient Debris Score: " + r.debris.suspicionScore));
        lore.add(Component.literal("Fly Flags: " + r.flyFlags));
        lore.add(Component.literal("Speed Flags: " + r.speedFlags));
        lore.add(Component.literal("Elytra Flags: " + r.elytraFlags));
        lore.add(Component.literal("Saved Mining Locations: " + r.miningEvidence.size() + "/" + SusStore.MAX_MINING_EVIDENCE));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Click to investigate"));
        return lore;
    }

    private static List<Component> miningLore(SusRecord r) {
        List<Component> lore = new ArrayList<>();
        addOreLore(lore, "DIAMONDS", r.diamond, r.totalBlocksBroken);
        lore.add(Component.literal(""));
        addOreLore(lore, "ANCIENT DEBRIS", r.debris, r.totalBlocksBroken);
        lore.add(Component.literal(""));
        lore.add(Component.literal("Saved Locations: " + r.miningEvidence.size() + "/" + SusStore.MAX_MINING_EVIDENCE));
        lore.add(Component.literal("Only mining/X-ray evidence saves locations."));
        return lore;
    }

    private static void addOreLore(List<Component> lore, String title, SusRecord.OreCase c, long totalBlocks) {
        lore.add(Component.literal(title));
        lore.add(Component.literal("Suspicion Score: " + c.suspicionScore + " (" + c.status() + ")"));
        lore.add(Component.literal("Active Flags: " + c.activeFlags));
        lore.add(Component.literal("Ore Mined: " + c.oreMined));
        lore.add(Component.literal("Separate Veins: " + c.separateVeins));
        lore.add(Component.literal("Ore per Vein: " + String.format(Locale.ROOT, "%.1f", c.orePerVein())));
        lore.add(Component.literal("Total Blocks Broken: " + totalBlocks));
        lore.add(Component.literal("Avg Blocks Between Veins: " + blocks(c.averageBlocksBetweenVeins())));
        lore.add(Component.literal("Average Time Between Veins: " + duration(c.averageIntervalMs())));
        lore.add(Component.literal("Fastest Vein: " + duration(c.fastestIntervalMs())));
        lore.add(Component.literal("Cave-Exposed Veins: " + c.caveExposedVeins + " (" + c.cavePercent() + "%)"));
        lore.add(Component.literal("Tunnel-Like Veins: " + c.tunnelLikeVeins + " (" + c.tunnelPercent() + "%)"));
        lore.add(Component.literal("Unusual Ore Events: " + c.unusualOreEvents));
        lore.add(Component.literal("Last Flag: " + timeAgo(c.lastFlagEpochMs)));
    }

    private static List<Component> flyLore(SusRecord r) {
        return List.of(
            Component.literal("Fly Flags: " + r.flyFlags),
            Component.literal("Flight Attempts: " + r.flightAttempts),
            Component.literal("Blocked by Anti-Fly: " + r.blockedFlightAttempts),
            Component.literal("Successful Flight: " + (r.successfulFlight ? "YES" : "NO")),
            Component.literal("Last Movement Flag: " + timeAgo(r.lastMovementFlagEpochMs)),
            Component.literal(""),
            Component.literal("Movement evidence is informational only."),
            Component.literal("No teleport location is saved for Fly flags.")
        );
    }

    private static List<Component> speedLore(SusRecord r) {
        return List.of(
            Component.literal("Speed Flags: " + r.speedFlags),
            Component.literal("Last Actual Speed: " + speed(r.lastActualSpeed)),
            Component.literal("Last Allowed Speed: " + speed(r.lastAllowedSpeed)),
            Component.literal("Last Movement Flag: " + timeAgo(r.lastMovementFlagEpochMs)),
            Component.literal(""),
            Component.literal("Movement evidence is informational only."),
            Component.literal("No teleport location is saved for Speed flags.")
        );
    }

    private static List<Component> elytraLore(SusRecord r) {
        return List.of(
            Component.literal("Elytra Flags: " + r.elytraFlags),
            Component.literal("Last Elytra Speed: " + speed(r.lastElytraSpeed)),
            Component.literal("Expected Speed: " + speed(r.lastExpectedElytraSpeed)),
            Component.literal("Pitch: " + String.format(Locale.ROOT, "%.1f", r.lastPitch)),
            Component.literal("Straight-Up Flight: " + (r.straightUpFlight ? "YES" : "NO")),
            Component.literal("Rockets: " + r.rocketsUsed),
            Component.literal("Wind Charges: " + r.windChargesUsed),
            Component.literal("Last Movement Flag: " + timeAgo(r.lastMovementFlagEpochMs)),
            Component.literal(""),
            Component.literal("Movement evidence is informational only."),
            Component.literal("No teleport location is saved for Elytra flags.")
        );
    }

    private static List<Component> evidenceLore(SusRecord.EvidenceLocation e) {
        String type = "debris".equals(e.type) ? "Ancient Debris" : "Diamond";
        return List.of(
            Component.literal("Type: " + type),
            Component.literal("World: " + shortWorld(e.world)),
            Component.literal("X: " + e.x + "  Y: " + e.y + "  Z: " + e.z),
            Component.literal("Saved: " + timeAgo(e.epochMs)),
            Component.literal(e.reason == null ? "Suspicious mining flag" : e.reason),
            Component.literal(""),
            Component.literal("Click to teleport to this evidence location")
        );
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, net.minecraft.world.entity.player.Player player) {
        if (slotId < 0 || slotId >= SIZE) return;

        if (focused == null) {
            UUID uuid = playerSlots.get(slotId);
            if (uuid != null) {
                SusRecord r = store.get(uuid);
                if (r != null) openPlayer(viewer, uuid, safeName(r), store);
                return;
            }
            if (slotId == 45 && page > 0) {
                open(viewer, store, page - 1);
                return;
            }
            if (slotId == 53) {
                int active = 0;
                for (SusRecord r : store.all()) if (r != null && r.hasActiveEvidence()) active++;
                int maxPage = active == 0 ? 0 : (active - 1) / LIST_SLOTS;
                if (page < maxPage) open(viewer, store, page + 1);
            }
            return;
        }

        ServerPlayer target = onlinePlayer(focused);

        if (slotId == 10 && Permissions.has(viewer, Permissions.TELEPORT)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            viewer.teleportTo(
                target.level(),
                target.getX(), target.getY(), target.getZ(),
                Set.of(),
                target.getYRot(), target.getXRot(),
                false
            );
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Teleported to " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 13 && Permissions.has(viewer, Permissions.SPECTATE)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            // A camera-only survival implementation is fragile on vanilla clients;
            // keep the reliable normal spectator behavior rather than risking a
            // desynced staff body/camera.
            viewer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            viewer.setCamera(target);
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Now spectating " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 16 && Permissions.has(viewer, Permissions.CLEAR)) {
            SusRecord r = store.get(focused);
            String name = target != null ? target.getGameProfile().name()
                : (r != null ? safeName(r) : (focusedName == null ? "player" : focusedName));
            store.clearActive(focused, name);
            store.save(viewer.level().getServer());
            viewer.sendSystemMessage(Component.literal("Cleared /sus evidence and saved mining locations for " + name + "."));
            open(viewer, store);
            return;
        }

        Integer evidenceIndex = evidenceSlots.get(slotId);
        if (evidenceIndex != null && Permissions.has(viewer, Permissions.TELEPORT)) {
            SusRecord r = store.get(focused);
            if (r == null || evidenceIndex < 0 || evidenceIndex >= r.miningEvidence.size()) return;
            SusRecord.EvidenceLocation e = r.miningEvidence.get(evidenceIndex);
            ServerLevel level = findLevel(e.world);
            if (level == null) {
                viewer.sendSystemMessage(Component.literal("That evidence world is not currently available."));
                return;
            }
            viewer.teleportTo(
                level,
                e.x + 0.5, e.y + 1.0, e.z + 0.5,
                Set.of(),
                viewer.getYRot(), viewer.getXRot(),
                false
            );
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Teleported to saved mining evidence #" + (evidenceIndex + 1) + "."));
            return;
        }

        if (slotId == 49) open(viewer, store, page);
    }

    private ServerPlayer onlinePlayer(UUID uuid) {
        return viewer.level().getServer().getPlayerList().getPlayer(uuid);
    }

    private ServerLevel findLevel(String world) {
        if (world == null) return null;
        for (ServerLevel level : viewer.level().getServer().getAllLevels()) {
            if (world.equals(level.dimension().toString())) return level;
        }
        return null;
    }

    private static long lastFlag(SusRecord r) {
        long last = Math.max(r.diamond.lastFlagEpochMs, r.debris.lastFlagEpochMs);
        return Math.max(last, r.lastMovementFlagEpochMs);
    }

    private static String safeName(SusRecord r) {
        return r.lastKnownName == null || r.lastKnownName.isBlank() ? r.uuid.toString() : r.lastKnownName;
    }

    private static ItemStack named(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static String blocks(double v) {
        return v < 0 ? "N/A" : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String speed(double v) {
        return v <= 0 ? "N/A" : String.format(Locale.ROOT, "%.2f", v);
    }

    private static String duration(long ms) {
        if (ms < 0) return "N/A";
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    private static String shortWorld(String world) {
        if (world == null || world.isBlank()) return "Unknown";
        int slash = world.lastIndexOf('/');
        int close = world.lastIndexOf(']');
        if (slash >= 0 && close > slash) return world.substring(slash + 1, close).trim();
        return world;
    }

    private static String timeAgo(long epochMs) {
        if (epochMs <= 0) return "None";
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000L);
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
