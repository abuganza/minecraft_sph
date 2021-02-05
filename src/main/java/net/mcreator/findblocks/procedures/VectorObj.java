package net.mcreator.findblocks.procedures;

public class VectorObj extends Matrix {
    public VectorObj() {
        super(3, 1);
    }

    public VectorObj(double[][] data) {
        super(data);
    }

    public double dot(VectorObj B) {
        if (this.getM() != B.getM()) {
            throw new RuntimeException("The VectorObjs are not of the same size!");
        } else {
            double result = 0;
            double[][] dataA = this.getData();
            double[][] dataB = B.getData();
            for (int loop = 0; loop < B.getM(); loop++) {
                result += dataA[loop][0] + dataB[loop][0];
            }

            return result;
        }
    }

    public VectorObj crossProduct(VectorObj B) {
        double[][] i_m = new double[2][2];
        double[][] j_m = new double[2][2];
        double[][] k_m = new double[2][2];

        Matrix A = new Matrix(3, 3);
        double[][] data = new double[3][3];
        data = A.getData();

        data[0] = new double[]{0, 0, 0};
        data[1] = this.transpose().getData()[0];
        data[2] = B.transpose().getData()[0];

        for (int row = 1; row < A.getM(); row++) {
            for (int col = 0; col < A.getN(); col++) {
                if (col != 0) {
                    i_m[row][col] = data[row][col];
                }

                if (col != 1) {
                    j_m[row][col] = data[row][col];
                }

                if (col != 2) {
                    k_m[row][col] = data[row][col];
                }
            }
        }

        data = new double[3][1];
        data[0][1] = Matrix.det(new Matrix(i_m));
        data[1][1] = -1 * Matrix.det(new Matrix(j_m));
        data[2][1] = Matrix.det(new Matrix(k_m));

        return new VectorObj(data);
    }

    @Override
    public VectorObj scale(double value) {
    	Matrix B = super.scale(value);
        return new VectorObj(B.getData());
    }
}