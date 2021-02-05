package net.mcreator.findblocks.procedures;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.container.Slot;
import net.minecraft.inventory.container.Container;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.server.MinecraftServer;

import net.mcreator.findblocks.block.TepoliumBlockBlock;
import net.mcreator.findblocks.FindblocksModElements;

import java.util.function.Supplier;
import java.util.Map;
import net.minecraft.block.BlockState;

@FindblocksModElements.ModElement.Tag
public class ResetBlocksCommandExecutedProcedure extends FindblocksModElements.ModElement {
	public ResetBlocksCommandExecutedProcedure(FindblocksModElements instance) {
		super(instance, 9);
	}

	public static void executeProcedure(java.util.HashMap<String, Object> dependencies) {
		if (dependencies.get("entity") == null) {
			System.err.println("Failed to load dependency entity for procedure ResetBlocksCommandExecuted!");
			return;
		}
		if (dependencies.get("x") == null) {
			System.err.println("Failed to load dependency x for procedure ResetBlocksCommandExecuted!");
			return;
		}
		if (dependencies.get("y") == null) {
			System.err.println("Failed to load dependency y for procedure ResetBlocksCommandExecuted!");
			return;
		}
		if (dependencies.get("z") == null) {
			System.err.println("Failed to load dependency z for procedure ResetBlocksCommandExecuted!");
			return;
		}
		if (dependencies.get("world") == null) {
			System.err.println("Failed to load dependency world for procedure ResetBlocksCommandExecuted!");
			return;
		}
		Entity entity = (Entity) dependencies.get("entity");
		int x = (int) dependencies.get("x");
		int y = (int) dependencies.get("y");
		int z = (int) dependencies.get("z");
		int radius = (int) dependencies.get("rad");
		int count = 0;
		World world = (World) dependencies.get("world");
		//Loop over all positions in radius
		for (int indexX = -1 * radius; indexX < radius; indexX++) {
			for (int indexY = -1 * radius; indexY < radius; indexY++) {
				for (int indexZ = -1 * radius; indexZ < radius; indexZ++) {
					if (world.getBlockState(new BlockPos(((int) x) + indexX , ((int) y) + indexY , ((int) z) + indexZ)).toString().contains("wool")) { //If target block is any instance of wool
						BlockPos pos = new BlockPos(((int) x) + indexX , ((int) y) + indexY , ((int) z) + indexZ);
						BlockState state = TepoliumBlockBlock.block.getDefaultState();
						world.setBlockState(pos, state, 3); //Set back to Tepolium block
						count++;
					}
				}
			}
		}
		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
		if (mcserv != null) {
			mcserv.getPlayerList().sendMessage(new StringTextComponent(count + " Wool inside radius " + radius + " have been reset."));
		}
	}
}
