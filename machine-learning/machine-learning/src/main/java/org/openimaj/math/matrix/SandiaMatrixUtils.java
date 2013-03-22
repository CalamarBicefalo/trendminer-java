package org.openimaj.math.matrix;

import com.jmatio.types.MLArray;
import com.jmatio.types.MLDouble;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.mtj.SparseMatrixFactoryMTJ;

public class SandiaMatrixUtils {

	public static Matrix abs(Matrix mat) {
		Matrix ret = mat.clone();
		int nrows = ret.getNumRows();
		int ncols = ret.getNumColumns();
		for (int r = 0; r < nrows; r++) {
			for (int c = 0; c < ncols; c++) {
				ret.setElement(r, c, Math.abs(mat.getElement(r, c)));
			}
		}
		return ret;
	}

	public static double absSum(Matrix mat) {
		double tot = 0;
		int nrows = mat.getNumRows();
		int ncols = mat.getNumColumns();
		for (int r = 0; r < nrows; r++) {
			for (int c = 0; c < ncols; c++) {
				tot += Math.abs(mat.getElement(r, c));
			}
		}
		return tot;
	}

	public static Matrix timesInplace(Matrix mat, double etat) {
		int nrows = mat.getNumRows();
		int ncols = mat.getNumColumns();
		for (int r = 0; r < nrows; r++) {
			for (int c = 0; c < ncols; c++) {
				mat.setElement(r, c, mat.getElement(r, c) * etat);
			}
		}
		return mat;
	}

	public static Matrix asMat(MLArray mlArray) {
		MLDouble mlArrayDbl = (MLDouble) mlArray;
		int rows = mlArray.getM();
		int cols = mlArray.getN();
		
		Matrix mat = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(rows, cols);
		
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				mat.setElement(r, c, mlArrayDbl.get(r, c));
			}
		}
		return mat;
	}

}
