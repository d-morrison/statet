/*=============================================================================#
 # Copyright (c) 2013-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.ui.dataeditor;


public class LoadDataException extends Exception {
	
	private static final long serialVersionUID = -2599418116314887064L;
	
	private final boolean fIsUnrecoverable;
	
	public LoadDataException(final boolean isUnrecoverable) {
		this.fIsUnrecoverable = isUnrecoverable;
	}
	
	public boolean isUnrecoverable() {
		return this.fIsUnrecoverable;
	}
	
}
