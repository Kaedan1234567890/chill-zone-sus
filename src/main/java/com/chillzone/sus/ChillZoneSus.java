package com.chillzone.sus;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.detect.SusDetector;
import com.chillzone.sus.permission.Permissions;
import com.chillzone.sus.ui.SusMenu;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ChillZoneSus implements ModInitializer {
    public static final String MOD_ID = "chill_zone_sus";
    private static SusStore store;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            store = SusStore.load(server);
            SusDetector.init(store);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (store != null) {
                store.tick(server);
                if (server.getTickCount() % 20 == 0) SusDetector.refreshAll(System.currentTimeMillis());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (store != null) store.save(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("sus")
                .requires(source -> Permissions.has(source, Permissions.VIEW))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    SusMenu.open(player, store);
                    return 1;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        Set<String> names = new LinkedHashSet<>();
                        for (SusRecord r : store.all()) {
                            if (r.lastKnownName != null && !r.lastKnownName.isBlank()) names.add(r.lastKnownName);
                        }
                        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                            names.add(p.getGameProfile().name());
                        }
                        for (String name : names) builder.suggest(name);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        ServerPlayer staff = ctx.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(ctx, "player");

                        ServerPlayer online = findOnline(ctx.getSource().getServer().getPlayerList().getPlayers(), name);
                        if (online != null) {
                            SusRecord record = store.getOrCreate(online.getUUID(), online.getGameProfile().name());
                            SusMenu.openPlayer(staff, online.getUUID(), record.lastKnownName, store);
                            return 1;
                        }

                        SusRecord record = store.findByName(name);
                        if (record == null) {
                            ctx.getSource().sendFailure(Component.literal("No remembered SUS record for " + name + "."));
                            return 0;
                        }
                        SusMenu.openPlayer(staff, record.uuid, record.lastKnownName, store);
                        return 1;
                    }))
            );

            dispatcher.register(Commands.literal("susclear")
                .requires(source -> Permissions.has(source, Permissions.CLEAR))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (SusRecord r : store.all()) {
                            if (r.lastKnownName != null && !r.lastKnownName.isBlank()) builder.suggest(r.lastKnownName);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer online = findOnline(ctx.getSource().getServer().getPlayerList().getPlayers(), name);
                        SusRecord record = online != null
                            ? store.getOrCreate(online.getUUID(), online.getGameProfile().name())
                            : store.findByName(name);

                        if (record == null) {
                            ctx.getSource().sendFailure(Component.literal("No remembered SUS record for " + name + "."));
                            return 0;
                        }

                        store.clearActive(record.uuid, record.lastKnownName);
                        store.save(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Cleared /sus evidence and saved mining locations for " + record.lastKnownName + "."),
                            false
                        );
                        return 1;
                    }))
            );
        });
    }

    private static ServerPlayer findOnline(Iterable<ServerPlayer> players, String name) {
        for (ServerPlayer p : players) {
            if (p.getGameProfile().name().equalsIgnoreCase(name)) return p;
        }
        return null;
    }
}
