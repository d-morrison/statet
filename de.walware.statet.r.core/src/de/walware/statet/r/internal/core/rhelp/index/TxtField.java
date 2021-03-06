/*=============================================================================#
 # Copyright (c) 2014-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.core.rhelp.index;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;


/** 
 * A field that is indexed and tokenized, with term vectors.  For example this would be used on a 
 * 'body' field, that contains the bulk of a document's text.
 * 
 * Like {@link org.apache.lucene.document.TextField}, but stores term vector positions and
 * offsets, as required e.g. by vectorhightlighting.
 **/
final class TxtField extends StringDataField {
	
	public static final FieldType TYPE_STORED;
	public static final FieldType TYPE_STORED_OMIT_NORM;
	
	static {
		TYPE_STORED= new FieldType(TextField.TYPE_STORED);
		TYPE_STORED.setStoreTermVectors(true);
		TYPE_STORED.setStoreTermVectorPositions(true);
		TYPE_STORED.setStoreTermVectorOffsets(true);
		TYPE_STORED.freeze();
		
		TYPE_STORED_OMIT_NORM= new FieldType(TYPE_STORED);
		TYPE_STORED_OMIT_NORM.setOmitNorms(true);
		TYPE_STORED_OMIT_NORM.freeze();
	}
	
	
	static final class OmitNorm extends StringDataField {
		
		
		/**
		 * Creates a new field.
		 * 
		 * @param name field name
		 * @throws IllegalArgumentException if the field name.
		 */
		public OmitNorm(final String name) {
			super(name, TYPE_STORED_OMIT_NORM);
		}
		
		
	}
	
	
	/**
	 * Creates a new field.
	 * 
	 * @param name field name
	 * @throws IllegalArgumentException if the field name.
	 */
	public TxtField(final String name) {
		super(name, TYPE_STORED);
	}
	
	/**
	 * Creates a new field.
	 * 
	 * @param name field name
	 * @param boost the field boost
	 * @throws IllegalArgumentException if the field name.
	 */
	public TxtField(final String name, final float boost) {
		super(name, TYPE_STORED);
		setBoost(boost);
	}
	
}
