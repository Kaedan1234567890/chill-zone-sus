package com.chillzone.sus.ui;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.permission.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.Duration;
import java.util.*;

public final class SusMenu extends AbstractContainerMenu {
    private static final int SIZE = 54;
    private final SimpleContainer container;
    private final ServerPlayer viewer;
    private final SusStore store;
    private final Map<Integer, UUID> playerSlots = new HashMap<>();
    private final UUID focused;

    private SusMenu(int syncId, Inventory inv, ServerPlayer viewer, SusStore store, UUID focused) {
        super(MenuType.GENERIC_9x6, syncId);
        this.viewer = viewer;
        this.store = store;
        this.focused = focused;
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
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, player, store, null),
            Component.literal("SUS - Most Suspicious")
        ));
    }

    public static void openPlayer(ServerPlayer staff, ServerPlayer target, SusStore store) {
        staff.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, staff, store, target.getUUID()),
            Component.literal("SUS - " + target.getGameProfile().name())
        ));
    }

    private void build() {
        ItemStack filler = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));
        for (int i = 0; i < SIZE; i++) container.setItem(i, filler.copy());

        if (focused != null) {
            buildFocused();
        } else {
            buildList();
        }
    }

    private void buildList() {
        long now = System.currentTimeMillis();
        List<SusRecord> records = new ArrayList<>(store.all());
        records.removeIf(r -> r.suspicionScore <= 0);
        records.sort(Comparator
            .comparingInt((SusRecord r) -> r.suspicionScore).reversed()
            .thenComparingLong(r -> r.lastFlagEpochMs));

        int slot = 10;
        for (SusRecord r : records) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.CUSTOM_NAME, Component.literal(r.lastKnownName));
            head.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Suspicion Score: " + r.suspicionScore),
                Component.literal("Recent Flags: " + r.recentFlags(now)),
                Component.literal("Last Flag: " + timeAgo(r.lastFlagEpochMs)),
                Component.literal("Status: " + r.status()),
                Component.literal("Clean Period: " + cleanPeriod(r.cleanActiveTicks)),
                Component.literal(""),
                Component.literal("Click to investigate")
            )));
            container.setItem(slot, head);
            playerSlots.put(slot, r.uuid);
            slot++;
        }

        if (playerSlots.isEmpty()) {
            ItemStack good = named(new ItemStack(Items.LIME_DYE), Component.literal("No active SUS flags"));
            good.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Nobody currently has an active suspicion score.")
            )));
            container.setItem(22, good);
        }

        ItemStack info = named(new ItemStack(Items.BOOK), Component.literal("How SUS works"));
        info.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Flags are private to staff."),
            Component.literal("No automatic punishments."),
            Component.literal("90 days of clean active play clears active flags.")
        )));
        container.setItem(49, info);
    }

    private void buildFocused() {
        SusRecord r = store.get(focused);
        String name = r == null ? "Unknown Player" : r.lastKnownName;

        ItemStack head = named(new ItemStack(Items.PLAYER_HEAD), Component.literal(name));
        if (r != null) {
            head.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Suspicion Score: " + r.suspicionScore),
                Component.literal("Recent Flags: " + r.recentFlags(System.currentTimeMillis())),
                Component.literal("Last Flag: " + timeAgo(r.lastFlagEpochMs)),
                Component.literal("Status: " + r.status()),
                Component.literal("Clean Period: " + cleanPeriod(r.cleanActiveTicks)),
                Component.literal("Archived Points: " + r.archivedFlags)
            )));
        }
        container.setItem(13, head);

        if (Permissions.has(viewer, Permissions.TELEPORT)) {
            ItemStack pearl = named(new ItemStack(Items.ENDER_PEARL), Component.literal("Teleport to Player"));
            container.setItem(29, pearl);
        }

        if (Permissions.has(viewer, Permissions.SPECTATE)) {
            ItemStack eye = named(new ItemStack(Items.ENDER_EYE), Component.literal("Spectate Player"));
            container.setItem(31, eye);
        }

        if (Permissions.has(viewer, Permissions.CLEAR)) {
            ItemStack clear = named(new ItemStack(Items.BUCKET), Component.literal("Clear Active Flags"));
            clear.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Moves the active score into archive history.")
            )));
            container.setItem(33, clear);
        }

        container.setItem(49, named(new ItemStack(Items.ARROW), Component.literal("Back")));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, net.minecraft.world.entity.player.Player player) {
        if (slotId < 0 || slotId >= SIZE) return;

        if (focused == null) {
            UUID targetId = playerSlots.get(slotId);
            if (targetId != null) {
                ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(targetId);
                if (target != null) {
                    openPlayer(viewer, target, store);
                } else {
                    viewer.sendSystemMessage(Component.literal("That player is no longer online."));
                    open(viewer, store);
                }
            }
            return;
        }

        ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(focused);

        if (slotId == 29 && Permissions.has(viewer, Permissions.TELEPORT)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            viewer.teleportTo(
                target.serverLevel(),
                target.getX(), target.getY(), target.getZ(),
                java.util.Set.of(),
                target.getYRot(), target.getXRot(),
                false
            );
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Teleported to " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 31 && Permissions.has(viewer, Permissions.SPECTATE)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            viewer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            viewer.setCamera(target);
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Now spectating " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 33 && Permissions.has(viewer, Permissions.CLEAR)) {
            String name = target != null ? target.getGameProfile().name()
                : Optional.ofNullable(store.get(focused)).map(x -> x.lastKnownName).orElse("player");
            store.clearActive(focused, name);
            store.save(viewer.level().getServer());
            viewer.sendSystemMessage(Component.literal("Cleared active SUS flags for " + name + "."));
            open(viewer, store);
            return;
        }

        if (slotId == 49) {
            open(viewer, store);
        }
    }

    private static ItemStack named(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
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

    private static String cleanPeriod(long ticks) {
        long seconds = ticks / 20L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        if (days > 0) return days + "d " + hours + "h";
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
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
