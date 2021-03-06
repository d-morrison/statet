/*=============================================================================#
 # Copyright (c) 2010-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.ui.dataeditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import de.walware.statet.r.ui.dataeditor.RDataTableComposite;


public class SelectAllHandler extends AbstractHandler {
	
	
	private final RDataTableComposite tableComposite;
	
	
	public SelectAllHandler(final RDataTableComposite table) {
		this.tableComposite = table;
	}
	
	
	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		if (!this.tableComposite.isOK()) {
			return null;
		}
		
		this.tableComposite.selectAll();
		return null;
	}
	
}
