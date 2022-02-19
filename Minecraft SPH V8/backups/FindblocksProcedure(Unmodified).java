package net.mcreator.findblocks.procedures;

import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.common.MinecraftForge;

import net.minecraft.world.World;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.ServerPlayerEntity;

import net.mcreator.findblocks.block.TepoliumBlockBlock;
import net.mcreator.findblocks.block.LoadBlockBlock;
import net.mcreator.findblocks.FindblocksModElements;
import com.sun.org.apache.xpath.internal.FoundIndex;
import com.sun.org.apache.xpath.internal.FoundIndex;
import java.util.List;
import java.util.ArrayList;
import java.awt.RadialGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RadialGradientPaint;

@FindblocksModElements.ModElement.Tag
public class FindblocksProcedure extends FindblocksModElements.ModElement {
	public FindblocksProcedure(FindblocksModElements instance) {
		super(instance, 2);
		MinecraftForge.EVENT_BUS.register(this);
	}

	public static void executeProcedure(java.util.HashMap<String, Object> dependencies) {
		if (dependencies.get("x") == null) {
			System.err.println("Failed to load dependency x for procedure Findblocks!");
			return;
		}
		if (dependencies.get("y") == null) {
			System.err.println("Failed to load dependency y for procedure Findblocks!");
			return;
		}
		if (dependencies.get("z") == null) {
			System.err.println("Failed to load dependency z for procedure Findblocks!");
			return;
		}
		if (dependencies.get("world") == null) {
			System.err.println("Failed to load dependency world for procedure Findblocks!");
			return;
		}
		if (dependencies.get("rad") == null) {
			System.err.println("Failed to load dependency rad for procedure Findblocks!");
			return;
		}
		int x = (int) dependencies.get("x");
		int y = (int) dependencies.get("y");
		int z = (int) dependencies.get("z");
		int radius = (int) dependencies.get("rad");
		World world = (World) dependencies.get("world");
		List<BlockPos> FoundBlocks = new ArrayList<BlockPos>();
		List<Integer> loaded_blocks = new ArrayList<Integer>();
		//Loop over all positions in radius
		for (int indexX = -1 * radius; indexX < radius; indexX++) {
			for (int indexY = -1 * radius; indexY < radius; indexY++) {
				for (int indexZ = -1 * radius; indexZ < radius; indexZ++) {
					if (((new ItemStack((world.getBlockState(new BlockPos(((int) x) + indexX , ((int) y) + indexY , ((int) z) + indexZ))).getBlock()))
						.getItem() == new ItemStack(TepoliumBlockBlock.block, (int) (1)).getItem())) { //If target block is Tepolium, add position to foundblocks list
						FoundBlocks.add(new BlockPos(((int) x) + indexX , ((int) y) + indexY , ((int) z) + indexZ));
						if (((new ItemStack((world.getBlockState(new BlockPos(((int) x) + indexX , ((int) y) + indexY + 1 , ((int) z) + indexZ))).getBlock()))
						.getItem() == new ItemStack(LoadBlockBlock.block, (int) (1)).getItem())) {
							loaded_blocks.add(FoundBlocks.size() - 1);
						}
					}
				}
			}
		}
		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
		if (mcserv != null) {
			mcserv.getPlayerList().sendMessage(new StringTextComponent(FoundBlocks.size() + " Blocks added to list in radius " + radius + " with " + loaded_blocks.size() + " Load Blocks added."));
		}
		SPHCodeProcedure.executeProcedure(dependencies, FoundBlocks, loaded_blocks); //Run SPH Code with foundblocks
	}

	@SubscribeEvent
	public void onChat(ServerChatEvent event) {
		ServerPlayerEntity entity = event.getPlayer();
		int i = (int) entity.getPosX();
		int j = (int) entity.getPosY();
		int k = (int) entity.getPosZ();
		java.util.HashMap<String, Object> dependencies = new java.util.HashMap<>();
		dependencies.put("x", i);
		dependencies.put("y", j);
		dependencies.put("z", k);
		dependencies.put("world", entity.world);
		dependencies.put("entity", entity);
		dependencies.put("event", event);
		dependencies.put("rad", 32);
		dependencies.put("stresspos",true);
		this.executeProcedure(dependencies);
	}
}
