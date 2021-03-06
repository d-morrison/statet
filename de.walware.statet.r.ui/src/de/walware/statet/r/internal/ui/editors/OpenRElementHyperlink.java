/*=============================================================================#
 # Copyright (c) 2009-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.ui.editors;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.statushandlers.StatusManager;

import de.walware.ecommons.ltk.core.model.ISourceElement;
import de.walware.ecommons.ltk.ui.sourceediting.ISourceEditor;
import de.walware.ecommons.ui.SharedUIResources;

import de.walware.statet.r.core.model.IRSourceUnit;
import de.walware.statet.r.core.model.RElementAccess;
import de.walware.statet.r.core.model.RModel;
import de.walware.statet.r.core.rsource.ast.RAst;
import de.walware.statet.r.ui.sourceediting.ROpenDeclaration;


public class OpenRElementHyperlink implements IHyperlink {
	
	
	private final ISourceEditor editor;
	private final IRegion region;
	
	private final IRSourceUnit su;
	private final RElementAccess access;
	
	
	public OpenRElementHyperlink(final ISourceEditor editor, final IRSourceUnit su, final RElementAccess access) {
		assert (su != null);
		assert (access != null);
		
		this.editor= editor;
		this.region= RAst.getElementNameRegion(access.getNameNode());
		this.su= su;
		this.access= access;
	}
	
	
	@Override
	public String getTypeLabel() {
		return null;
	}
	
	@Override
	public IRegion getHyperlinkRegion() {
		return this.region;
	}
	
	@Override
	public String getHyperlinkText() {
		return NLS.bind(Messages.Hyperlinks_OpenDeclaration_label, this.access.getDisplayName());
	}
	
	@Override
	public void open() {
		try {
			final List<ISourceElement> list= RModel.searchDeclaration(this.access, this.su);
			final ROpenDeclaration open= new ROpenDeclaration();
			final ISourceElement element= open.selectElement(list, this.editor.getWorkbenchPart());
			if (element != null) {
				open.open(element, true);
			}
			else {
				Display.getCurrent().beep();
			}
		}
		catch (final PartInitException e) {
			Display.getCurrent().beep();
			StatusManager.getManager().handle(new Status(IStatus.INFO, SharedUIResources.PLUGIN_ID, -1,
					NLS.bind("An error occurred when following the hyperlink and opening the editor for the declaration of ''{0}''", this.access.getDisplayName()), e));
		}
		catch (final CoreException e) {
			// cancelled
		}
	}
	
}
