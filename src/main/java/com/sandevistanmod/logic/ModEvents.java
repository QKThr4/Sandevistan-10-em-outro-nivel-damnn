package com.sandevistanmod.logic;

import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import com.sandevistanmod.net.Net;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SandevistanMod.MODID)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) SandevistanLogic.tick(sp);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        SandevistanLogic.updateTickRate(e.getServer());
    }

    /**
     * Com o Sandevistan o jogo roda a 2 ticks e o "tempo de invulnerabilidade" do alvo duraria varios segundos:
     * zera, para bater rapido continuar funcionando como no tempo normal.
     */
    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer sp
                && e.getEntity() != sp
                && SandevistanData.get(sp).active) {
            e.getEntity().invulnerableTime = 0;
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) SandevistanLogic.resetOnDeath(sp);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) SandevistanLogic.onLogin(sp);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) SandevistanLogic.onLogout(sp);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) SandevistanLogic.onRespawn(sp);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Net.sync(sp);
    }

    /** Comando de teste (OP): /sandevistan psicose <0-300> define a ciberpsicose. */
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("sandevistan")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("mente")
                        .then(mindMode("add", 0))
                        .then(mindMode("remove", 1))
                        .then(mindMode("set", 2))
                        .then(Commands.literal("atual")
                                .then(mindMode("add", 0, true))
                                .then(mindMode("remove", 1, true))
                                .then(mindMode("set", 2, true))))
                .then(Commands.literal("psicose")
                        .then(Commands.argument("valor", FloatArgumentType.floatArg(0f, 300f))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    SandevistanLogic.setPsy(p, FloatArgumentType.getFloat(c, "valor"));
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }

    /** /sandevistan mente <add|remove|set> <0-100> [jogadores]: muda a Mente permanente. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mindMode(String name, int mode) {
        return mindMode(name, mode, false);
    }

    /** current = true muda a Mente ATUAL (a que regenera) em vez da permanente. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mindMode(String name, int mode,
                                                                                                    boolean current) {
        return Commands.literal(name).then(Commands.argument("valor", IntegerArgumentType.integer(0, 100))
                .executes(c -> {
                    adjust(c.getSource().getPlayerOrException(), mode, IntegerArgumentType.getInteger(c, "valor"), current);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("alvos", EntityArgument.players()).executes(c -> {
                    int v = IntegerArgumentType.getInteger(c, "valor");
                    int n = 0;
                    for (ServerPlayer sp : EntityArgument.getPlayers(c, "alvos")) {
                        adjust(sp, mode, v, current);
                        n++;
                    }
                    return n;
                })));
    }

    private static void adjust(ServerPlayer p, int mode, int value, boolean current) {
        if (current) SandevistanLogic.adjustCurrentMind(p, mode, value);
        else SandevistanLogic.adjustMind(p, mode, value);
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent e) {
        SandevistanLogic.clearAll();
    }
}
