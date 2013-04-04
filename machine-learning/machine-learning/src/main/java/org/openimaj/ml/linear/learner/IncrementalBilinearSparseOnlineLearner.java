package org.openimaj.ml.linear.learner;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.mtj.SparseMatrixFactoryMTJ;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.openimaj.util.pair.IndependentPair;
import org.openimaj.util.pair.Pair;

public class IncrementalBilinearSparseOnlineLearner implements OnlineLearner<Map<String, Map<String, Double>>,Map<String,Double>>{
	static class IncrementalBilinearSparseOnlineLearnerParams extends BilinearLearnerParameters{

	/**
	 * 
	 */
	private static final long serialVersionUID = -1847045895118918210L;
		
	}
	private HashMap<String, Integer> vocabulary;
	private HashMap<String, Integer> users;
	private HashMap<String, Integer> values;
	private BilinearSparseOnlineLearner bilinearLearner;

	public IncrementalBilinearSparseOnlineLearner() {
		init(new IncrementalBilinearSparseOnlineLearnerParams());
	}
	
	public IncrementalBilinearSparseOnlineLearner(BilinearLearnerParameters params){
		init(params);
	}

	private void init(BilinearLearnerParameters params) {
		vocabulary = new HashMap<String,Integer>();
		users = new HashMap<String, Integer>();
		values = new HashMap<String, Integer>();
		bilinearLearner = new BilinearSparseOnlineLearner(params);
	}
	
	@Override
	public void process(Map<String, Map<String, Double>> x, Map<String, Double> y) {
		updateUserWords(x);
		updateValues(y);
		Matrix yMat = constructYMatrix(y);
		Matrix xMat = constructXMatrix(x);
		
		this.bilinearLearner.process(xMat, yMat);
	}

	private void updateValues(Map<String, Double> y) {
		for (String value : y.keySet()) {
			if(!values.containsKey(value)){
				values.put(value,values.size());
			}
		}
	}

	private Matrix constructYMatrix(Map<String, Double> y) {
		Matrix mat = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(1, values.size());
		for (Entry<String, Double> ent : y.entrySet()) {
			mat.setElement(0, values.get(ent.getKey()), ent.getValue());
		}
		return mat;
	}
	
	private Matrix constructXMatrix(Map<String,Map<String, Double>> x) {
		Matrix mat = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(vocabulary.size(), users.size());
		for (Entry<String, Map<String, Double>> userwords : x.entrySet()) {
			int userindex = this.users.get(userwords.getKey());
			for (Entry<String, Double> ent : userwords.getValue().entrySet()) {				
				mat.setElement(vocabulary.get(ent.getKey()), userindex, ent.getValue());
			}
		}
		return mat;
	}

	private void updateUserWords(Map<String, Map<String, Double>> x) {
		int newUsers = 0;
		int newWords = 0;
		for (Entry<String, Map<String, Double>> userWords: x.entrySet()) {
			String user = userWords.getKey();
			if(!users.containsKey(user)){
				users.put(user, users.size());
				newUsers++;
			}
			newWords += updateWords(userWords.getValue());
		}
		
		this.bilinearLearner.addU(newUsers);
		this.bilinearLearner.addW(newWords);
	}

	private int updateWords(Map<String, Double> value) {
		int newWords = 0;
		for (String word : value.keySet()) {
			if(!vocabulary.containsKey(word)){
				vocabulary.put(word, vocabulary.size());
				newWords++;
			}
		}
		return newWords;
	}

	public BilinearSparseOnlineLearner getBilinearLearner() {
		return this.bilinearLearner;
	}

	public Pair<Matrix> asMatrixPair(
			IndependentPair<Map<String, Map<String, Double>>, Map<String, Double>> xy,
			int nfeatures, int nusers, int ntasks) {
		Matrix y = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(1, ntasks);
		Matrix x = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(nfeatures, nusers);
		Map<String, Double> ymap = xy.secondObject();
		Map<String, Map<String, Double>> userFeatureMap = xy.firstObject();
		for (Entry<String, Double> yent : ymap.entrySet()) {
			y.setElement(0, this.values.get(yent.getKey()), yent.getValue());
		}
		for (Entry<String, Map<String, Double>> xent : userFeatureMap.entrySet()) {
			int userind = this.users.get(xent.getKey());
			for (Entry<String, Double> fent : xent.getValue().entrySet()) {
				x.setElement(this.vocabulary.get(fent.getKey()), userind, fent.getValue());
			}
		}
		return new Pair<Matrix>(x, y);
	}
	
}
