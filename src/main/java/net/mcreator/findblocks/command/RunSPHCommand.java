
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
import org.omg.CORBA.CTX_RESTRICT_SCOPE;
import java.awt.RadialGradientPaint;

@FindblocksModElements.ModElement.Tag
public class RunSPHCommand extends FindblocksModElements.ModElement {
	public RunSPHCommand(FindblocksModElements instance) {
		super(instance, 8);
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
		event.getCommandDispatcher().register(customCommand());
	}

	private LiteralArgumentBuilder<CommandSource> customCommand() {
		return LiteralArgumentBuilder.<CommandSource>literal("RunSPH")
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
			//Separates each argument into individual strings
			Arrays.stream(ctx.getInput().split("\\s+")).forEach(param -> {
				if (count[0] == 1) {
					cmdparams.put("radius", param);
				}
				if (count[0] == 0) {
					cmdparams.put("stresspos", param);
				}
				count[0]++;
			});
			{
				java.util.HashMap<String, Object> $_dependencies = new java.util.HashMap<>();
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
				//Argument 'position' -> 1, 'stress' -> 2, otherwise 0
				if(cmdparams.get("stresspos").equals("position")) {
					$_dependencies.put("stresspos",1);
				} else if(cmdparams.get("stresspos").equals("stress")) {
					$_dependencies.put("stresspos",2);
				} else {
					$_dependencies.put("stresspos",0);
				}
				FindblocksProcedure.executeProcedure($_dependencies);
			}
		}
		return 0;
	}
}
