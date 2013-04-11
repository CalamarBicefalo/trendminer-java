package org.openimaj.ml.linear.experiments;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.MatrixFactory;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;
import gov.sandia.cognition.math.matrix.VectorUtil;
import gov.sandia.cognition.math.matrix.mtj.DenseVector;
import gov.sandia.cognition.math.matrix.mtj.DenseVectorFactoryMTJ;
import gov.sandia.cognition.math.matrix.mtj.SparseVector;

import jal.doubles.Range;
import jal.doubles.Sorting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.openimaj.io.IOUtils;
import org.openimaj.math.matrix.SandiaMatrixUtils;
import org.openimaj.ml.linear.data.BillMatlabFileDataGenerator;
import org.openimaj.ml.linear.data.BillMatlabFileDataGenerator.Mode;
import org.openimaj.ml.linear.evaluation.BilinearEvaluator;
import org.openimaj.ml.linear.evaluation.RootMeanSumLossEvaluator;
import org.openimaj.ml.linear.learner.BilinearLearnerParameters;
import org.openimaj.ml.linear.learner.BilinearSparseOnlineLearner;
import org.openimaj.ml.linear.learner.init.OnesInitStrategy;
import org.openimaj.ml.linear.learner.init.SingleValueInitStrat;
import org.openimaj.ml.linear.learner.init.SparseOnesInitStrategy;
import org.openimaj.ml.linear.learner.init.SparseZerosInitStrategy;
import org.openimaj.util.pair.Pair;

public class AustrianWordExperiments extends BillAustrianExperiments{
	public static void main(String[] args) throws IOException {
		
		
		
		BilinearLearnerParameters params = new BilinearLearnerParameters();
		params.put(BilinearLearnerParameters.ETA0_U, 0.0002);
		params.put(BilinearLearnerParameters.ETA0_W, 0.002);
		params.put(BilinearLearnerParameters.LAMBDA, 0.001);
		params.put(BilinearLearnerParameters.BICONVEX_TOL, 0.05);
		params.put(BilinearLearnerParameters.BICONVEX_MAXITER, 5);
		params.put(BilinearLearnerParameters.BIAS, true);
		params.put(BilinearLearnerParameters.BIASETA0, 0.05);
		Random initRandom = new Random(1);
		params.put(BilinearLearnerParameters.WINITSTRAT, new SingleValueInitStrat(0.1));
		params.put(BilinearLearnerParameters.UINITSTRAT, new SparseZerosInitStrategy());
		BillMatlabFileDataGenerator bmfdg = new BillMatlabFileDataGenerator(new File(BILL_DATA()), 98,true);
		prepareExperimentLog(params);
		int fold = 0;
		File foldParamFile = new File(prepareExperimentRoot(),String.format("fold_%d_learner", fold));
//		File foldParamFile = new File("/Users/ss/Dropbox/TrendMiner/deliverables/year2-18month/Austrian Data/streamingExperiments/experiment_1365582027691/fold_0_learner");
		logger.debug("Fold: " + fold );
		BilinearSparseOnlineLearner learner = new BilinearSparseOnlineLearner(params);
		learner.reinitParams();			
		bmfdg.setFold(fold, Mode.TEST);
		
		logger.debug("...training");
		bmfdg.setFold(fold, Mode.TRAINING);
		int j = 0;
		Pair<Matrix> wu;	
		if(!foldParamFile .exists()){
			
			while(true){
				Pair<Matrix> next = bmfdg.generate();
				if(next == null) break;
				logger.debug("...trying item "+j++);
				learner.process(next.firstObject(), next.secondObject());
			}
			System.out.println("Writing W and U to: " + foldParamFile);
			wu = new Pair<Matrix>(learner.getW(),learner.getU());
			IOUtils.write(wu, new DataOutputStream(new FileOutputStream(foldParamFile)));
		}else{
			wu = IOUtils.read(new DataInputStream(new FileInputStream(foldParamFile)));	
		}
		
		Matrix w = wu.getFirstObject();
		int ncols = w.getNumColumns();
		int nwords = 20;
		for (int c = 0; c < ncols; c++) {
			System.out.println("Top " + nwords + " words for: " + bmfdg.getTasks()[c]);
			Vector col = w.getColumn(c);
			double[] wordWeights = new DenseVectorFactoryMTJ().copyVector(col).getArray();
			Integer[] integerRange = ArrayIndexComparator.integerRange(wordWeights);
			Arrays.sort(integerRange, new ArrayIndexComparator(wordWeights));
			for (int i = wordWeights.length-1; i >= wordWeights.length-nwords; i--) {
				System.out.printf("%s: %1.5f\n",bmfdg.getVocabulary().get(integerRange[i]),wordWeights[integerRange[i]]);
			}
		}
	}
}
