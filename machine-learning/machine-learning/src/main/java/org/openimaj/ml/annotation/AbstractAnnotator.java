/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.ml.annotation;

import org.openimaj.experiment.evaluation.classification.BasicClassificationResult;
import org.openimaj.experiment.evaluation.classification.ClassificationResult;
import org.openimaj.feature.FeatureExtractor;

/**
 * Abstract base class for objects capable of annotating things. Implementors
 * should consider extending {@link BatchAnnotator} or
 * {@link IncrementalAnnotator} instead of subclassing {@link AbstractAnnotator}
 * directly.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 * 
 * @param <OBJECT>
 *            Type of object being annotated
 * @param <ANNOTATION>
 *            Type of annotation
 * @param <EXTRACTOR>
 *            Type of object capable of extracting features from the object
 */
public abstract class AbstractAnnotator<OBJECT, ANNOTATION, EXTRACTOR extends FeatureExtractor<?, OBJECT>>
		implements
		Annotator<OBJECT, ANNOTATION, EXTRACTOR>
{
	/**
	 * The underlying feature extractor
	 */
	public EXTRACTOR extractor;

	protected AbstractAnnotator() {
	}

	/**
	 * Construct with the given feature extractor.
	 * 
	 * @param extractor
	 *            the feature extractor
	 */
	public AbstractAnnotator(EXTRACTOR extractor) {
		this.extractor = extractor;
	}

	@Override
	public ClassificationResult<ANNOTATION> classify(OBJECT object) {
		final BasicClassificationResult<ANNOTATION> res = new BasicClassificationResult<ANNOTATION>();

		for (final ScoredAnnotation<ANNOTATION> anno : this.annotate(object)) {
			res.put(anno.annotation, anno.confidence);
		}

		return res;
	}
}