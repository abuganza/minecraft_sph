
package net.mcreator.findblocks.command;

import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.common.util.FakePlayerFactory;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.command.Commands;
import net.minecraft.command.CommandSource;

import net.mcreator.findblocks.FindblocksModElements;

import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.lang.Math.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.World;
import net.minecraft.world.IWorld;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.math.BlockPos;
import net.mcreator.findblocks.FindblocksModVariables;
import net.mcreator.findblocks.FindblocksModElements;
import net.minecraft.client.renderer.debug.NeighborsUpdateDebugRenderer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.mcreator.findblocks.block.TepoliumBlockBlock;
import net.mcreator.findblocks.block.LoadBlockBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.AirBlock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.world.World;
import net.minecraft.world.IWorld;

@FindblocksModElements.ModElement.Tag
public class SPHPropertiesCommandCommand extends FindblocksModElements.ModElement {
	public SPHPropertiesCommandCommand(FindblocksModElements instance) {
		super(instance, 22);
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
		event.getCommandDispatcher().register(customCommand());
	}

	private LiteralArgumentBuilder<CommandSource> customCommand() {
		return LiteralArgumentBuilder.<CommandSource>literal("SPHproperties")
				.then(Commands.argument("arguments", StringArgumentType.greedyString()).executes(this::execute)).executes(this::execute);
	}

	private int execute(CommandContext<CommandSource> ctx) {
		ServerWorld world = ctx.getSource().getWorld();
		double x = ctx.getSource().getPos().getX();
		double y = ctx.getSource().getPos().getY();
		double z = ctx.getSource().getPos().getZ();
		Entity entity = ctx.getSource().getEntity();
		if (entity == null)
			entity = FakePlayerFactory.getMinecraft(world);
		HashMap<String, String> cmdparams = new HashMap<>();
		final int[] count = {-1};
		
		Arrays.stream(ctx.getInput().split("\\s+")).forEach(param -> {
				if (count[0] == 1) {
					try {
						Double.parseDouble(param);
						cmdparams.put("value", param);
					} catch (NumberFormatException e) {
						cmdparams.put("value", "0.0");
					}
				}
				if (count[0] == 0) {
					cmdparams.put("property", param);
				}
				count[0]++;
			});
		
		// Define and set simulation variables to default settings
		double h_threshold = 2.0; //define the initial threshold
		double alpha = 1.0; //hourglass control
		double Ehg = 1e8; //hourglass control [Pa]
		double mat0 = 1;
		double mat1 = 1e9;
		double mat2 = 0.4;
		double eta = 1e7;
		double rho = 15000; //[kg/m^3]
		double ult_stress = 15; //[kPa]
		double n_t_steps = 5000;
		double dt_global = 0.0001;
		double loading_time_percent = 20;
		double block_load = -900;
		double load_block_weight = -900;

		// Get file path for SPH files 
		Path currentRelativePath = Paths.get("");
		String PATH = currentRelativePath.toAbsolutePath().toString();
		String directoryName = "SPH_Files"; // Name of folder for csv and .property files
		File directory = new File(directoryName); 
		String filePath = PATH + "/" + directoryName + "/";
		String propString = filePath + "sph.properties"; // string path of properties file
		File propFile = new File(propString); // file path of properties

		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
					
		if(propFile.exists() && !propFile.isDirectory()) {
			
			// Load SPH properties
			Properties prop = new Properties();
			InputStream input = null;
			try {
			    input = new FileInputStream(propString);
				prop.load(input); // Load properties file
	
				// Update Sim variables from properties file
				h_threshold = Double.parseDouble(prop.getProperty("h_threshold")); //define the initial threshold
				alpha = Double.parseDouble(prop.getProperty("alpha")); //hourglass control
				Ehg = Double.parseDouble(prop.getProperty("Ehg")); //hourglass control [Pa]
				mat0 = Double.parseDouble(prop.getProperty("mat[0]"));
				mat1 = Double.parseDouble(prop.getProperty("mat[1]"));
				mat2 = Double.parseDouble(prop.getProperty("mat[2]"));
				eta = Double.parseDouble(prop.getProperty("eta"));
				rho = Double.parseDouble(prop.getProperty("rho")); //[kg/m^3]
				ult_stress = Double.parseDouble(prop.getProperty("ult_stress")); //[kPa]
			
				n_t_steps = Double.parseDouble(prop.getProperty("n_t_steps"));
				dt_global = Double.parseDouble(prop.getProperty("dt_global"));
				loading_time_percent = Double.parseDouble(prop.getProperty("loading_time_percent"));
				block_load = Double.parseDouble(prop.getProperty("block_load"));
				load_block_weight = Double.parseDouble(prop.getProperty("load_block_weight"));
				
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	
			String[] names = {"h_threshold", "alpha", "Ehg", "mat[0]", "mat[1]", "mat[2]", "eta", "rho", "ult_stress", "n_t_steps",
				"dt_global", "loading_time_percent", "block_load", "load_block_weight"};
			
			if (cmdparams.get("property") == null || cmdparams.get("property").equals("list")) {
				// list properties if arugment is null or "list"
				mcserv.getPlayerList().sendMessage(new StringTextComponent("#########################"));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("SPH Properties:"));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("h_threshold = " + h_threshold));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("alpha = " + alpha));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Ehg = " + Ehg));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("mat[] = {" + mat0 + ", " + mat1 + ", " + mat2 + "}"));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("eta = " + eta));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("rho = " + rho));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("ult_stress = " + ult_stress));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("n_t_steps = " + n_t_steps));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("dt_global = " + dt_global));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("loading_time_percent = " + loading_time_percent));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("block_load = " + block_load));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("load_block_weight = " + load_block_weight));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("#########################"));
	
			} else { // Change property

				// Check for valid input
				String propInput = cmdparams.get("property");
				boolean matchInput = false; // set default value for chekcing input property against possible properties
				double value = Double.parseDouble(cmdparams.get("value"));
				
				for (int i = 0; i < names.length; i++) {
					if (propInput.equals(names[i])) {
						matchInput = true;
					}
				}
	
				if (matchInput && value != 0.0) { // if input valid, change property
					try {
					
				    input = new FileInputStream(propString);
					prop.load(input); // Load properties file
					
					prop.setProperty((cmdparams.get("property")),(cmdparams.get("value")));
					
					FileOutputStream fos = new FileOutputStream(propString);
					prop.store(fos,"SPH Simulation Properties");
					fos.close();

					// output change to user on sucessful input
					mcserv.getPlayerList().sendMessage(new StringTextComponent("SPH Property '" + cmdparams.get("property") + "' changed to " + cmdparams.get("value")));
					
					} catch (IOException ex) {
						ex.printStackTrace();
					} finally {
						try {
							input.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				} else if (value == 0.0) {
					mcserv.getPlayerList().sendMessage(new StringTextComponent("Invalid Value")); // if input value is not valid
					
				} else {
					mcserv.getPlayerList().sendMessage(new StringTextComponent("Invalid Property Name")); // if input is not a valid property
				}
			}
		} else {
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Properties file does not exist. Run SPH simulation to generate sph.properties file"));
		}
		return 0;
	}
}
