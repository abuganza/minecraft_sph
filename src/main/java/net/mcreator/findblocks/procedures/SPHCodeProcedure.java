package net.mcreator.findblocks.procedures;

import net.mcreator.findblocks.FindblocksModElements;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.lang.Math.*;
import net.minecraft.world.World;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.math.BlockPos;
import net.mcreator.findblocks.FindblocksModElements;
import net.minecraft.client.renderer.debug.NeighborsUpdateDebugRenderer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.mcreator.findblocks.block.TepoliumBlockBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.AirBlock;

@FindblocksModElements.ModElement.Tag
public class SPHCodeProcedure extends FindblocksModElements.ModElement {
	public SPHCodeProcedure(FindblocksModElements instance) {
		super(instance, 7);
	}
	static double h_threshold = 2.1; //define the initial threshold 
	static double alpha = 0.0; //hourglass control 
	static double Ehg = 1.0; //hourglass control [MPa]
	static double mat[] = new double[] {0,1000000,10000000};
	static double eta = 1000; //viscosity 
	static int rho = 1000000; //[kg/mm^3]
	
	public static void executeProcedure(java.util.HashMap<String, Object> dependencies, List<BlockPos> foundBlocks) {
		//Particle properties and neighbors
		MinecraftServer mcserv = ServerLifecycleHooks.getCurrentServer();
		if((int) dependencies.get("stresspos") == 0) {
			mcserv.getPlayerList().sendMessage(new StringTextComponent("Invalid Argument Input"));
			return;
		}
		mcserv.getPlayerList().sendMessage(new StringTextComponent("Running SPH...."));
		int n_particles = foundBlocks.size();
		VectorObj[] particle_X = new VectorObj[n_particles];
		VectorObj[] particle_x = new VectorObj[n_particles];
		double[] particle_Vol = new double[n_particles];
		for(int i = 0; i < foundBlocks.size(); i++){
			particle_X[i] = new VectorObj(new double[][] {new double[] {foundBlocks.get(i).getX()},new double[] {foundBlocks.get(i).getY()},new double[] {foundBlocks.get(i).getZ()}});
			particle_Vol[i] = 1.0;
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
		//Checks if all blocks have atleast 3 neighbor particles
		boolean neighborCheck = true;
		for(int i = 0; i < n_particles; i++){
			int count = 0;
			for(int j = 0; j < n_particles; j++){
				if(Neighbor_list[i][j]){
					count++;
				}
			}
			if(count < 3){
				neighborCheck = false;
			}
		}
		//Loop over time (Start  of SPH calculations)
		if(neighborCheck){
			int n_t_steps = 1000;
			double dt = 0.01;
			for (int ti = 0; ti < n_t_steps; ti++){
				for (int pi = 0; pi < n_particles; pi++){
					Matrix[] FAinv = calculate_FAinv(pi,particle_X,particle_x,Neighbor_list); //Index 0 = F, Index 1 = Ainv
					Matrix P = calculate_P(FAinv[0],particle_FF[pi],dt,mat);
					particle_FF[pi] = FAinv[0];
					particle_PP[pi] = calculate_P(FAinv[0],particle_FF[pi],dt,mat);
					particle_Ainv[pi] = FAinv[1];
				}
				for (int pi = 0; pi < n_particles; pi++){
					particle_Fint[pi] = calculate_Fint(pi,particle_X,particle_x,Neighbor_list,particle_Ainv,particle_PP,particle_FF);
					//External Force of gravity (-y direction)
					VectorObj Fext = new VectorObj(new double[][]{new double[] {0}, new double[] {-900}, new double[] {0}});
					//Update kinematic properties
					particle_V[pi] = new VectorObj(particle_V[pi].plus(particle_A[pi].scale(dt / 2.0)).getData());
					particle_x[pi] = new VectorObj(particle_x[pi].plus(particle_V[pi].scale(dt)).getData());
					particle_A[pi] = new VectorObj((particle_Fint[pi].plus(Fext)).scale(1.0 / (particle_Vol[pi]*rho)).getData());
					particle_V[pi] = new VectorObj(particle_V[pi].plus(particle_A[pi].scale(dt / 2.0)).getData());
				}
				//Enforce Boundaries
				for (int ebc = 0; ebc < boundary_Blocks.size(); ebc++){
					particle_x[boundary_Blocks.get(ebc)] = particle_X[boundary_Blocks.get(ebc)];
				}
			}
			//SPH Calculations complete, changing colors
			if((int) dependencies.get("stresspos") == 1) { //Position
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Sim Complete, Changing Block Colors based on Position Change"));
				colorChangeX(particle_X, particle_x, foundBlocks, (World) dependencies.get("world"));
			} else { //Stress
				mcserv.getPlayerList().sendMessage(new StringTextComponent("Sim Complete, Changing Block Colors based on von Mises Stress"));
				colorChangeStress(particle_FF, particle_PP, foundBlocks, (World) dependencies.get("world"));
			}
		} else { //Neighbor Check == false
			mcserv.getPlayerList().sendMessage(new StringTextComponent("All blocks must have atleast 3 adjacent neighbors"));
		}
	}
	//Extra Functions
	public static boolean[][] findNeighbors(VectorObj[] particles) { //Returns nparticle x nparticle boolean array, if findNeighbors[pi][pj] == true, they are within h_thresholf distance from eachother and pj is a neighbor of pi
		boolean[][] neighbs = new boolean[particles.length][particles.length];
		for(int pi = 0; pi < particles.length; pi++) {
			for(int pj = 0; pj < particles.length; pj++) {
				if(pj != pi) {
					double[][] dif = particles[pi].minus(particles[pj]).getData();
					double d = Math.sqrt(dif[0][0]*dif[0][0] + dif[1][0]*dif[1][0] + dif[2][0]*dif[2][0]);
					if(d < h_threshold) {
						neighbs[pi][pj] = true;
					}
				}
			}
		}
		return neighbs;
	}

	public static List<Integer> findBounds(List<BlockPos> foundBlocks, World world) { //Returns indices of blocks that have anything other than tepolium blocks or air directly below them
		List<Integer> r = new ArrayList<Integer>();
		for(int i = 0; i < foundBlocks.size(); i++){
			BlockState underBlock = world.getBlockState(new BlockPos((int) foundBlocks.get(i).getX(), (int) (foundBlocks.get(i).getY() - 1), (int) foundBlocks.get(i).getZ()));
			if(underBlock.getBlock() != TepoliumBlockBlock.block.getDefaultState().getBlock() && underBlock.getBlock() != Blocks.AIR.getDefaultState().getBlock()){
				r.add(i);
			}
		}
		return r;
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
				//double Volj = particle_Vol[pj];
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
	
	public static VectorObj calculate_Fint(int pi, VectorObj[] particle_X, VectorObj[] particle_x, boolean[][] Neighbor_list, Matrix[] particle_Ainv, Matrix[] particle_PP, Matrix[] particle_FF) {
		Matrix Ainv = particle_Ainv[pi];
		double Voli = 1.0; //particle_Vol[pi];
		VectorObj Xi = particle_X[pi];
		VectorObj xi = particle_x[pi];
		VectorObj Fint = new VectorObj();
		VectorObj Fhg = new VectorObj();
		for(int pj = 0; pj < particle_X.length; pj++) {
			if(Neighbor_list[pi][pj]) {
				double Volj = 1.0; //particle_Vol[pj];
				VectorObj Xj = particle_X[pj];
				VectorObj xj = particle_x[pj];
				VectorObj rij = new VectorObj(xj.minus(xi).getData());
				VectorObj Rij = new VectorObj(Xj.minus(Xi).getData());
				double rijmag = Math.sqrt(Math.pow(rij.getData()[0][0], 2) + Math.pow(rij.getData()[1][0], 2) + Math.pow(rij.getData()[2][0], 2));
				double Rijmag = Math.sqrt(Math.pow(Rij.getData()[0][0], 2) + Math.pow(Rij.getData()[1][0], 2) + Math.pow(Rij.getData()[2][0], 2));
				VectorObj gradW = Ainv.mat_dot_vector(eval_gradW(Rij,h_threshold));
				Fint = new VectorObj(Fint.plus(particle_PP[pi].plus(particle_PP[pj]).mat_dot_vector(gradW).scale(Voli*Volj)).getData());
				//hourglass control
				VectorObj rij_pred = particle_FF[pi].mat_dot_vector(Rij);
				VectorObj rji_pred = particle_FF[pj].mat_dot_vector(Rij.scale(-1.0));
				double deltai = (new VectorObj(rij_pred.minus(rij).getData())).dot(Rij);
				deltai = deltai / rijmag;
				double deltaj = (new VectorObj(rji_pred.plus(rij).getData())).dot(rij.scale(-1.0));
				deltaj = deltaj / rijmag;
				Fhg = new VectorObj(Fhg.plus(rij.scale(-0.5*alpha*Ehg*Voli*Volj*eval_W(Rij,h_threshold)*(deltai+deltaj) / (Math.pow(Rijmag, 2) + rijmag))).getData());
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
		} else {
			return null;
		}
	}
	//Color Change Functions
	public static void colorChangeStress(Matrix[] particle_FF, Matrix[] particle_PP, List<BlockPos> foundBlocks, World world) {
        double[] vmstress = new double[particle_FF.length];
        double min = 999999999;
        double max = 0;
        for(int i = 0; i < particle_FF.length; i++) { //Calculate von Mises Stress
        	double detF = Matrix.det(particle_FF[i].getData(),3);
        	double[][] sigma = particle_PP[i].times(particle_FF[i].transpose()).scale(1 / detF).getData();
        	vmstress[i] = Math.sqrt(Math.pow(sigma[0][0] - sigma[1][1], 2) + Math.pow(sigma[1][1] - sigma[2][2], 2) + Math.pow(sigma[2][2] - sigma[0][0], 2) + 6*(Math.pow(sigma[1][2], 2) + Math.pow(sigma[0][2], 2) + Math.pow(sigma[0][1], 2))) / Math.sqrt(2);
        	if(vmstress[i] < min){
        		min = vmstress[i];
        	}
        	if(vmstress[i] > max){
        		max = vmstress[i];
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
                if (vmstress[n] <= min + interval) {
                    _bs = Blocks.PURPLE_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= min + 2*interval) {
                    _bs = Blocks.BLUE_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= min + 3*interval) {
                    _bs = Blocks.GREEN_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= min + 4*interval) {
                    _bs = Blocks.YELLOW_WOOL.getDefaultState();
                }
                else if (vmstress[n] <= min + 5*interval) {
                    _bs = Blocks.ORANGE_WOOL.getDefaultState();
        		} else {
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
}	
