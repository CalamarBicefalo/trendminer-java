package org.openimaj.ml.linear.learner.init;

import java.util.Random;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.mtj.SparseMatrixFactoryMTJ;

public class SparseRandomInitStrategy implements InitStrategy{
	SparseMatrixFactoryMTJ smf = SparseMatrixFactoryMTJ.INSTANCE;
	private double min;
	private double max;
	private Random random;
	public SparseRandomInitStrategy(double min,double max, Random random) {
		this.min = min;
		this.max = max;
		this.random = random;
	}
	@Override
	public Matrix init(int rows, int cols) {
		return smf.createUniformRandom(rows, cols, min, max, random);
	}

}
