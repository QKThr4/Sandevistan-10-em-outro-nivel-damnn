package com.sandevistanmod.logic;

import com.sandevistanmod.data.Tx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Clinica de Ripperdoc: posto de trabalho do aldeao doutor, que e quem implanta e remove o Sandevistan. */
public class ClinicBlock extends Block {
    public ClinicBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Tx.t(ChatFormatting.AQUA, "clinic.info", SandevistanLogic.CLINIC_EMERALDS));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
