
package net.mcreator.findblocks.command;

import net.minecraft.world.IWorld;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.common.util.FakePlayerFactory;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.command.Commands;
import net.minecraft.command.CommandSource;

import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.server.MinecraftServer;

import net.mcreator.findblocks.FindblocksModVariables;
import net.mcreator.findblocks.FindblocksModElements;
import net.mcreator.findblocks.block.TepoliumBlockBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.block.BlockState;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

@FindblocksModElements.ModElement.Tag
public class SetSpecialBlockCommand extends FindblocksModElements.ModElement {
	public SetSpecialBlockCommand(FindblocksModElements instance) {
		super(instance, 12);
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
		event.getCommandDispatcher().register(customCommand());
	}

	private LiteralArgumentBuilder<CommandSource> customCommand() {
		System.out.println("Command Recognized!!!!!");
		return LiteralArgumentBuilder.<CommandSource>literal("SetSpecialBlock")
				.then(Commands.argument("arguments", StringArgumentType.greedyString()).executes(this::execute)).executes(this::execute);
	}

	private int execute(CommandContext<CommandSource> ctx) {
		System.out.println("Execution Started!!!!!");
		ServerWorld world = ctx.getSource().getWorld();
		double x = ctx.getSource().getPos().getX();
		double y = ctx.getSource().getPos().getY();
		double z = ctx.getSource().getPos().getZ();
		Entity entity = ctx.getSource().getEntity();
		if (entity == null)
			entity = FakePlayerFactory.getMinecraft(world);
		HashMap<String, String> cmdparams = new HashMap<>();
		final int[] count = {-1};
		System.out.println("Parameters");
		//Separates each argument into individual strings
		Arrays.stream(ctx.getInput().split("\\s+")).forEach(param -> {
			if (count[0] == 2) {
				cmdparams.put("Z", param);
			}
			if (count[0] == 1) {
				cmdparams.put("Y", param);
			}
			if (count[0] == 0) {
				cmdparams.put("X", param);
			}
			count[0]++;
		});

		System.out.println("End Parameters");
		double special_x = Double.parseDouble(cmdparams.get("X"));
		double special_y = Double.parseDouble(cmdparams.get("Y"));
		double special_z = Double.parseDouble(cmdparams.get("Z"));
		
		if (((new ItemStack((world.getBlockState(new BlockPos(special_x,special_y,special_z)).getBlock())).getItem() == new ItemStack(TepoliumBlockBlock.block, (int) (1)).getItem()))) {
			IWorld world2 = (IWorld) world;
			FindblocksModVariables.MapVariables.get(world2).special_block_X = special_x;
			FindblocksModVariables.MapVariables.get(world2).special_block_Y = special_y;
			FindblocksModVariables.MapVariables.get(world2).special_block_Z = special_z;
			FindblocksModVariables.MapVariables.get(world2).syncData(world);
			MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
			if (mcserv != null) {
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Special Block set to X: " + cmdparams.get("X") + " Y: " +cmdparams.get("Y") + " Z: "+ cmdparams.get("Z")));
			}
		} else {
			MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
			if (mcserv != null) {
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Invalid Coordinates: Special Block Coordinates Must Target a Tepolium Block"));
			}
		}

		return 0;
	}
}
