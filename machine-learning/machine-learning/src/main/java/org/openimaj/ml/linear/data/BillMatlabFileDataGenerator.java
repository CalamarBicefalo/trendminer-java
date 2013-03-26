package org.openimaj.ml.linear.data;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.mtj.SparseMatrixFactoryMTJ;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openimaj.util.filter.Filter;
import org.openimaj.util.filter.FilterUtils;
import org.openimaj.util.pair.Pair;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLCell;
import com.jmatio.types.MLDouble;
import com.jmatio.types.MLSparse;

public class BillMatlabFileDataGenerator implements DataGenerator<Matrix>{
	private static class Fold {
		public Fold(int[] training, int[] test, int[] validation) {
			this.training = training;
			this.test = test;
			this.validation = validation;
		}

		int[] training;
		int[] test;
		int[] validation;
	}
	
	public enum Mode{
		TRAINING {
			@Override
			public int[] indexes(Fold fold) {
				return fold.training;
			}
		},TEST {
			@Override
			public int[] indexes(Fold fold) {
				return fold.test;
			}
		},VALIDATION {
			@Override
			public int[] indexes(Fold fold) {
				return fold.validation;
			}
		};
		public abstract int[] indexes(Fold fold) ;
	}
	
	
	private Map<String, MLArray> content;
	private List<Fold> folds;
	private int ndays;
	private int nusers;
	private int nwords;
	private List<Matrix> dayWords;
	private List<Matrix> dayPolls;
	private int currentIndex;
	private int ntasks;
	private int[] indexes;

	public BillMatlabFileDataGenerator(File matfile, int ndays)
			throws IOException {
		MatFileReader reader = new MatFileReader(matfile);
		this.ndays = ndays;
		this.content = reader.getContent();
		this.currentIndex = 0;
		prepareFolds();
		prepareDayUserWords();
		prepareDayPolls();
	}
	
	public void setFold(int fold, Mode mode){
		this.indexes = mode.indexes(this.folds.get(fold));
		this.currentIndex = 0;
	}

	private void prepareDayPolls() {
		ArrayList<String> pollKeys = FilterUtils.filter(this.content.keySet(),
				new Filter<String>() {

					@Override
					public boolean accept(String object) {
						return object.endsWith("per_unique_extended");
					}
				});
		this.ntasks = pollKeys.size();
		dayPolls = new ArrayList<Matrix>();
		for (int i = 0; i < this.ndays; i++) {
			dayPolls.add(SparseMatrixFactoryMTJ.INSTANCE.createMatrix(1,
					this.ntasks));
		}

		for (int t = 0; t < this.ntasks; t++) {
			String pollKey = pollKeys.get(t);
			MLDouble arr = (MLDouble) this.content.get(pollKey);
			for (int i = 0; i < this.ndays; i++) {
				Matrix dayPoll = dayPolls.get(i);
				dayPoll.setElement(0, t, arr.get(i, 0));
			}
		}
	}

	private void prepareDayUserWords() {
		MLSparse arr =  (MLSparse) this.content.get("user_vsr_for_polls");
		Double[] realVals = arr.exportReal();
		int[] rows = arr.getIR();
		int[] cols = arr.getIC();
		this.nwords = arr.getN();
		this.nusers = arr.getM()/this.ndays;
		dayWords = new ArrayList<Matrix>();
		for (int i = 0; i < ndays; i++) {
			Matrix userWord = SparseMatrixFactoryMTJ.INSTANCE.createMatrix(this.nwords, this.nusers);
			dayWords.add(userWord);
		}
		for (int i = 0; i < rows.length; i++) {
			int wordIndex = cols[i];
			int dayIndex = rows[i] / this.nusers;
			int userIndex = rows[i] - (dayIndex * this.nusers);
			
			dayWords.get(dayIndex).setElement(wordIndex, userIndex, realVals[i]);
			
		}
	}

	private void prepareFolds() {

		MLArray setfolds = this.content.get("set_fold");
		if (setfolds.isCell()) {
			this.folds = new ArrayList<Fold>();
			MLCell foldcells = (MLCell) setfolds;
			int nfolds = foldcells.getM();
			System.out.println(String.format("Found %d folds", nfolds));
			for (int i = 0; i < nfolds; i++) {
				MLDouble training = (MLDouble) foldcells.get(i, 0);
				MLDouble test = (MLDouble) foldcells.get(i, 1);
				MLDouble validation = (MLDouble) foldcells.get(i, 2);
				Fold f = new Fold(toIntArray(training), toIntArray(test),
						toIntArray(validation));
				folds.add(f);
			}
		} else {
			throw new RuntimeException(
					"Can't find set_folds in expected format");
		}
	}

	private int[] toIntArray(MLDouble training) {
		int[] arr = new int[training.getN()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = training.get(0, i).intValue();
		}
		return arr;
	}

	@Override
	public Pair<Matrix> generate() {
		if(currentIndex >= this.indexes.length) return null;
		int dayIndex = this.indexes[currentIndex];
		Pair<Matrix> pair = new Pair<Matrix>(this.dayWords.get(dayIndex),this.dayPolls.get(dayIndex));
		currentIndex++;
		return pair;
	}

	public int nFolds() {
		return this.folds.size();
	}
}
