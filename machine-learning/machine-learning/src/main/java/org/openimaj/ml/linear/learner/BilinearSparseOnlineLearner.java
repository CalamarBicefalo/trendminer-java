package org.openimaj.ml.linear.learner;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.mtj.DenseMatrixFactoryMTJ;
import gov.sandia.cognition.math.matrix.mtj.SparseMatrix;
import gov.sandia.cognition.math.matrix.mtj.SparseMatrixFactoryMTJ;

import org.apache.log4j.Logger;
import org.openimaj.io.ReadWriteableBinary;
import org.openimaj.math.matrix.SandiaMatrixUtils;
import org.openimaj.ml.linear.learner.init.InitStrategy;
import org.openimaj.ml.linear.learner.loss.LossFunction;
import org.openimaj.ml.linear.learner.loss.MatLossFunction;
import org.openimaj.ml.linear.learner.regul.Regulariser;


/**
 * An implementation of a stochastic gradient decent with proximal perameter adjustment
 * (for regularised parameters).
 * 
 * Data is dealt with sequentially using a one pass implementation of the 
 * online proximal algorithm described in chapter 9 and 10 of:
 * The Geometry of Constrained Structured Prediction: Applications to Inference and
 * Learning of Natural Language Syntax, PhD, Andre T. Martins
 * 
 * The implementation does the following:
 * 	- When an X,Y is recieved:
 * 		- Update currently held batch
 * 		- If the batch is full:
 * 			- While There is a great deal of change in U and W:
 * 				- Calculate the gradient of W holding U fixed
 * 				- Proximal update of W
 * 				- Calculate the gradient of U holding W fixed
 * 				- Proximal update of U
 * 				- Calculate the gradient of Bias holding U and W fixed
 * 			- flush the batch
 * 		- return current U and W (same as last time is batch isn't filled yet)
 * 
 * 
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class BilinearSparseOnlineLearner implements OnlineLearner<Matrix,Matrix>, ReadWriteableBinary{
	
	static Logger logger = Logger.getLogger(BilinearSparseOnlineLearner.class);
	
	protected BilinearLearnerParameters params;
	protected Matrix w;
	protected Matrix u;
	protected Boolean indu;
	protected Boolean indw;
	protected SparseMatrixFactoryMTJ smf = SparseMatrixFactoryMTJ.INSTANCE;
	protected LossFunction loss;
	protected Regulariser regul;
	protected Double lambda;
	protected Boolean biasMode;
	protected Matrix bias;
	protected Matrix diagX;
	protected Double eta0_u;
	protected Double eta0_w;

	private Boolean forceSparcity;
	
	public BilinearSparseOnlineLearner() {
		this(new BilinearLearnerParameters());
	}
	public BilinearSparseOnlineLearner(BilinearLearnerParameters params) {
		this.params = params;
		reinitParams();
	}
	
	public void reinitParams() {
		this.indw = this.params.getTyped(BilinearLearnerParameters.INDW);
		this.indu = this.params.getTyped(BilinearLearnerParameters.INDU);
		this.loss = this.params.getTyped(BilinearLearnerParameters.LOSS);
		this.regul = this.params.getTyped(BilinearLearnerParameters.REGUL);
		this.lambda = this.params.getTyped(BilinearLearnerParameters.LAMBDA);
		this.biasMode = this.params.getTyped(BilinearLearnerParameters.BIAS);
		this.eta0_u = this.params.getTyped(BilinearLearnerParameters.ETA0_U);
		this.eta0_w = this.params.getTyped(BilinearLearnerParameters.ETA0_W);
		this.forceSparcity = this.params.getTyped(BilinearLearnerParameters.FORCE_SPARCITY);
		
		if(indw && indu){
			this.loss = new MatLossFunction(this.loss);
		}
	}
	private void initParams(int xrows, int xcols, int ycols) {
		InitStrategy wstrat = this.params.getTyped(BilinearLearnerParameters.WINITSTRAT);
		InitStrategy ustrat = this.params.getTyped(BilinearLearnerParameters.UINITSTRAT);
		
		if(indw) this.w = wstrat.init(xrows, ycols);
		else this.w = wstrat.init(xrows, 1);
		if(indu) this.u = ustrat.init(xcols, ycols);
		else this.u = ustrat.init(xcols, 1);
		
		this.bias = smf.createMatrix(ycols,ycols);
		if(this.biasMode){			
			InitStrategy bstrat = this.params.getTyped(BilinearLearnerParameters.BIASINITSTRAT);
			this.bias = bstrat.init(ycols, ycols);
			this.diagX = smf.createIdentity(ycols, ycols);
		}
	}
	
	public void process(Matrix X, Matrix Y){
		int nfeatures = X.getNumRows();
		int nusers = X.getNumColumns();
		int ntasks = Y.getNumColumns();
//		int ninstances = Y.getNumRows(); // Assume 1 instance!

		// only inits when the current params is null
		if (this.w == null){
			initParams(nfeatures, nusers, ntasks); // Number of words, users and tasks	
		}
		
		Double dampening = this.params.getTyped(BilinearLearnerParameters.DAMPENING);
		double weighting = 1.0 - dampening ;
		
		logger.debug("... dampening w, u and bias by: " + weighting);
		
		if(indw && indu){ // Both u and w have a column per task
			
			// Adjust for weighting
			this.w.scaleEquals(weighting);
			this.u.scaleEquals(weighting);
			if(this.biasMode){
				this.bias.scaleEquals(weighting);
			}
			// First expand Y s.t. blocks of rows contain the task values for each row of Y. 
			// This means Yexp has (n * t x t)
			SparseMatrix Yexp = expandY(Y);
			loss.setY(Yexp);
			int iter = 0;
			while(true) {
				// We need to set the bias here because it is used in the loss calculation of U and W
				if(this.biasMode) loss.setBias(this.bias);
				iter += 1;
				
				double uLossWeight = etat(iter,eta0_u);
				double wLossWeighted = etat(iter,eta0_w);
				double weightedLambda = lambdat(iter,lambda);
				// Vprime is nusers x tasks
				Matrix Vprime = X.transpose().times(this.w);
				// ... so the loss function's X is (tasks x nusers)
				loss.setX(Vprime.transpose());
				Matrix newu = updateU(this.u,uLossWeight, weightedLambda);
				
				// Dprime is tasks x nwords
				Matrix Dprime = newu.transpose().times(X.transpose());
				// ... as is the cost function's X
				loss.setX(Dprime);
				Matrix neww = updateW(this.w,wLossWeighted, weightedLambda);
				
				
				
				double sumchangew = SandiaMatrixUtils.absSum(neww.minus(this.w));
				double totalw = SandiaMatrixUtils.absSum(this.w);
				
				double sumchangeu = SandiaMatrixUtils.absSum(newu.minus(this.u));
				double totalu = SandiaMatrixUtils.absSum(this.u);
				double ratio = ((sumchangeu/totalu) + (sumchangew/totalw)) / 2;
				
				if(this.biasMode){
					Matrix mult = newu.transpose().times(X.transpose()).times(neww).plus(this.bias);
					// We must set bias to null! 
					loss.setBias(null);
					loss.setX(diagX);
					// Calculate gradient of bias (don't regularise)
					Matrix biasGrad = loss.gradient(mult);
					double biasLossWeight = biasEtat(iter);
					Matrix newbias = updateBias(biasGrad, biasLossWeight);
					
					double sumchangebias = SandiaMatrixUtils.absSum(newbias.minus(this.bias));
					double totalbias = SandiaMatrixUtils.absSum(this.bias);
					
					ratio = ((ratio * 2) + (sumchangebias/totalbias) )/ 3;
					this.bias = newbias;
					
				}
				
				/**
				 * This is not a matter of simply type
				 * The 0 values of the sparse matrix are also removed. very important.
				 */
				if(forceSparcity){
					this.w = smf.copyMatrix(neww);
					this.u = smf.copyMatrix(newu);
				}
				else{
					
					this.w = neww;
					this.u = newu;
				}
				
				Double biconvextol = this.params.getTyped("biconvex_tol");
				Integer maxiter = this.params.getTyped("biconvex_maxiter");
				if(iter%3 == 0){
					logger.debug(String.format("Iter: %d. Last Ratio: %2.3f",iter,ratio));
				}
				if(biconvextol  < 0 || ratio < biconvextol || iter >= maxiter) {
					logger.debug("tolerance reached after iteration: " + iter);
					break;
				}
			}
		}
	}
	protected Matrix updateBias(Matrix biasGrad, double biasLossWeight) {
		Matrix newbias = this.bias.minus(
				SandiaMatrixUtils.timesInplace(
						biasGrad,
						biasLossWeight
				)
		);
		return newbias;
	}
	protected Matrix updateW(Matrix currentW, double wLossWeighted, double weightedLambda) {
		Matrix gradW = loss.gradient(currentW);		
		SandiaMatrixUtils.timesInplace(gradW,wLossWeighted);
		
		Matrix neww = currentW.minus(gradW);
		neww = regul.prox(neww, weightedLambda);
		return neww;
	}
	protected Matrix updateU(Matrix currentU, double uLossWeight, double uWeightedLambda) {
		Matrix gradU = loss.gradient(currentU);
		SandiaMatrixUtils.timesInplace(gradU,uLossWeight);
		Matrix newu = currentU.minus(gradU);
		newu = regul.prox(newu, uWeightedLambda);
		return newu;
	}
	private double lambdat(int iter, double lambda) {
		return lambda/iter;
	}
	public static SparseMatrix expandY(Matrix Y) {
		int ntasks = Y.getNumColumns();
		SparseMatrix Yexp = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(ntasks, ntasks);
		for (int touter = 0; touter < ntasks; touter++) {
			for (int tinner = 0; tinner < ntasks; tinner++) {
				if(tinner == touter){
					Yexp.setElement(touter, tinner, Y.getElement(0, tinner));
				}
				else{
					Yexp.setElement(touter, tinner, Double.NaN);
				}
			}
		}
		return Yexp;
	}
	private double biasEtat(int iter){
		Double biasEta0 = this.params.getTyped(BilinearLearnerParameters.BIASETA0);
		return biasEta0 / Math.sqrt(iter);
	}
	
	private double dimWeightedetat(int iter, int ndims,double eta0) {
		Integer etaSteps = this.params.getTyped(BilinearLearnerParameters.ETASTEPS);
		double sqrtCeil = Math.sqrt(Math.ceil(iter/(double)etaSteps));
		return (eta(eta0) / sqrtCeil) / ndims;
	}
	
	private double etat(int iter,double eta0) {
		Integer etaSteps = this.params.getTyped(BilinearLearnerParameters.ETASTEPS);
		double sqrtCeil = Math.sqrt(Math.ceil(iter/(double)etaSteps));
		return eta(eta0) / sqrtCeil;
	}
	private double eta(double eta0) {
		Integer batchsize = this.params.getTyped(BilinearLearnerParameters.BATCHSIZE);
		return eta0 / batchsize;
	}
	
	
	
	public BilinearLearnerParameters getParams() {
		return this.params;
	}
	
	public Matrix getU(){
		return this.u;
	}
	
	public Matrix getW(){
		return this.w;
	}
	public Matrix getBias() {
		if(this.biasMode)
			return this.bias;
		else
			return null;
	}
	public void addU(int newUsers) {
		if(this.u == null) return; // If u has not be inited, then it will be on first process
		InitStrategy ustrat = this.params.getTyped(BilinearLearnerParameters.UINITSTRAT);
		Matrix newU = ustrat.init(newUsers, this.u.getNumColumns());
		this.u = SandiaMatrixUtils.vstack(this.u,newU);
	}
	
	public void addW(int newWords) {
		if(this.w == null) return; // If w has not be inited, then it will be on first process
		InitStrategy wstrat = this.params.getTyped(BilinearLearnerParameters.WINITSTRAT);
		Matrix newW = wstrat.init(newWords, this.w.getNumColumns());
		this.w = SandiaMatrixUtils.vstack(this.w,newW);
	}
	
	public BilinearSparseOnlineLearner clone(){
		BilinearSparseOnlineLearner ret = new BilinearSparseOnlineLearner(this.getParams());
		ret.u = this.u.clone();
		ret.w = this.w.clone();
		return ret;
	}
	public void setU(Matrix newu) {
		this.u = newu;
	}
	
	public void setW(Matrix neww) {
		this.w = neww;
	}
	@Override
	public void readBinary(DataInput in) throws IOException {
		int nwords = in.readInt();
		int nusers = in.readInt();
		int ntasks = in.readInt();
		
		
		this.w = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(nwords, ntasks);
		for (int t = 0; t < ntasks; t++) {				
			for (int r = 0; r < nwords; r++) {
				double readDouble = in.readDouble();
				if(readDouble != 0){
					this.w.setElement(r, t, readDouble);					
				}
			}
		}
		
		this.u = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(nusers, ntasks);
		for (int t = 0; t < ntasks; t++) {				
			for (int r = 0; r < nusers; r++) {
				double readDouble = in.readDouble();
				if(readDouble != 0){
					this.u.setElement(r, t, readDouble);					
				}
			}
		}
		
		this.bias = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(ntasks, ntasks);
		for (int t1 = 0; t1 < ntasks; t1++) {
			for (int t2 = 0; t2 < ntasks; t2++) {				
				double readDouble = in.readDouble();
				if(readDouble != 0){
					this.bias.setElement(t1, t2, readDouble);					
				}
			}
		}
	}
	@Override
	public byte[] binaryHeader() {
		return "".getBytes();
	}
	@Override
	public void writeBinary(DataOutput out) throws IOException {
		out.writeInt(w.getNumRows());
		out.writeInt(u.getNumRows());
		out.writeInt(u.getNumColumns());
		double[] wdata = SandiaMatrixUtils.getData(w);
		for (int i = 0; i < wdata.length; i++) {
			out.writeDouble(wdata[i]);
		}
		double[] udata = SandiaMatrixUtils.getData(u);
		for (int i = 0; i < udata.length; i++) {
			out.writeDouble(udata[i]);
		}
		double[] biasdata = SandiaMatrixUtils.getData(bias);
		for (int i = 0; i < biasdata.length; i++) {
			out.writeDouble(biasdata[i]);
		}		
	}
	
	public static void main(String[] args) {
		Matrix d = DenseMatrixFactoryMTJ.INSTANCE.createMatrix(5, 10);
		d.setElement(4, 5, 1);
		System.out.println(d);
		d = SparseMatrixFactoryMTJ.INSTANCE.copyMatrix(d);
		System.out.println(d);
	}
	@Override
	public Matrix predict(Matrix x) {
		
		return null;
	}
}
