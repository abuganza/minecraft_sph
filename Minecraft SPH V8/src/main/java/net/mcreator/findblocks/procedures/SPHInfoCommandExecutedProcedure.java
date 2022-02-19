package net.mcreator.findblocks.procedures;

import net.mcreator.findblocks.FindblocksModElements;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.server.MinecraftServer;

@FindblocksModElements.ModElement.Tag
public class SPHInfoCommandExecutedProcedure extends FindblocksModElements.ModElement {
	public SPHInfoCommandExecutedProcedure(FindblocksModElements instance) {
		super(instance, 10);
	}

	public static void executeProcedure() {
	MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
		if (mcserv != null) {
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Welcome to the Minecraft SPH Stress Analysis tool! The command '/RunSPH' runs with 2 arguments, the first is either 'stress' or 'position', which indicates which you want to change the block color based on. The second argument is the radius from the player in which you want to search for Tepolium Blocks (Default 32 Blocks). As well, the '/resetblocks' command will change all wool back into Tepolium Blocks using that same radius argument."));
		}
	}
}
