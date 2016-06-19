/*=============================================================================#
 # Copyright (c) 2010-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.debug.core.sourcelookup;


public interface IRSourceLookupMatch {
	
	
	IRSourceLookupMatch NO_CONTEXT_INFORMATION= new IRSourceLookupMatch() {
		@Override
		public Object getElement() {
			return null;
		}
		@Override
		public void select() {
		}
	};
	
	
	Object getElement();
	
	void select();
	
}
