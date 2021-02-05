package net.mcreator.findblocks.procedures;

import java.util.Arrays;
import java.util.Objects;

public class Matrix {
    private final int M;             // number of rows
    private final int N;             // number of columns
    private double[][] data;   // M-by-N array

    // create M-by-N matrix of 0's
    public Matrix(int M, int N) {
        this.M = M;
        this.N = N;
        data = new double[M][N];
    }

    // create matrix based on 2d array
    public Matrix(double[][] data) {
        M = data.length;
        N = data[0].length;
        this.data = data;
    }

    // copy constructor
    private Matrix(Matrix A) {
        this(A.data);
    }

    public double[][] getData() {
        return data;
    }

    public int getM() {
        return M;
    }

    public int getN() {
        return N;
    }

    public void setData(double[][] data) {
        this.data = data;
    }

    // create and return a zeros matrix M-by-N
    public static Matrix zerosMatrix(int M, int N) {
        Matrix A = new Matrix(M, N);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                A.data[i][j] = 0;
            }
        }
        return A;
    }

    // create and return a random M-by-N matrix with values between 0 and 1
    public static Matrix random(int M, int N) {
        Matrix A = new Matrix(M, N);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                A.data[i][j] = Math.random();
            }
        }
        return A;
    }

    // create and return the N-by-N identity matrix
    public static Matrix identity(int N) {
        Matrix I = new Matrix(N, N);
        for (int i = 0; i < N; i++) {
            I.data[i][i] = 1;
        }
        return I;
    }

    // swap rows i and j
    private void swap(int i, int j) {
        double[] temp = data[i];
        data[i] = data[j];
        data[j] = temp;
    }

    // create and return the transpose of the invoking matrix
    public Matrix transpose() {
        Matrix A = new Matrix(N, M);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                A.data[j][i] = this.data[i][j];
            }
        }
        return A;
    }

    // return C = A + B
    public Matrix plus(Matrix B) {
        Matrix A = this;
        if (B.getM() != A.getM() || B.getN() != A.getN()) {
            throw new RuntimeException("Illegal matrix dimensions.");
        }
        Matrix C = new Matrix(M, N);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                C.data[i][j] = A.data[i][j] + B.data[i][j];
            }
        }
        return C;
    }


    // return C = A - B
    public Matrix minus(Matrix B) {
        Matrix A = this;
        if (B.getM() != A.getM() || B.getN() != A.getN()) {
            throw new RuntimeException("Illegal matrix dimensions.");
        }
        Matrix C = new Matrix(M, N);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                C.data[i][j] = A.data[i][j] - B.data[i][j];
            }
        }
        return C;
    }

    // does A = B exactly?
    public boolean equalsMatrix(Matrix B) {
        Matrix A = this;
        if (B.getM() != A.getM() || B.getN() != A.getN()) {
            throw new RuntimeException("Illegal matrix dimensions.");
        }
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                if (A.data[i][j] != B.data[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (getClass() != o.getClass()) {
            return false;
        } else {
            Matrix matrix = (Matrix) o;
            return M == matrix.getM() && N == matrix.getN() && Arrays.equals(data, matrix.data);
        }
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(M, N);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    // return C = A * B
    public Matrix times(Matrix B) {
        Matrix A = this;
        if (A.getN() != B.getM()) {
            throw new RuntimeException("Illegal matrix dimensions.");
        }
        Matrix C = new Matrix(A.getM(), B.getN());
        for (int i = 0; i < C.getM(); i++) {
            for (int j = 0; j < C.getN(); j++) {
                for (int k = 0; k < A.getN(); k++) {
                    C.data[i][j] += (A.data[i][k] * B.data[k][j]);
                }
            }
        }
        return C;
    }


    // return x = A^-1 b, assuming A is square and has full rank
    public Matrix solve(Matrix rhs) {
        if (M != N || rhs.getM() != N || rhs.getN() != 1) {
            throw new RuntimeException("Illegal matrix dimensions.");
        }

        // create copies of the data
        Matrix A = new Matrix(this);
        Matrix b = new Matrix(rhs);

        // Gaussian elimination with partial pivoting
        for (int i = 0; i < N; i++) {

            // find pivot row and swap
            int max = i;
            for (int j = i + 1; j < N; j++) {
                if (Math.abs(A.data[j][i]) > Math.abs(A.data[max][i])) {
                    max = j;
                }
            }
            A.swap(i, max);
            b.swap(i, max);

            // singular
            if (A.data[i][i] == 0.0) {
                throw new RuntimeException("Matrix is singular.");
            }

            // pivot within b
            for (int j = i + 1; j < N; j++) {
                b.data[j][0] -= b.data[i][0] * A.data[j][i] / A.data[i][i];
            }

            // pivot within A
            for (int j = i + 1; j < N; j++) {
                double m = A.data[j][i] / A.data[i][i];
                for (int k = i+1; k < N; k++) {
                    A.data[j][k] -= A.data[i][k] * m;
                }
                A.data[j][i] = 0.0;
            }
        }

        // back substitution
        Matrix x = new Matrix(N, 1);
        for (int j = N - 1; j >= 0; j--) {
            double t = 0.0;
            for (int k = j + 1; k < N; k++) {
                t += A.data[j][k] * x.data[k][0];
            }
            x.data[j][0] = (b.data[j][0] - t) / A.data[j][j];
        }

        return x;
    }

    // print matrix to standard output
    public void print() {
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                System.out.printf("%9.4f ", data[i][j]);
            }
            System.out.println();
        }
    }

    public static double det(double[][] A, int n) {
        double D = 0; // Initialize result

        // Base case : if matrix contains single element
        if (n == 1) {
            return A[0][0];
        }

        double[][] temp = new double[A.length][A.length]; // To store co-factors

        int sign = 1; // To store sign multiplier

        // Iterate for each element of first row
        for (int f = 0; f < n; f++) {
            // Getting Co-factor of A[0][f]
            temp =  getCofactor(A, 0, f, n);
            D += sign * A[0][f] * det(temp, n - 1);

            // terms are to be added with alternate sign
            sign = -sign;
        }

        return D;
    }

    public static double[][] getCofactor(double[][] A, int p, int q, int n) {
        int N = A.length;
        double[][] temp = new double[N][N];
        int i = 0, j = 0;

        // Looping for each element of the matrix
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                // Copying into temporary matrix only those element
                // which are not in given row and column
                if (row != p && col != q) {
                    temp[i][j++] = A[row][col];

                    // Row is filled, so increase row index and
                    // reset col index
                    if (j == n - 1) {
                        j = 0;
                        i++;
                    }
                }
            }
        }

        return temp;
    }

    public static double[][] adjoint(double[][] A) {
        int N = A.length;
        double[][] adj = new double[N][N];
        if (N == 1) {
            adj[0][0] = 1;
            return adj;
        }

        // temp is used to store cofactors of A[][]
        int sign = 1;
        double[][] temp = new double[N][N];

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                // Get cofactor of A[i][j]
                temp = getCofactor(A, i, j, N);

                // sign of adj[j][i] positive if sum of row
                // and column indexes is even.
                sign = ((i + j) % 2 == 0)? 1: -1;

                // Interchanging rows and columns to get the
                // transpose of the cofactor matrix
                adj[j][i] = (sign)*(det(temp, N-1));
            }
        }

        return adj;
    }

    public Matrix inverse() {
        return Matrix.inverse(this.getData());
    }

    public static Matrix inverse(double[][] A) {
        int N = A.length;
        double[][] inverse = new double[N][N];

        // Find determinant of A
        double det = det(A, N);
        if (det == 0) {
            throw new RuntimeException("There is no inverse!");
        }

        // Find adjoint
        double[][] adj = adjoint(A);

        // Find Inverse using formula "inverse(A) = adj(A)/det(A)"
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                inverse[i][j] = adj[i][j] / (float) det;
            }
        }

        return new Matrix(inverse);
    }

    public static double det(Matrix A) {
        if (A.getM() != A.getN()) {
            throw new RuntimeException("The matrix is not full rank!");
        } else {
            return det(A.getData(), A.getM());
        }
    }

    public Matrix dot(Matrix B) {
        if (this.getN() != B.getM()) {
            throw new RuntimeException("The Matrices are not of the same size!");
        } else {
            Matrix result = new Matrix(this.getM(), B.getN());
            double[][] dataR = new double[this.getM()][B.getN()];
            double[][] dataA = this.getData();
            double[][] dataB = B.getData();
            for (int i = 0; i < this.getM(); i++) {
                for (int j = 0; j < B.getN(); j++) {
                    for (int k = 0; k < this.getN(); k++) {
                        dataR[i][j] += dataA[i][k] * dataB[k][j];
                    }
                }
            }

            result.setData(dataR);
            return result;
        }
    }

    public Matrix scale(double value) {
        double[][] data = new double[M][N];
        for (int row = 0; row < this.getM(); row++) {
            for (int col = 0; col < this.getN(); col++) {
                data[row][col] = value * this.data[row][col];
            }
        }

        return new Matrix(data);
    }
    
    public VectorObj mat_dot_vector(VectorObj b) {
    	double[][] data = new double[3][1];
    	data[0][0] = (this.getData()[0][0] * b.getData()[0][0]) + (this.getData()[1][0] * b.getData()[1][0]) + (this.getData()[2][0] * b.getData()[2][0]);
    	data[1][0] = (this.getData()[0][1] * b.getData()[0][0]) + (this.getData()[1][1] * b.getData()[1][0]) + (this.getData()[2][1] * b.getData()[2][0]);
    	data[2][0] = (this.getData()[0][2] * b.getData()[0][0]) + (this.getData()[1][2] * b.getData()[1][0]) + (this.getData()[2][2] * b.getData()[2][0]);
    	return new VectorObj(data);
    }
}