package com.sandevistanmod.registry;

import com.sandevistanmod.SandevistanMod;
import com.sandevistanmod.data.SandevistanData;
import com.sandevistanmod.logic.ClinicBlock;
import com.sandevistanmod.logic.GoodMindEffect;
import com.sandevistanmod.logic.ImmunoItem;
import com.sandevistanmod.logic.StabilizerItem;
import com.sandevistanmod.logic.SandevistanItem;
import com.google.common.collect.ImmutableSet;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SandevistanMod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SandevistanMod.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SandevistanMod.MODID);
    public static final DeferredRegister<PoiType> POIS =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SandevistanMod.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, SandevistanMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SandevistanMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SandevistanMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SandevistanMod.MODID);

    public static final Supplier<AttachmentType<SandevistanData>> SANDEVISTAN =
            ATTACHMENTS.register("sandevistan",
                    () -> AttachmentType.serializable(() -> new SandevistanData()).copyOnDeath().build());

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, name)));
    }

    public static final Supplier<SoundEvent> SND_ON = sound("sandevistan_on");
    public static final Supplier<SoundEvent> SND_OFF = sound("sandevistan_off");
    public static final Supplier<SoundEvent> PSY_SND_1 = sound("psy_snd_1");
    public static final Supplier<SoundEvent> PSY_SND_2 = sound("psy_snd_2");
    public static final Supplier<SoundEvent> ALERT = sound("alert");
    public static final Supplier<SoundEvent> PSY_MUSIC_1 = sound("psy_music_1");
    public static final Supplier<SoundEvent> PSY_MUSIC_2 = sound("psy_music_2");

    public static final DeferredItem<SandevistanItem> SANDEVISTAN_ITEM = ITEMS.register("sandevistan",
            () -> new SandevistanItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredHolder<MobEffect, GoodMindEffect> GOOD_MIND =
            EFFECTS.register("good_mind", () -> new GoodMindEffect());

    public static final DeferredItem<StabilizerItem> ESTABILIZADOR = ITEMS.register("estabilizador_neural",
            () -> new StabilizerItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<ImmunoItem> IMUNOSSUPRESSOR = ITEMS.register("imunossupressor",
            () -> new ImmunoItem(new Item.Properties().stacksTo(16)));

    public static final DeferredBlock<ClinicBlock> CLINICA = BLOCKS.register("clinica_ripperdoc",
            () -> new ClinicBlock(BlockBehaviour.Properties.of().strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .lightLevel(s -> 7)));
    public static final DeferredItem<BlockItem> CLINICA_ITEM = ITEMS.register("clinica_ripperdoc",
            () -> new BlockItem(CLINICA.get(), new Item.Properties()));

    /** Posto de trabalho do Ripperdoc: o aldeao que pega o bloco da clinica vira o doutor. */
    public static final ResourceKey<PoiType> RIPPERDOC_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(SandevistanMod.MODID, "ripperdoc"));
    public static final Supplier<PoiType> RIPPERDOC_POI = POIS.register("ripperdoc",
            () -> new PoiType(ImmutableSet.copyOf(CLINICA.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final Supplier<VillagerProfession> RIPPERDOC = PROFESSIONS.register("ripperdoc",
            () -> new VillagerProfession("ripperdoc",
                    holder -> holder.is(RIPPERDOC_POI_KEY), holder -> holder.is(RIPPERDOC_POI_KEY),
                    ImmutableSet.of(), ImmutableSet.of(), null));

    public static final DeferredItem<Item> CRISTAL = ITEMS.registerSimpleItem("cristal_neural", new Item.Properties());
    public static final DeferredItem<Item> FIBRA = ITEMS.registerSimpleItem("fibra_sinaptica", new Item.Properties());
    public static final DeferredItem<Item> VENTOINHA = ITEMS.registerSimpleItem("ventoinha", new Item.Properties());
    public static final DeferredItem<Item> NUCLEO = ITEMS.registerSimpleItem("nucleo", new Item.Properties());
    public static final DeferredItem<Item> SERVO = ITEMS.registerSimpleItem("servo_motor", new Item.Properties());
    public static final DeferredItem<Item> BATERIA = ITEMS.registerSimpleItem("bateria_energia", new Item.Properties());

    public static final DeferredItem<Item> CHIP = ITEMS.registerSimpleItem("chip", new Item.Properties());
    public static final DeferredItem<Item> NEURALINK = ITEMS.registerSimpleItem("neuralink", new Item.Properties());
    public static final DeferredItem<Item> ESPINHA = ITEMS.registerSimpleItem("espinha", new Item.Properties());
    public static final DeferredItem<Item> ACELERADOR = ITEMS.registerSimpleItem("acelerador", new Item.Properties());

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sandevistan"))
                    .icon(() -> new ItemStack(SANDEVISTAN_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(CHIP.get());
                        out.accept(CRISTAL.get());
                        out.accept(FIBRA.get());
                        out.accept(VENTOINHA.get());
                        out.accept(NUCLEO.get());
                        out.accept(SERVO.get());
                        out.accept(BATERIA.get());
                        out.accept(CLINICA_ITEM.get());
                        out.accept(NEURALINK.get());
                        out.accept(ESPINHA.get());
                        out.accept(ACELERADOR.get());
                        out.accept(SANDEVISTAN_ITEM.get());
                        out.accept(ESTABILIZADOR.get());
                        out.accept(IMUNOSSUPRESSOR.get());
                    })
                    .build());
}
