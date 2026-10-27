package com.sandevistanmod.logic;

import com.mojang.brigadier.Command;
import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import com.sandevistanmod.data.Tx;
import com.sandevistanmod.registry.ModRegistries;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** O Ripperdoc: aldeao doutor da clinica da vila. Implanta e remove o Sandevistan. */
@EventBusSubscriber(modid = SandevistanMod.MODID)
public final class RipperdocEvents {
    private RipperdocEvents() {}

    public static final ResourceLocation CLINIC_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, "village/ripperdoc_clinic");
    /** Posicao (x, y, z) do bloco jigsaw dentro da estrutura (entrada, virada para o norte). */
    private static final int ENTRANCE_X = 5;
    private static final int ENTRANCE_Y = 1;

    private static final String[] VILLAGE_TYPES = {"plains", "desert", "savanna", "snowy", "taiga"};

    // ---------- conversa com o doutor ----------
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Villager v)) return;
        if (v.getVillagerData().getProfession() != ModRegistries.RIPPERDOC.get()) return;
        // o doutor nao faz troca normal: so conversa
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide()));
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (e.getEntity() instanceof ServerPlayer sp) talk(sp, v);
    }

    private static MutableComponent button(String key, String command, ChatFormatting color, String hoverKey, Object... hoverArgs) {
        return Tx.t(color, key).withStyle(st -> st.withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Tx.t(hoverKey, hoverArgs))));
    }

    private static void talk(ServerPlayer p, Villager v) {
        SandevistanData d = SandevistanData.get(p);
        v.getNavigation().stop();
        v.getLookControl().setLookAt(p, 30f, 30f);
        int greet = p.getRandom().nextInt(4);
        p.sendSystemMessage(Tx.t(ChatFormatting.GOLD, "doc.name").append(Component.literal(": "))
                .append(Tx.t(ChatFormatting.YELLOW, "doc.greet." + greet)));
        if (d.implanted) {
            p.sendSystemMessage(Tx.t(ChatFormatting.YELLOW, "doc.remove.ask").append(Component.literal(" "))
                    .append(button("doc.btn.remove", "/ripperdoc remove", ChatFormatting.RED, "doc.hover.remove")));
        } else {
            p.sendSystemMessage(Tx.t(ChatFormatting.YELLOW, "doc.implant.ask", SandevistanLogic.CLINIC_EMERALDS,
                            SandevistanLogic.IMPLANT_HEARTS).append(Component.literal(" "))
                    .append(button("doc.btn.implant", "/ripperdoc implant", ChatFormatting.GREEN, "doc.hover.implant",
                            SandevistanLogic.CLINIC_EMERALDS, SandevistanLogic.IMPLANT_HEARTS)));
        }
    }

    // ---------- comandos ----------
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        // usado pelos botoes do chat; so funciona perto de um Ripperdoc
        e.getDispatcher().register(Commands.literal("ripperdoc")
                .then(Commands.literal("implant").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!SandevistanLogic.nearDoctor(p)) {
                        p.sendSystemMessage(Tx.t(ChatFormatting.RED, "doc.far"));
                        return 0;
                    }
                    SandevistanLogic.implantAtDoctor(p);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("remove").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!SandevistanLogic.nearDoctor(p)) {
                        p.sendSystemMessage(Tx.t(ChatFormatting.RED, "doc.far"));
                        return 0;
                    }
                    SandevistanLogic.removeImplant(p, SandevistanData.get(p));
                    return Command.SINGLE_SUCCESS;
                })));
        // OP: constroi a clinica na frente do jogador (para testar sem procurar vila)
        e.getDispatcher().register(Commands.literal("sandevistan")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("clinica").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    boolean ok = placeClinic(p);
                    p.sendSystemMessage(Tx.t(ok ? ChatFormatting.GREEN : ChatFormatting.RED, ok ? "clinic.built" : "clinic.fail"));
                    return ok ? Command.SINGLE_SUCCESS : 0;
                })));
    }

    /** Coloca a clinica ao sul do jogador, com a entrada virada para o norte (para o jogador). */
    private static boolean placeClinic(ServerPlayer p) {
        try {
            ServerLevel level = p.serverLevel();
            StructureTemplate t = level.getStructureManager().get(CLINIC_STRUCTURE).orElse(null);
            if (t == null) return false;
            BlockPos origin = new BlockPos(p.getBlockX() - ENTRANCE_X, p.getBlockY() - ENTRANCE_Y, p.getBlockZ() + 2);
            StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false);
            boolean ok = t.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
            if (ok) {
                // o jigsaw so serve para a vila: troca por um bloco de calcada
                BlockPos jig = origin.offset(ENTRANCE_X, ENTRANCE_Y, 0);
                if (level.getBlockState(jig).is(Blocks.JIGSAW)) level.setBlock(jig, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
            }
            return ok;
        } catch (Exception ex) {
            return false;
        }
    }

    // ---------- a clinica nas vilas ----------
    /** Coloca a clinica na lista de casas das vilas (as 5 variantes), sem substituir nada do jogo. */
    @SubscribeEvent
    public static void aboutToStart(ServerAboutToStartEvent e) {
        try {
            MinecraftServer server = e.getServer();
            var pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            for (String type : VILLAGE_TYPES) {
                ResourceLocation id = ResourceLocation.withDefaultNamespace("village/" + type + "/houses");
                StructureTemplatePool pool = pools.getOptional(id).orElse(null);
                if (pool == null) continue;
                addToPool(pool, CLINIC_STRUCTURE.toString(), 3);
            }
        } catch (Exception ex) {
            SandevistanMod.LOGGER_WARN("Could not add the Ripperdoc clinic to the villages: " + ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addToPool(StructureTemplatePool pool, String location, int weight) throws Exception {
        // a lista usada na geracao e a unica ObjectArrayList da classe (acha pelo tipo, nao pelo nome)
        for (Field f : StructureTemplatePool.class.getDeclaredFields()) {
            if (f.getType() == ObjectArrayList.class) {
                f.setAccessible(true);
                ObjectArrayList<StructurePoolElement> list = (ObjectArrayList<StructurePoolElement>) f.get(pool);
                StructurePoolElement el = StructurePoolElement.single(location).apply(StructureTemplatePool.Projection.RIGID);
                for (int i = 0; i < weight; i++) list.add(el);
                return;
            }
        }
    }
}
