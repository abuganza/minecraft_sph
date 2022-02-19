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
import net.mcreator.findblocks.block.TrussBlockBlock;
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
public class TrussCodeProcedure extends FindblocksModElements.ModElement {
	public TrussCodeProcedure(FindblocksModElements instance) {
		super(instance, 7);
	}
	
	static double E = 1e9; //stiffness [Pa]
	static double A = 0.2; // cross sectional area in m^2 
	
	static int ult_stress = 12; //[kPa]
	static double h_threshold = 5;

	static double block_load = -900;

	public static void executeProcedure(java.util.HashMap<String, Object> dependencies, List<BlockPos> foundBlocks, List<Integer> loaded_blocks) {
		//Particle initialization and neighbors
		World world = (World) dependencies.get("world");
		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
	//	if((int) dependencies.get("stresspos") == 0) {
	//		mcserv.getPlayerList().sendMessage(new StringTextComponent("Invalid Argument Input"));
	//		return;
	//	}


		//FILE PATH
		//*****************************************************************************************************************************
	/*	
		String filePath = "/Users/abuganza/Documents/SPH/"; //<--- ***CHANGE FILE PATH*** Folder in which all .csv will be written to
		Path pathFile = Paths.get(filePath);				//Folder path should look similar to "C:/Users/brandon/Desktop/SPH/"
		
		//*****************************************************************************************************************************




		if(Files.notExists(pathFile)){									//******DO NOT CHANGE FILE PATH HERE***** CHANGE ON LINE 71
			mcserv.getPlayerList().sendMessage(new StringTextComponent("**Path Not Found** -- Change CSV Save Directory in SPHCode to desired folder"));
			return;														//******
		}
		*/
		
		//All Pre-Checks Passed
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Running Truss...."));

		double start = System.currentTimeMillis();

		// number of nodes in Truss structure 
		int n_nodes = foundBlocks.size();
		// initial positions, just dumping into a Vector array
		VectorObj[] particle_X = new VectorObj[n_nodes];
		// displacement of truss nodes 
		VectorObj[] particle_u = new VectorObj[n_nodes];

		// special block to save deflection of one of the nodes
		int special_block = -1;
		IWorld world2 = (IWorld) world;
		double x_special = (double) FindblocksModVariables.MapVariables.get(world2).special_block_X; //Coordinates of Block that is written to CSV
		double y_special = (double) FindblocksModVariables.MapVariables.get(world2).special_block_Y;
		double z_special = (double) FindblocksModVariables.MapVariables.get(world2).special_block_Z;
		for(int i = 0; i < foundBlocks.size(); i++){
			particle_X[i] = new VectorObj(new double[][] {new double[] {foundBlocks.get(i).getX()},new double[] {foundBlocks.get(i).getY()},new double[] {foundBlocks.get(i).getZ()}});
			if(particle_X[i].getData()[0][0] == x_special && particle_X[i].getData()[1][0] == y_special && particle_X[i].getData()[2][0] == z_special){
				special_block = i;
			}
		}

		if(special_block == -1){
			special_block = 0;
			double[][] default_special_coords = particle_X[0].getData();
			x_special = default_special_coords[0][0];
			y_special = default_special_coords[1][0];
			z_special = default_special_coords[2][0];
		}

		List<Integer> boundary_Blocks = findBounds(foundBlocks, (World) dependencies.get("world"));
		
		// TO DO 
		// it would be nice to have a spcial truss element as well, to save the stress of a particular element
		
		// Element list, 
		// need some sort of list and not the boolean matrix because need loop over elements for solving 
		List<Element> Element_list = FindElements(particle_X, (World) dependencies.get("world"));
		int n_elem = Element_list.size();
		System.out.println("Found: " + n_elem + " elements");
		// Stiffness matrix
		double[][] KK_data = new double[n_nodes*3][n_nodes*3];
		// Loop over elements 
		for(int ei=0;ei<n_elem;ei++){
			// for each element get node 1 and node 2
			int p1 = Element_list.get(ei).getn1();
			int p2 = Element_list.get(ei).getn2();
			// coordinates of each 
			double [][] p1_coord = particle_X[p1].getData();
			double [][] p2_coord = particle_X[p2].getData();
			// length of the truss
			double x21 = p2_coord[0][0]-p1_coord[0][0];
			double y21 = p2_coord[1][0]-p1_coord[1][0];
			double z21 = p2_coord[2][0]-p1_coord[2][0];
			double le = Math.sqrt( x21*x21+y21*y21+z21*z21);
			// based on the coordinates get the rotation matrix Re 
			Matrix Re = new Matrix(2,6);
			double [][] Re_data = new double [2][6];;
			Re_data[0][0] = x21/le;
			Re_data[0][1] = y21/le;
			Re_data[0][2] = z21/le;
			Re_data[0][3] = 0;
			Re_data[0][4] = 0;
			Re_data[0][5] = 0;
			Re_data[1][0] = 0; 
			Re_data[1][1] = 0;
			Re_data[1][2] = 0;
			Re_data[1][3] = x21/le;
			Re_data[1][4] = y21/le;
			Re_data[1][5] = z21/le;
			Re.setData(Re_data);
			
			// 2x2 matrix of local stiffness
			Matrix Ke_local = new Matrix(2,2);
			double[][] Ke_local_data = new double [2][2];
			Ke_local_data[0][0] = +1*E*A/le;
			Ke_local_data[0][1] = -1*E*A/le;
			Ke_local_data[1][0] = -1*E*A/le;
			Ke_local_data[1][1] = +1*E*A/le;
			Ke_local.setData(Ke_local_data);

			// rotate for the 6x6 stiffness matrix in global coordinates 
			Matrix Ke = Re.transpose().times(Ke_local.times(Re));
			double [][] Ke_data = Ke.getData();
			// assembly 
			int[] elem_nodes = new int[]{p1,p2};
			for(int i=0;i<2;i++){
				int pi = elem_nodes[i];
				for(int ci=0;ci<3;ci++){
					for(int j=0;j<2;j++){
						int pj = elem_nodes[j];
						for(int cj=0;cj<3;cj++){
							KK_data[pi*3+cj][pj*3+cj]+=Ke_data[i*3+ci][j*3+cj];
						}
					}
				}
			}
		}
		// The right hand side needs forces 
		double[][] Forces_data = new double[3*n_nodes][1];
		// by default every node has a small weight associated with it
		// plus, for every particle, check if the block above is a load block, in which case add load 
		for(int pi=0;pi<n_nodes;pi++){
			Forces_data[pi*3+1][0] = -1; // default weight in y direction for every node 
			// check if load block on top 
			if(loaded_blocks.contains(pi)){
				Forces_data[pi*3+1][0] -= block_load;
			}
			// no forces in x or y 
			Forces_data[pi*3+0][0] = 0;
			Forces_data[pi*3+2][0] = 0;
		}

		//testatgt
		Matrix Ftest = new Matrix(Forces_data);
		Ftest.print();

		//asfgasdgasd 
		
		// The stiffness matrix has been assembled, need to replace the rows for the bound elements 
		//Enforce Boundaries
		double [][] KK_reactions_data = new double [boundary_Blocks.size()*3][3*n_nodes];
		for (int ebc = 0; ebc < boundary_Blocks.size(); ebc++){
			int pb = boundary_Blocks.get(ebc) ;
			// for the entire 3 rows, change to zeros but save copy first
			for(int col=0;col<n_nodes*3;col++){
				
				KK_reactions_data[ebc*3+0][col] = KK_data[pb*3+0][col];
				KK_reactions_data[ebc*3+1][col] = KK_data[pb*3+1][col];
				KK_reactions_data[ebc*3+2][col] = KK_data[pb*3+2][col];
				
				KK_data[pb*3+0][col] = 0;
				KK_data[pb*3+1][col] = 0;
				KK_data[pb*3+2][col] = 0;
			}
			// entire three columns for this node change to zeros 
			for(int row=0;row<n_nodes*3;row++){
				KK_data[row][pb*3+0] = 0;
				KK_data[row][pb*3+1] = 0;
				KK_data[row][pb*3+2] = 0;
			}
			// replace the diagonal with 1 
			KK_data[pb*3+0][pb*3+0] = 1;
			KK_data[pb*3+1][pb*3+1] = 1;
			KK_data[pb*3+2][pb*3+2] = 1;
			// and also need to replace right hand side with 0
			// note already zeros in x and z, just y needs to be changed
			Forces_data[pb*3+1][0] = 0;
		}
		// convert arrays to matrix and to vector 
		Matrix KK = new Matrix(KK_data);
		Matrix Forces = new Matrix(Forces_data);
		KK.print();
		System.out.println(" ");
		Forces.print();
		System.out.println(" ");
		
		// solve the system of equation for the displacements 
		Matrix u = KK.solve(Forces);
		double[][] u_data = u.getData();
		// recast into a nicer format 
		for(int pi=0;pi<n_nodes;pi++){
			particle_u[pi] = new VectorObj(new double[][]{new double[] {u_data[pi*3+0][0]}, new double[] {u_data[pi*3+1][0]}, new double[] {u_data[pi*3+2][0]}});
		}

		//compute stresses per element by looping again over elements 
		double [] element_stress = new double [n_elem]; 
		// Loop over elements 
		for(int ei=0;ei<n_elem;ei++){
			// for each element get node 1 and node 2
			int p1 = Element_list.get(ei).getn1();
			int p2 = Element_list.get(ei).getn2();
			// coordinates of each 
			double [][] p1_coord = particle_X[p1].getData();
			double [][] p2_coord = particle_X[p2].getData();
			// displacements of each node 
			double [][] u1_coord = particle_u[p1].getData();
			double [][] u2_coord = particle_u[p2].getData();
			double [][] de_data = new double[6][1];
			
			de_data[0][0] = u1_coord[0][0];
			de_data[1][0] = u1_coord[1][0];
			de_data[2][0] = u1_coord[2][0];
			de_data[3][0] = u2_coord[0][0];
			de_data[4][0] = u2_coord[1][0];
			de_data[5][0] = u2_coord[2][0];
			Matrix de = new Matrix(de_data);
			// length of the truss
			double x21 = p2_coord[0][0]-p1_coord[0][0];
			double y21 = p2_coord[1][0]-p1_coord[1][0];
			double z21 = p2_coord[2][0]-p1_coord[2][0];
			double le = Math.sqrt(x21*x21+y21*y21+z21*z21);

			// based on the coordinates get the rotation matrix Re 
			Matrix Re = new Matrix(2,6);
			double [][] Re_data = new double [2][6];;
			Re_data[0][0] = x21/le;
			Re_data[0][1] = y21/le;
			Re_data[0][2] = z21/le;
			Re_data[0][3] = 0;
			Re_data[0][4] = 0;
			Re_data[0][5] = 0;
			Re_data[1][0] = 0; 
			Re_data[1][1] = 0;
			Re_data[1][2] = 0;
			Re_data[1][3] = x21/le;
			Re_data[1][4] = y21/le;
			Re_data[1][5] = z21/le;
			Re.setData(Re_data);
			
			// 2x2 matrix of local stiffness
			Matrix Ke_local = new Matrix(2,2);
			double[][] Ke_local_data = new double [2][2];
			Ke_local_data[0][0] = +1*E*A/le;
			Ke_local_data[0][1] = -1*E*A/le;
			Ke_local_data[1][0] = -1*E*A/le;
			Ke_local_data[1][1] = +1*E*A/le;
			Ke_local.setData(Ke_local_data);

			// 2x2 * 2x6 * 6x1 = 2x1
			Matrix sigma =  Ke_local.times(Re.times(de));
			double [][] sigma_data = sigma.getData();
			// must be same with different sign, second entry should be correct one 
			element_stress[ei]=sigma_data[1][0]; 
			
		}
		
		// get the reaction forces with K_copy, just need to read in the lines of the bound blocks 
		int n_bound = boundary_Blocks.size();
		double [] reactions = new double[n_bound*3];
		for(int i=0;i<n_bound*3;i++){
			reactions[i]=0;
			for(int j=0;j<n_nodes*3;j++){
				reactions[i]+=KK_reactions_data[i][j]*u_data[j][0];
			}
		System.out.println("Reaction " + i + ": " + reactions[i]);
		}

		// color the truss blocks in between the nodes 
		// loop over elements is easier 
		for(int ei=0;ei<n_elem;ei++){
			int p1 = Element_list.get(ei).getn1();
			int p2 = Element_list.get(ei).getn2();
			// coordinates of each 
			double [][] p1_coord = particle_X[p1].getData();
			double [][] p2_coord = particle_X[p2].getData();
			// check if there are truss blocks in the line by these two nodes and if yes then color according to stress of element
		}
		/*
		try {
		PrintWriter writer3 = new PrintWriter(new File(filePath + "Bound_block_reaction_forces.csv"));
		StringBuilder cc = new StringBuilder();
		cc.append("x,y,z,Fint[0],Fint[1],Fint[2],Index,");
		cc.append("\n");
		writer3.write(cc.toString());
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<boundary_Blocks.size(); i++){
			int index = boundary_Blocks.get(i);
			double[] xyz = new double[] {particle_X[index].getData()[0][0],particle_X[index].getData()[1][0],particle_X[index].getData()[2][0]};
			sb.append(xyz[0] + ",");
			sb.append(xyz[1] + ",");
			sb.append(xyz[2] + ",");
			sb.append(Double.toString(reactions[i*3+0]) + ",");
			sb.append(Double.toString(reactions[i*3+1]) + ",");
			sb.append(Double.toString(reactions[i*3+2]) + ",");
			sb.append(index + ",");
			sb.append("\n");
		}
		writer3.write(sb.toString());
		writer3.flush();
		writer3.close();

		PrintWriter writer4 = new PrintWriter(new File(filePath + "block_coordinates.csv"));
		StringBuilder dd = new StringBuilder();
		dd.append("x, y, z, Index,\n");
		writer4.write(dd.toString());

		for (int i = 0; i < n_nodes; i++){
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
		}catch (Exception e) {
				e.printStackTrace();
		}
		*/
		//SPH Calculations complete, changing colors
		if((int) dependencies.get("stresspos") == 1) { //Position
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Sim Complete,"));
		} else { //Stress
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Sim Complete, Changing Block Colors based on von Mises Stress"));
		}


		// Time calculation / formatting
		double finish = System.currentTimeMillis();
		double timeElapsed = (finish - start) / 1000;
       	String minutes = String.format("%.0f",Math.floor(timeElapsed / 60));
       	String seconds = String.format("%.0f", timeElapsed % 60);
       					
       	// Display Info to player
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Elapsed Time: " + minutes + ":" + seconds));
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Number of blocks: " + n_nodes + "Nodes and " + n_elem + " Elements"));
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Total Weight: " + -1* n_nodes * block_load));
	}

	
	//Extra Functions

	// Find possible elements 
	public static List<Element> FindElements(VectorObj[] particles, World world) { //Returns nparticle x nparticle boolean array, if findNeighbors[pi][pj] == true, they are within h_thresholf distance from eachother and pj is a neighbor of pi
		List<Element> Element_list = new ArrayList<Element>();
		//System.out.println("line 403");
		for(int pi = 0; pi < particles.length; pi++) {
			//System.out.println("line 405");
			int count = 0;
			for(int pj = pi+1; pj < particles.length; pj++) {
				//System.out.println("line 408");
				double[][] dif = particles[pi].minus(particles[pj]).getData();
				double d = Math.sqrt(dif[0][0]*dif[0][0] + dif[1][0]*dif[1][0] + dif[2][0]*dif[2][0]);
				//System.out.println("line 411");
				System.out.println("D = " + d);
				if(d < h_threshold) {
					//System.out.println("line 413");
					// if the distance is below the threshold, then check if there are filler elements in between
					// based on the distance, loop over equation of the line and count how many Truss blocks are right on the line between pi and pj
					int count2=0;
					for(int li=0; li<(int)d; li++){
						double alpha = li/d; // alpha is between 0 and 1
						Matrix li_pos  = (particles[pi].scale(1-alpha)).plus(particles[pj].scale(alpha));
						//System.out.println("line 421");
						double[][] li_coord = li_pos.getData();
						//System.out.println("line 423");
						//if (((new ItemStack((world.getBlockState(new BlockPos(((int) li_coord[0][0]) , ((int) li_coord[0][1])  , ((int) li_coord[0][2]) ))).getBlock())).getItem() == new ItemStack(TrussBlockBlock.block, (int) (1)).getItem())) {
							
						BlockState trussBlock = TrussBlockBlock.block.getDefaultState();
						//System.out.println("line 427");
						//System.out.println("li coord " + li_coord[0][0]);
						//System.out.println("li coord " + li_coord[1][0]);
						//System.out.println("li coord " + li_coord[2][0]);
		
						BlockState newBlock = world.getBlockState(new BlockPos(((int) li_coord[0][0]) , ((int) li_coord[1][0])  , ((int) li_coord[2][0]) ));

						//System.out.println("line 429");
						
						if (trussBlock.getBlock() == newBlock.getBlock()){	
							//System.out.println("line 432");
							count2 ++;
							
						}
						System.out.println("Count2: " + count2);
						//System.out.println("line 435");
					}
					// if there are at least half of the blocks in the line between pi and pj that are TrussBlocks, then pi and pj are
					// actually neighbors and we should save which of the truss blocks belong to this truss element 
					//System.out.println("line 439");
					if(count2/d>=0.5){
						//System.out.println("line 431");
						Element new_elem = new Element(pi,pj);
						Element_list.add(new_elem);
						count++;
						
					}
					System.out.println("Count: " + count);
				}				
			}
		
		}
		//System.out.println("line 434");
		return Element_list;
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
			BlockState trussBlock = TrussBlockBlock.block.getDefaultState();

			BlockState[] bindVec = new BlockState[] {waterBlock, airBlock, tepBlock, loadBlock, trussBlock};

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
					break; // exit early if any block state is bound
				}
			}
		}
		return r;
	}

	
}	

// Need a simple pair object for the element
// Creating a class here, unsure this is the best

class Element {
    private final int n1;             // node 1
    private final int n2;             // node 2

    // create the element based on two nodes
    public Element(int n1, int n2) {
        this.n1 = n1;
        this.n2 = n2;
    }

  	public int getn1() {
        return n1;
    }
    public int getn2() {
        return n2;
    }
}