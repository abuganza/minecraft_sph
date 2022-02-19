package net.mcreator.findblocks.command;

import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.command.Commands;
import net.minecraft.command.CommandSource;

import net.mcreator.findblocks.procedures.FindblocksProcedure;
import net.mcreator.findblocks.FindblocksModElements;

import java.util.HashMap;
import java.util.Arrays;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.awt.RadialGradientPaint;

@FindblocksModElements.ModElement.Tag
public class RunTrussCommand extends FindblocksModElements.ModElement {
	public RunTrussCommand(FindblocksModElements instance) {
		super(instance, 18);
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
		event.getCommandDispatcher().register(customCommand());
	}

	private LiteralArgumentBuilder<CommandSource> customCommand() {
		return LiteralArgumentBuilder.<CommandSource>literal("RunTruss")
				.then(Commands.argument("arguments", StringArgumentType.greedyString()).executes(this::execute)).executes(this::execute);
	}

	private int execute(CommandContext<CommandSource> ctx) {
		Entity entity = ctx.getSource().getEntity();
		if (entity != null) {
			int x = entity.getPosition().getX();
			int y = entity.getPosition().getY();
			int z = entity.getPosition().getZ();
			World world = entity.world;
			HashMap<String, String> cmdparams = new HashMap<>();
			final int[] count = {-1};
			
			Arrays.stream(ctx.getInput().split("\\s+")).forEach(param -> {
				if (count[0] == 0) {
					cmdparams.put("radius", param);
				}
				count[0]++;
			});
		
			{
			java.util.HashMap<String, Object> $_dependencies = new HashMap<>();
			$_dependencies.put("simtype", 1); // tells findblocks to run truss procedure
			$_dependencies.put("x", x);
			$_dependencies.put("y", y);
			$_dependencies.put("z", z);
			$_dependencies.put("world", world);
			//If radius is given, inputs that value, otherwise gives 32 blocks
			if(cmdparams.get("radius") == null) {
				$_dependencies.put("rad",32);
			} else {
				$_dependencies.put("rad", Integer.parseInt(cmdparams.get("radius")));
			}

			$_dependencies.put("stresspos",1); // temporary until add different arguments



			
			FindblocksProcedure.executeProcedure($_dependencies);
			}
		}
		return 0;
	}
}
