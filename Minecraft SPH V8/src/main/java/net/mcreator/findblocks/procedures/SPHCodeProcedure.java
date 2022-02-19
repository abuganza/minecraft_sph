package net.mcreator.findblocks.procedures;

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

import net.minecraft.item.ItemStack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.*;
import javax.print.attribute.standard.OrientationRequested;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.util.EasterHoliday;
import org.apache.logging.log4j.core.filter.TimeFilter;
import java.nio.file.Paths;

@FindblocksModElements.ModElement.Tag
public class SPHCodeProcedure extends FindblocksModElements.ModElement {
	public SPHCodeProcedure(FindblocksModElements instance) {
		super(instance, 7);
	}

	// Define and set simulation variables to default settings
	static double h_threshold = 2.0; //define the initial threshold
	
	static double alpha = 1.0; //hourglass control
	static double Ehg = 1e8; //hourglass control [Pa]
	static double mat[] = new double[] {1,1e9,0.4}; // change from 0.4
	static double eta = 1e7;
	static double rho = 15000; //[kg/m^3]
	static double ult_stress = 15; //[kPa]

	static double n_t_steps = 5000;
	static double dt_global = 0.0001;
	static double loading_time_percent = 20;
	static double block_load = -900;
	static double load_block_weight = -900;

	public static void executeProcedure(java.util.HashMap<String, Object> dependencies, List<BlockPos> foundBlocks, List<Integer> loaded_blocks) {
		//Particle initialization and neighbors
		World world = (World) dependencies.get("world");
		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
		if((int) dependencies.get("stresspos") == 0) {
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Invalid Argument Input"));
			return;
		}

		// File saving setup
		Path currentRelativePath = Paths.get("");
		String PATH = currentRelativePath.toAbsolutePath().toString();
		System.out.println("Current absolute path is: " + PATH);
		String directoryName = "SPH_Files"; // Name of folder for csv and .property files
		File directory = new File(directoryName); 
		if (! directory.exists()){ // Create SPH_Files folder if does not exist
			directory.mkdir();
		}
		String filePath = PATH + "/" + directoryName + "/";
		filePath = getProperties(filePath);
		Path pathFile = Paths.get(filePath); // sets path for CSV outputs

		//All Pre-Checks Passed
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Running SPH...."));

		double start = System.currentTimeMillis(); // Timer start
		boolean longsim = (boolean) dependencies.get("shortlong");
		
		int n_particles = foundBlocks.size();
		VectorObj[] particle_X = new VectorObj[n_particles];
		VectorObj[] particle_x = new VectorObj[n_particles];
		double[] particle_Vol = new double[n_particles];
		int special_block = -1;
		IWorld world2 = (IWorld) world;
		double x_special = (double) FindblocksModVariables.MapVariables.get(world2).special_block_X; //Coordinates of Block that is written to CSV
		double y_special = (double) FindblocksModVariables.MapVariables.get(world2).special_block_Y;
		double z_special = (double) FindblocksModVariables.MapVariables.get(world2).special_block_Z;
		for(int i = 0; i < foundBlocks.size(); i++){
			particle_X[i] = new VectorObj(new double[][] {new double[] {foundBlocks.get(i).getX()},new double[] {foundBlocks.get(i).getY()},new double[] {foundBlocks.get(i).getZ()}});
			particle_Vol[i] = 1.0;
			if(particle_X[i].getData()[0][0] == x_special && particle_X[i].getData()[1][0] == y_special && particle_X[i].getData()[2][0] == z_special){
				special_block = i;
			}
		}
		if(special_block == -1){
			special_block = getDefaultSpecial(particle_X);
			double[][] default_special_coords = particle_X[special_block].getData();
			x_special = default_special_coords[0][0];
			y_special = default_special_coords[1][0];
			z_special = default_special_coords[2][0];
		}
		//Neighbor List
		boolean[][] Neighbor_list = findNeighbors(particle_X);
		//Velocity and acceleration 
		VectorObj[] particle_V = new VectorObj[n_particles]; //[mm/s]
		VectorObj[] particle_A = new VectorObj[n_particles]; //[mm/s^2]
		//Deformation gradient and stress
		Matrix[] particle_FF = new Matrix[n_particles]; //[]
		Matrix[] particle_PP = new Matrix[n_particles]; //[MPa]
		Matrix[] particle_Ainv = new Matrix[n_particles];
		//Internal forces 
		VectorObj[] particle_Fint = new VectorObj[n_particles]; //[N]
		VectorObj[] particle_Fext = new VectorObj[(int) n_t_steps];
		//Boundary Blocks
		List<Integer> boundary_Blocks = findBounds(foundBlocks, (World) dependencies.get("world"));

		//Initializing all Matrix arrays
		for (int i = 0; i < n_particles; i++){
			particle_x[i] = particle_X[i];
			particle_V[i] = new VectorObj();
			particle_A[i] = new VectorObj();
			particle_FF[i] = Matrix.identity(3);
			particle_PP[i] = Matrix.zerosMatrix(3, 3);
		}

		//Checks for only 3D geometry
		boolean[] geometryCheck = new boolean[n_particles + 1]; // First array position tells if pass or failed 3d check
		geometryCheck[0] = true;
		geometryCheck = check3D(foundBlocks, (World) dependencies.get("world"), geometryCheck, n_particles);
		System.out.println("Geometry Check: " + geometryCheck[0]);

		//Loop over time (Start  of SPH calculations)
		if(geometryCheck[0]){
			try {
				// Set up CSV Files
            	PrintWriter writer = new PrintWriter(new File(filePath + "critical_point_data.csv")); //Change File Path up at Line 70
	            StringBuilder ab = new StringBuilder();
	            ab.append("Time,y,");
	            ab.append("Fint [0][0], ");
	            ab.append("Fint [1][0], ");
	            ab.append("Fint [2][0], ");
				ab.append("Fext [0][0], ");
				ab.append("Fext [1][0], ");
				ab.append("Fext [2][0], ");
	            ab.append("sigma [0][0],");
				ab.append("sigma [1][0],");
				ab.append("sigma [2][0],");
				ab.append("sigma [0][1],");
				ab.append("sigma [1][1],");
				ab.append("sigma [2][1],");
				ab.append("sigma [0][2],");
				ab.append("sigma [1][2],");
				ab.append("sigma [2][2],");
				ab.append("Loading,");
				ab.append("\n");
				writer.write(ab.toString());


				PrintWriter writer2 = new PrintWriter(new File(filePath + "sim_properties.csv"));
				StringBuilder bb = new StringBuilder();
				bb.append("h_threshold, alpha, Ehg, mat[0], mat[1], mat[2], eta, rho, n_t_steps, dt_global, loading_time_percent,");
				bb.append("\n");
				bb.append(Double.toString(h_threshold) + ",");
				bb.append(Double.toString(alpha) + ",");
				bb.append(Double.toString(Ehg) + ",");
				bb.append(Double.toString(mat[0]) + ",");
				bb.append(Double.toString(mat[1]) + ",");
				bb.append(Double.toString(mat[2]) + ",");
				bb.append(Double.toString(eta) + ",");
				bb.append(Double.toString(rho) + ",");
				bb.append(Double.toString(n_t_steps) + ",");
				bb.append(Double.toString(dt_global) + ",");
				bb.append(Double.toString(loading_time_percent) + ",");
				
				writer2.write(bb.toString());
				writer2.flush();
				writer2.close();

				PrintWriter writer3 = new PrintWriter(new File(filePath + "Bound_block_internal_forces.csv"));
				StringBuilder cc = new StringBuilder();
				cc.append("x,y,z,Fint[0],Fint[1],Fint[2],Index,");
				cc.append("\n");
				writer3.write(cc.toString());

				PrintWriter writer4 = new PrintWriter(new File(filePath + "block_coordinates.csv"));
				StringBuilder dd = new StringBuilder();
				dd.append("x, y, z, Index,\n");
				writer4.write(dd.toString());

				for (int i = 0; i < n_particles; i++){
					StringBuilder ee = new StringBuilder();
					ee.append(Double.toString(foundBlocks.get(i).getX()) + ",");
					ee.append(Double.toString(foundBlocks.get(i).getY()) + ",");
					ee.append(Double.toString(foundBlocks.get(i).getZ()) + ",");
					ee.append(Integer.toString(i));
					ee.append("\n");
					writer4.write(ee.toString());
				}

				writer4.flush();
				writer4.close();
				
		//Begin SPH calculations
				double dt = dt_global; // changed from 0.0015
				int nt_stable = 0;
				int nt_finished = 0;
				boolean look_for_stop = false;
				
				for (int ti = 0; ti < n_t_steps; ti++){
					double ext_force = 0;
					double load_block_force = 0;
					double step_factor = 0;

					for (int pi = 0; pi < n_particles; pi++){
						Matrix[] FAinv = calculate_FAinv(pi,particle_X,particle_x,Neighbor_list); //Index 0 = F, Index 1 = Ainv

						Matrix P = calculate_P(FAinv[0],particle_FF[pi],dt,mat);
						P = P.dot(FAinv[1]);
						
						particle_FF[pi] = FAinv[0]; //FAinv[0];
						particle_PP[pi] = P;
						particle_Ainv[pi] = FAinv[1];
						}
						
						for (int pi = 0; pi < n_particles; pi++){
						particle_Fint[pi] = calculate_Fint(pi,particle_X,particle_x,Neighbor_list,particle_PP,particle_FF);
	
						//Force stepping
						step_factor = ti / (loading_time_percent / 100 * n_t_steps);

						if (step_factor > 1) {
							step_factor = 1;
						}
						ext_force = block_load * step_factor;
						load_block_force = load_block_weight * step_factor;
						
						VectorObj Fext = new VectorObj(new double[][]{new double[] {0},new double[] {ext_force}, new double[] {0}});
						if(loaded_blocks.contains(pi)){
							Fext = new VectorObj(new double[][]{new double[] {0}, new double[] {load_block_force + ext_force}, new double[] {0}});
						}

						particle_Fext[ti] = Fext;
						
						//Update kinematic properties
						particle_V[pi] = new VectorObj(particle_V[pi].plus(particle_A[pi].scale(dt / 2.0)).getData());
						particle_x[pi] = new VectorObj(particle_x[pi].plus(particle_V[pi].scale(dt)).getData());
						particle_A[pi] = new VectorObj((particle_Fint[pi].plus(Fext)).scale(1.0 / (particle_Vol[pi]*rho)).getData());
						particle_V[pi] = new VectorObj(particle_V[pi].plus(particle_A[pi].scale(dt / 2.0)).getData());

						if(!longsim && ti > n_t_steps*loading_time_percent/100 && nt_stable == 0 && particle_V[special_block].getData()[1][0] > 0.00005){
							nt_stable = ti;
							mcserv.getPlayerList().sendMessage(new StringTextComponent("***Bottom Found*** " + Integer.toString(nt_stable)));
							look_for_stop = true;
						}  //Checks for particle velocity=~0 when short sim is on

						if(look_for_stop==true){
							double total_weight = -1 * (block_load * n_particles + load_block_force * loaded_blocks.size());
							if(combined_reaction(particle_Fint,boundary_Blocks)<total_weight){
								mcserv.getPlayerList().sendMessage(new StringTextComponent("***Stop Here*** " + ti));
								nt_finished = ti;
								n_t_steps = ti + 5;
								look_for_stop = false;
							}
						}
					}
					
					//Enforce Boundaries
					for (int ebc = 0; ebc < boundary_Blocks.size(); ebc++){
						//System.out.println("Boundary blocks: " + boundary_Blocks.get(ebc));
						particle_x[boundary_Blocks.get(ebc)] = particle_X[boundary_Blocks.get(ebc)];
					}
					try {
						//appendPosToCSV(writer, particle_x, particle_X, ti*dt);
						Matrix P_correct = particle_PP[special_block].times(particle_Ainv[special_block].inverse());
						double detF_special = Matrix.det(particle_FF[special_block].getData(),3);
        				Matrix sigma = P_correct.times(particle_FF[special_block].transpose()).scale(1 / detF_special);
        				
						appendStressToCSV(writer, sigma, particle_x[special_block], particle_X[special_block], ti, particle_Fint[special_block], particle_Fext[ti], ext_force);

						if (ti == n_t_steps -1){
							appendBoundForcesToCSV(writer3, boundary_Blocks, particle_X, particle_Fint);
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				writer.flush();
				writer.close();
				writer3.flush();
				writer3.close();

				//SPH Calculations complete, changing colors
				if((int) dependencies.get("stresspos") == 1) { //Position
					mcserv.getPlayerList().sendMessage(new StringTextComponent("Sim Complete,"));
					colorChangeX(particle_X, particle_x, foundBlocks, (World) dependencies.get("world"));

				} else { //Stress
					mcserv.getPlayerList().sendMessage(new StringTextComponent("Sim Complete, Changing Block Colors based on von Mises Stress"));
					colorChangeStress(particle_FF, particle_PP, particle_Ainv, foundBlocks, (World) dependencies.get("world"));
				}

				// Time calculation / formatting
				double finish = System.currentTimeMillis();
				double timeElapsed = (finish - start) / 1000;
       			String minutes = String.format("%.0f",Math.floor(timeElapsed / 60));
       			String seconds = String.format("%.0f", timeElapsed % 60);
       			
       			// Display Info to player
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Elapsed Time: " + minutes + ":" + seconds));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Number of blocks: " + n_particles));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Total Weight + Load: " + -1 * (block_load * n_particles + load_block_weight * loaded_blocks.size())));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Bottom out: " + nt_stable));
				mcserv.getPlayerList().sendMessage(new StringTextComponent("End: " + (nt_finished + 5)));
				
	        } catch (Exception e) {
	            // TODO: handle exception
    	        e.printStackTrace();
    	    }
		} 
		
		else if (!geometryCheck[0]) {
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Error: 2D geometry detected, unable to run simulation. Convert to 3D"));
			colorChangeGeometry(foundBlocks, (World) dependencies.get("world"), geometryCheck, n_particles);
			
		}
	}
	
	//Extra Functions
	public static boolean[][] findNeighbors(VectorObj[] particles) { //Returns nparticle x nparticle boolean array, if findNeighbors[pi][pj] == true, they are within h_thresholf distance from eachother and pj is a neighbor of pi
		boolean[][] neighbs = new boolean[particles.length][particles.length];
		for(int pi = 0; pi < particles.length; pi++) {
			int count = 0;
			for(int pj = pi+1; pj < particles.length; pj++) {
				double[][] dif = particles[pi].minus(particles[pj]).getData();
				double d = Math.sqrt(dif[0][0]*dif[0][0] + dif[1][0]*dif[1][0] + dif[2][0]*dif[2][0]);
				if(d < h_threshold) {
					neighbs[pi][pj] = true;
					neighbs[pj][pi] = true;
					count++;
				}				
			}
		}
		return neighbs;
	}

	public static double combined_reaction(VectorObj[] particle_Fint,List<Integer> boundary_Blocks){
		double total_force = 0.0;
		for(int i = 0;i < boundary_Blocks.size();i++){
			total_force += particle_Fint[boundary_Blocks.get(i)].getData()[1][0];
		}
		return (total_force*-1);
	}

	public static int getDefaultSpecial(VectorObj[] particle_X){
		double xavg = 0;
		double yavg = 0;
		double zavg = 0;
		int closest = 0;
		double min_dist = 999999999; 
		for(int i = 0;i<particle_X.length;i++){
			double[][] pos = particle_X[i].getData();
			xavg += pos[0][0];
			yavg += pos[1][0];
			zavg += pos[2][0];
		}
		xavg = xavg / particle_X.length;
		yavg = yavg / particle_X.length;
		zavg = zavg / particle_X.length;
		for(int i = 0;i<particle_X.length;i++){
			double[][] pos2 = particle_X[i].getData();
			double dis_sq = Math.pow(pos2[0][0]-xavg,2) + Math.pow(pos2[1][0]-yavg,2) + Math.pow(pos2[2][0]-zavg,2);
			if(dis_sq < min_dist){
				min_dist = dis_sq;
				closest = i;
			}
		}
		return closest;
	}

	public static void appendPosToCSV(PrintWriter writer, VectorObj[] particle_x, VectorObj[] particle_X, double time){
		StringBuilder sb = new StringBuilder();
		sb.append(Double.toString(time) + ",");
		for(int i = 0; i < particle_x.length; i++){
			double[][] vec = particle_x[i].getData();
			double[][] og_pos = particle_X[i].getData();
			sb.append(Double.toString(vec[1][0]-og_pos[1][0]));
			sb.append(",");
		}
		sb.append("\n");
		writer.write(sb.toString());
	}

	public static void appendStressToCSV(PrintWriter writer, Matrix i_PP, VectorObj particle_x, VectorObj particle_X, double time, Matrix Fint, Matrix Fext, double loading){
		double[][] data_PP = i_PP.getData();
		double[] xyz = new double[] {particle_x.getData()[0][0],particle_x.getData()[1][0],particle_x.getData()[2][0]};
		
		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
		
		xyz[0] = xyz[0] - particle_X.getData()[0][0];
		xyz[1] = xyz[1] - particle_X.getData()[1][0];
		xyz[2] = xyz[2] - particle_X.getData()[2][0];
		StringBuilder sb = new StringBuilder();
		sb.append(Double.toString(time) + ",");



		if (Double.isNaN(xyz[1]) || Double.isInfinite(data_PP[2][2]) ){
			xyz[1] = 0;
			data_PP[0][0] = 0;
			data_PP[1][0] = 0;
			data_PP[2][0] = 0;
			data_PP[0][1] = 0;
			data_PP[1][1] = 0;
			data_PP[2][1] = 0;
			data_PP[0][2] = 0;
			data_PP[1][2] = 0;
			data_PP[2][2] = 0;
			}

		double [][] data_Fint = Fint.getData();
		double [][] data_Fext = Fext.getData();
		
		sb.append(xyz[1] + ",");
		sb.append(Double.toString(data_Fint[0][0]) + ",");
		sb.append(Double.toString(data_Fint[1][0]) + ",");
		sb.append(Double.toString(data_Fint[2][0]) + ",");
		sb.append(Double.toString(data_Fext[0][0]) + ",");
		sb.append(Double.toString(data_Fext[1][0]) + ",");
		sb.append(Double.toString(data_Fext[2][0]) + ",");
		sb.append(Double.toString(data_PP[0][0])+",");
		sb.append(Double.toString(data_PP[1][0])+",");
		sb.append(Double.toString(data_PP[2][0])+",");
		sb.append(Double.toString(data_PP[0][1])+",");
		sb.append(Double.toString(data_PP[1][1])+",");
		sb.append(Double.toString(data_PP[2][1])+",");
		sb.append(Double.toString(data_PP[0][2])+",");
		sb.append(Double.toString(data_PP[1][2])+",");
		sb.append(Double.toString(data_PP[2][2])+",");
		sb.append(Double.toString(loading));
		sb.append("\n");
		writer.write(sb.toString());

		String deflect = String.format("%.6f", xyz[1]);
		String time2 = String.format("%.0f", time);
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Time Steps: " + time2 + " |  Deflection: " + deflect + " m"));
	}

	public static void appendBoundForcesToCSV(PrintWriter writer, List<Integer> boundary_Blocks, VectorObj[] particle_X, Matrix[] Fint){
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<boundary_Blocks.size(); i++){
			int index = boundary_Blocks.get(i);
			double[] xyz = new double[] {particle_X[index].getData()[0][0],particle_X[index].getData()[1][0],particle_X[index].getData()[2][0]};
			double [][] data_Fint = Fint[index].getData();
			sb.append(xyz[0] + ",");
			sb.append(xyz[1] + ",");
			sb.append(xyz[2] + ",");
			sb.append(Double.toString(data_Fint[0][0]) + ",");
			sb.append(Double.toString(data_Fint[1][0]) + ",");
			sb.append(Double.toString(data_Fint[2][0]) + ",");
			sb.append(index + ",");
			sb.append("\n");
		}
		writer.write(sb.toString());
	}


	public static List<Integer> findBounds(List<BlockPos> foundBlocks, World world) { //Returns indices of blocks that have anything other than tepolium blocks or air directly below them
		List<Integer> r = new ArrayList<Integer>();
		for(int i = 0; i < foundBlocks.size(); i++){

			// Get block position for 6 surrounding blocks
			BlockState underBlock = world.getBlockState(new BlockPos((int) foundBlocks.get(i).getX(), (int) (foundBlocks.get(i).getY() - 1), (int) foundBlocks.get(i).getZ()));
			BlockState overBlock = world.getBlockState(new BlockPos((int) foundBlocks.get(i).getX(), (int) (foundBlocks.get(i).getY() + 1), (int) foundBlocks.get(i).getZ()));
			BlockState northPosBlock = world.getBlockState(new BlockPos((int) (foundBlocks.get(i).getX()+1), (int) (foundBlocks.get(i).getY()), (int) (foundBlocks.get(i).getZ())));
			BlockState northNegBlock = world.getBlockState(new BlockPos((int) (foundBlocks.get(i).getX()-1), (int) (foundBlocks.get(i).getY()), (int) (foundBlocks.get(i).getZ())));
			BlockState eastPosBlock = world.getBlockState(new BlockPos((int) (foundBlocks.get(i).getX()), (int) (foundBlocks.get(i).getY()), (int) (foundBlocks.get(i).getZ()+1)));
			BlockState eastNegBlock = world.getBlockState(new BlockPos((int) (foundBlocks.get(i).getX()), (int) (foundBlocks.get(i).getY()), (int) (foundBlocks.get(i).getZ()-1)));

			BlockState[] stateVec = new BlockState[]{underBlock, overBlock, northPosBlock, northNegBlock, eastPosBlock, eastNegBlock};
			
			// Non-Binding Blocks
			BlockState waterBlock = Blocks.WATER.getDefaultState();
			BlockState airBlock = Blocks.AIR.getDefaultState();
			BlockState tepBlock = TepoliumBlockBlock.block.getDefaultState();
			BlockState loadBlock = LoadBlockBlock.block.getDefaultState();
			BlockState lavaBlock = Blocks.LAVA.getDefaultState();

			BlockState[] bindVec = new BlockState[] {waterBlock, airBlock, tepBlock, loadBlock, lavaBlock};

			// Check all block states against the binding blocks array
			for (int j = 0; j < stateVec.length; j++){
				int z = 0;
				for(int k = 0; k < bindVec.length; k++){
					if (stateVec[j].getBlock() != bindVec[k].getBlock()){
						z = z + 1;
					}
				}
				if (z == bindVec.length){
					r.add(i);
					break; // exit early if any block is bounded
				}
			}
		}
		return r;
	}


	public static boolean[] check3D(List<BlockPos> foundBlocks, World world, boolean[] geometryCheck, int n_particles) { // Checks structure for only 3D geometry
		
		BlockState tepBlock = TepoliumBlockBlock.block.getDefaultState(); // save tepolium block to variable
		
		
		for (int i = 0; i < foundBlocks.size(); i++) {
			
			boolean passfail = false; // Initialize pass/fail parameter - default to fail
			int count = 0; // Number of neighbor blocks + origin bock (0,0,0)
			int[][] cords = new int [27][3]; // Initialize rray of all possible block coordiantes

			for (int x = -1; x < 2; x++) {
				for (int y = -1; y < 2; y++) {
					for (int z = -1; z < 2; z++) {
						BlockState currentBlock = world.getBlockState(new BlockPos((int) foundBlocks.get(i).getX() + x, (int) foundBlocks.get(i).getY() + y, (int) foundBlocks.get(i).getZ() + z));

						if (currentBlock.getBlock() == tepBlock.getBlock()) {
							cords[count][0] = x;
							cords[count][1] = y;
							cords[count][2] = z;
							count++;
						}
					}
				}
			}

			if (count > 3) {		
				boolean colinear = true;
				int k = 2; // starting index for 3rd coordinate
				while (colinear && k < count) {
					int crossX = ((cords[1][1] - cords[0][1])*(cords[k][2] - cords[0][2])) - ((cords[k][1] - cords[0][1])*(cords[1][2] - cords[0][2]));
					int crossY = ((cords[k][0] - cords[0][0])*(cords[1][2] - cords[0][2])) - ((cords[1][0] - cords[0][0])*(cords[k][2] - cords[0][2]));
					int crossZ = ((cords[1][0] - cords[0][0])*(cords[k][1] - cords[0][1])) - ((cords[k][0] - cords[0][0])*(cords[1][1] - cords[0][1]));

					if (crossX + crossY + crossZ == 0) {
						k++; // if cords are colinear
					}
					else {
						colinear = false; // if cords are not colinear
					}
				}
				
					// Vectors for plane definition
					int a1 = cords[1][0] - cords[0][0]; // x2 - x1
					int b1 = cords[1][1] - cords[0][1]; // y2 - y1
					int c1 = cords[1][2] - cords[0][2]; // z2 - z1
					int a2 = cords[k][0] - cords[0][0]; // x3 - x1
					int b2 = cords[k][1] - cords[0][1]; // y3 - y1
					int c2 = cords[k][2] - cords[0][2]; // z3 - z1

					// Equation of plane
					int a = b1 * c2 - b2 * c1;
					int b = a2 * c1 - a1 * c2;
					int c = a1 * b2 - b1 * a2;
					int d = (- a * cords[0][0] - b * cords[0][1] - c * cords[0][2]);

					// Loop over remaining points for colinearity
					for (int j = 0; j < (count); j++) {
						if ( (a * cords[j][0]) + (b * cords[j][1]) + (c * cords[j][2]) + d != 0) {
							passfail = true; // if any point is non-coplanar, pass the block check
						}
					}
			}

			if (passfail) {
				geometryCheck[i + 1] = true;
			}
			else {
				geometryCheck[0] = false;	// sets overall check to fail
				geometryCheck[i+1] = false; // sets current block to fail (for color change)
			}

		}

		return geometryCheck;
	}
	
	//SPH Functions
	public static double eval_W(VectorObj Rij, double h_threshold1) {
		double x = Rij.getData()[0][0];
		double y = Rij.getData()[1][0];
		double z = Rij.getData()[2][0];
		double Rijmag = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
		return ((15.0/Math.PI/Math.pow(h_threshold1,6)) * Math.pow(h_threshold1-Rijmag,3));
	}
	
	public static VectorObj eval_gradW(VectorObj Rij, double h_threshold) {
		double x = Rij.getData()[0][0];
		double y = Rij.getData()[1][0];
		double z = Rij.getData()[2][0];
		double Rijmag = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
		double scale = -3.0 * ((15.0/Math.PI/Math.pow(h_threshold,6)) * Math.pow(h_threshold-Rijmag,2)) / Rijmag;
		double Rijnew[][] = new double[3][1];
		Rijnew[0][0] = x*scale;
		Rijnew[1][0] = y*scale;
		Rijnew[2][0] = z*scale;
		VectorObj ret = new VectorObj(Rijnew);
		return ret;
	}
	
	//Returns Index 0 = F, Index 1 = Ainv
	public static Matrix[] calculate_FAinv(int pi, VectorObj[] particle_X, VectorObj[] particle_x, boolean[][] Neighbor_list) {
		VectorObj Xi = particle_X[pi];
		VectorObj xi = particle_x[pi];
		Matrix F = new Matrix(3,3);
		Matrix A = new Matrix(3,3);
		for(int pj = 0; pj < particle_X.length; pj++) {
			if(Neighbor_list[pi][pj]) {
				VectorObj Xj = particle_X[pj];
				VectorObj xj = particle_x[pj];
				double Volj = 1.0;
				VectorObj rij = new VectorObj(xj.minus(xi).getData());
				VectorObj Rij = new VectorObj(Xj.minus(Xi).getData());
				VectorObj scaled_gradW = eval_gradW(Rij,h_threshold).scale(Volj);
				F = F.plus(rij.times(scaled_gradW.transpose()));
				A = A.plus(Rij.times(scaled_gradW.transpose()));
			}
		}
		Matrix Ainv = A.inverse();
		F = F.dot(Ainv);
		Matrix[] ret = new Matrix[] {F,Ainv};
		return ret;
	}
	
	public static VectorObj calculate_Fint(int pi, VectorObj[] particle_X, VectorObj[] particle_x, boolean[][] Neighbor_list, Matrix[] particle_PP, Matrix[] particle_FF) {
		
		double Voli = 1.0; //particle_Vol[pi];
		VectorObj Xi = particle_X[pi];
		VectorObj xi = particle_x[pi];

		double [] [] initial = new double [3][1];
		initial[0][0] = 0.0;
		initial[1][0] = 0.0;
		initial[2][0] = 0.0;
		VectorObj Fint = new VectorObj(initial);
		VectorObj Fhg = new VectorObj(initial);
		
		
		for(int pj = 0; pj < particle_X.length; pj++) {
			if(Neighbor_list[pi][pj]) {
				double Volj = 1.0; //particle_Vol[pj];
				VectorObj Xj = particle_X[pj];
				VectorObj xj = particle_x[pj];
				VectorObj rij = new VectorObj(xj.minus(xi).getData());
				VectorObj Rij = new VectorObj(Xj.minus(Xi).getData());
				double rijmag = Math.sqrt(Math.pow(rij.getData()[0][0], 2) + Math.pow(rij.getData()[1][0], 2) + Math.pow(rij.getData()[2][0], 2));
				double Rijmag = Math.sqrt(Math.pow(Rij.getData()[0][0], 2) + Math.pow(Rij.getData()[1][0], 2) + Math.pow(Rij.getData()[2][0], 2));				
				VectorObj gradW = eval_gradW(Rij,h_threshold);
				Fint = new VectorObj(Fint.plus(particle_PP[pi].plus(particle_PP[pj]).mat_dot_vector(gradW).scale(Voli*Volj)).getData());				
				
				//hourglass control
				VectorObj rij_pred = particle_FF[pi].mat_dot_vector(Rij);
				VectorObj rji_pred = particle_FF[pj].mat_dot_vector(Rij.scale(-1.0));
				double deltai = (new VectorObj(rij_pred.minus(rij).getData())).dot(rij);
				deltai = deltai / rijmag;
				double deltaj = (new VectorObj(rji_pred.plus(rij).getData())).dot(rij.scale(-1.0));
				deltaj = deltaj / rijmag;
				Fhg = new VectorObj(Fhg.plus(rij.scale(-0.5*alpha*Ehg*Voli*Volj*eval_W(Rij,h_threshold)*(deltai+deltaj) / (rijmag*Rijmag*Rijmag))).getData());
			}
		}
		return new VectorObj(Fint.plus(Fhg).getData());
	}
	
	public static Matrix calculate_P(Matrix F, Matrix Ft, double dt, double[] mat) {
		if(mat[0] == 0) {
			double mu = mat[1];
			double lam = mat[2];
			double J = Matrix.det(F);
			Matrix C = F.transpose().dot(F);
			Matrix Cinv = C.inverse();
			Matrix I = Matrix.identity(3);
			Matrix S = I.minus(Cinv).scale(mu).plus(Cinv.scale(lam*Math.log(J)));
			Matrix Fdot = F.minus(Ft).scale(1.0 / dt);
			Matrix FFinv = F.inverse();
			Matrix LL = Fdot.dot(FFinv);
			Matrix dd = LL.plus(LL.transpose()).scale(0.5);
			Matrix P = F.dot(S).plus(dd.dot(FFinv.transpose()).scale(2*J*eta));
			return P;

		 }  else if (mat[0] == 1) {
			double Esv = mat[1];
			double nusv = mat[2];
			Matrix C = F.transpose().dot(F);
			Matrix Cinv = C.inverse();
			Matrix I = Matrix.identity(3);
			Matrix E = C.minus(I).scale(0.5);
			double [][] eData = E.getData();  
			double trE = eData[0][0] + eData[1][1] + eData[2][2];
			double J = Matrix.det(F);
			double lame_lam = Esv * nusv / ((1+nusv)*(1-nusv));
			double lame_mu = Esv / (2*(1+nusv));
			Matrix S = I.scale(lame_lam*trE).plus(E.scale(2*lame_mu));
			Matrix Fdot = F.minus(Ft).scale(1.0 / dt);
			Matrix FFinv = F.inverse();
			Matrix LL = Fdot.dot(FFinv);
			Matrix dd = LL.plus(LL.transpose()).scale(0.5);
			double [][] dData = dd.getData();
			Matrix ddprime = dd.minus(I.scale(1.0/3.0 * (dData[0][0] + dData[1][1] + dData[2][2]))); //change from .plus to .minus
			Matrix P = F.dot(S).plus(ddprime.dot(FFinv.transpose()).scale(2*J*eta)); //changed to ddprime
			
			return P;
		} else {
			return null;
		}
	}
	//Color Change Functions
	public static void colorChangeStress(Matrix[] particle_FF, Matrix[] particle_PP, Matrix[] particle_Ainv, List<BlockPos> foundBlocks, World world) {
        double[] vmstress = new double[particle_FF.length];
        double min = 999999999;
        double max = 0;
        for(int i = 0; i < particle_FF.length; i++) { //Calculate von Mises Stress
        	double detF = Matrix.det(particle_FF[i].getData(),3);
        	Matrix P_correct = particle_PP[i].times(particle_Ainv[i].inverse());
        	double[][] sigma = P_correct.times(particle_FF[i].transpose()).scale(1 / detF).getData();
        	vmstress[i] = Math.sqrt(Math.pow(sigma[0][0] - sigma[1][1], 2) + Math.pow(sigma[1][1] - sigma[2][2], 2) + Math.pow(sigma[2][2] - sigma[0][0], 2) + 6*(Math.pow(sigma[1][2], 2) + Math.pow(sigma[0][2], 2) + Math.pow(sigma[0][1], 2))) / Math.sqrt(2);
        	if(vmstress[i] < min){
        		min = vmstress[i];
        	}
        	if(vmstress[i] > max){
        		max = vmstress[i];
        	}
        }
        String maxStr = String.format("%.3f",max / 1000);
        MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
        mcserv.getPlayerList().sendMessage(new StringTextComponent("Max VMStress: " + maxStr + " kPa"));
        //Change colors
        double interval = ult_stress * 1000 / 6;
        for(int n = 0; n < foundBlocks.size(); n++) {
            int x = foundBlocks.get(n).getX();
            int y = foundBlocks.get(n).getY();
            int z = foundBlocks.get(n).getZ();
            if (((world.getBlockState(new BlockPos((int) x, (int) y, (int) z))).getBlock() == TepoliumBlockBlock.block.getDefaultState().getBlock())) {
                BlockPos _bp = new BlockPos((int) x, (int) y, (int) z);
                //Highest to lowest colors - Black (Failure), Red, Orange, Yellow, Green, Blue, Purple
                BlockState _bs = Blocks.BLACK_WOOL.getDefaultState();
                if (vmstress[n] <= interval) {
                    _bs = Blocks.PURPLE_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= 2*interval) {
                    _bs = Blocks.BLUE_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= 3*interval) {
                    _bs = Blocks.GREEN_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= 4*interval) {
                    _bs = Blocks.YELLOW_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= 5*interval) {
                    _bs = Blocks.ORANGE_WOOL.getDefaultState();
        		} 
        		else if (vmstress[n] <= 6*interval) {
                    _bs = Blocks.RED_WOOL.getDefaultState();
        		}
				world.setBlockState(_bp, _bs, 3);
			}
		}
	}
	
	public static void colorChangeX(VectorObj[] particle_X, VectorObj[] particle_Xnew, List<BlockPos> foundBlocks, World world) {
        double[] delta = new double[particle_X.length];
        double min = 999999999;
        double max = 0;
        for(int i = 0; i < particle_X.length; i++) { //Calculate change in positions
        	double[][] vecdata = particle_X[i].getData();
        	double[][] vecdatanew = particle_Xnew[i].getData();
        	delta[i] = Math.sqrt(Math.pow(vecdata[0][0] - vecdatanew[0][0],2)+Math.pow(vecdata[1][0] - vecdatanew[1][0],2)+Math.pow(vecdata[2][0] - vecdatanew[2][0],2));
        	if(delta[i] < min){
        		min = delta[i];
        	}
        	if(delta[i] > max){
        		max = delta[i];
        	}
        }
        //Change colors
        double interval = (max - min)/ 6;
        for(int n = 0; n < foundBlocks.size(); n++) {
            int x = foundBlocks.get(n).getX();
            int y = foundBlocks.get(n).getY();
            int z = foundBlocks.get(n).getZ();
            if (((world.getBlockState(new BlockPos((int) x, (int) y, (int) z))).getBlock() == TepoliumBlockBlock.block.getDefaultState().getBlock())) {
                BlockPos _bp = new BlockPos((int) x, (int) y, (int) z);
                //Highest to lowest colors - Red, Orange, Yellow, Green, Blue, Purple
                BlockState _bs = Blocks.BLACK_WOOL.getDefaultState();
                if (delta[n] <= min + interval) {
                    _bs = Blocks.PURPLE_WOOL.getDefaultState();
                }
                else if (delta[n] <= min + 2*interval) {
                    _bs = Blocks.BLUE_WOOL.getDefaultState();
                }
                else if (delta[n] <= min + 3*interval) {
                    _bs = Blocks.GREEN_WOOL.getDefaultState();
                }
                else if (delta[n] <= min + 4*interval) {
                    _bs = Blocks.YELLOW_WOOL.getDefaultState();
                }
                else if (delta[n] <= min + 5*interval) {
                    _bs = Blocks.ORANGE_WOOL.getDefaultState();
        		} else {
                    _bs = Blocks.RED_WOOL.getDefaultState();
        		}
				world.setBlockState(_bp, _bs, 3);
			}
		}
	}	

	public static void colorChangeGeometry(List<BlockPos> foundBlocks, World world,  boolean[] geometryCheck, int n_particles) {
		
		// Change colors (red = failed/2D, green = passed/3D)		
		for (int i = 1; i < n_particles + 1; i++) { // i starts at 1 because the 1st position in geometry check is pass/fail for entire structure
			int x = foundBlocks.get(i - 1).getX();
			int y = foundBlocks.get(i - 1).getY();
			int z = foundBlocks.get(i - 1).getZ();
			
			if (((world.getBlockState(new BlockPos((int) x, (int) y, (int) z))).getBlock() == TepoliumBlockBlock.block.getDefaultState().getBlock())) {
                BlockPos _bp = new BlockPos((int) x, (int) y, (int) z);
                //Highest to lowest colors - Red, Orange, Yellow, Green, Blue, Purple
                BlockState _bs = Blocks.BLACK_WOOL.getDefaultState();

				if (geometryCheck[i]){
					_bs = Blocks.GREEN_WOOL.getDefaultState();
				}
				else {
					_bs = Blocks.RED_WOOL.getDefaultState();
				}
				world.setBlockState(_bp, _bs, 3);
			}
		}
	}	

	public static String getProperties(String filePath){
		// Read/Create Properties File
		Properties prop = new Properties();
		InputStream input = null;
		
		try {
			String propString = filePath + "sph.properties"; // string path of properties file
			File propFile = new File(propString); // file path of properties
			
			if(propFile.createNewFile()){ // if properties file does not exist, create and set variables to default values
				System.out.println("Properties file not found");
				System.out.println("Creating file: " + propString);
				prop.setProperty("h_threshold", Double.toString(h_threshold));
				prop.setProperty("alpha", Double.toString(alpha));
				prop.setProperty("Ehg", Double.toString(Ehg));
				prop.setProperty("mat[0]", Double.toString(mat[0]));
				prop.setProperty("mat[1]", Double.toString(mat[1]));
				prop.setProperty("mat[2]", Double.toString(mat[2]));
				prop.setProperty("eta", Double.toString(eta));
				prop.setProperty("rho", Double.toString(rho));
				prop.setProperty("ult_stress", Double.toString(ult_stress));
				prop.setProperty("n_t_steps", Double.toString(n_t_steps));
				prop.setProperty("dt_global", Double.toString(dt_global));
				prop.setProperty("loading_time_percent", Double.toString(loading_time_percent));
				prop.setProperty("block_load", Double.toString(block_load));
				prop.setProperty("load_block_weight", Double.toString(load_block_weight));
				prop.setProperty("filePath", filePath);
				
				FileOutputStream fos = new FileOutputStream(propString);
				prop.store(fos,"SPH Simulation Properties");
				fos.close();
				
			} else {
				System.out.println("Properties file already exists");
			}

		    input = new FileInputStream(propString);
			prop.load(input); // Load properties file

			// Update Sim variables from properties file
			h_threshold = Double.parseDouble(prop.getProperty("h_threshold")); //define the initial threshold
			alpha = Double.parseDouble(prop.getProperty("alpha")); //hourglass control
			Ehg = Double.parseDouble(prop.getProperty("Ehg")); //hourglass control [Pa]
			mat[0] = Double.parseDouble(prop.getProperty("mat[0]"));
			mat[1] = Double.parseDouble(prop.getProperty("mat[1]"));
			mat[2] = Double.parseDouble(prop.getProperty("mat[2]"));
			eta = Double.parseDouble(prop.getProperty("eta"));
			rho = Double.parseDouble(prop.getProperty("rho")); //[kg/m^3]
			ult_stress = Double.parseDouble(prop.getProperty("ult_stress")); //[kPa]
		
			n_t_steps = Double.parseDouble(prop.getProperty("n_t_steps"));
			dt_global = Double.parseDouble(prop.getProperty("dt_global"));
			loading_time_percent = Double.parseDouble(prop.getProperty("loading_time_percent"));
			block_load = Double.parseDouble(prop.getProperty("block_load"));
			load_block_weight = Double.parseDouble(prop.getProperty("load_block_weight"));
			filePath = prop.getProperty("filePath");
			
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return filePath; // Return updated file path if the user has modified in the properties file
	}

}	