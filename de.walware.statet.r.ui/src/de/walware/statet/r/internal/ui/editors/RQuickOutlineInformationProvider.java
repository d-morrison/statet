/*=============================================================================#
 # Copyright (c) 2013-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.ui.editors;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.widgets.Shell;

import de.walware.ecommons.ltk.LTK;
import de.walware.ecommons.ltk.ui.sourceediting.ISourceEditor;
import de.walware.ecommons.ltk.ui.sourceediting.QuickInformationProvider;

import de.walware.statet.r.core.model.RModel;
import de.walware.statet.r.core.source.IRDocumentConstants;
import de.walware.statet.r.core.source.RHeuristicTokenScanner;


public class RQuickOutlineInformationProvider extends QuickInformationProvider {
	
	
	private RHeuristicTokenScanner scanner;
	
	
	public RQuickOutlineInformationProvider(final ISourceEditor editor, final int viewerOperation) {
		super(editor, RModel.R_TYPE_ID, viewerOperation);
	}
	
	
	@Override
	public IRegion getSubject(final ITextViewer textViewer, final int offset) {
		if (this.scanner == null) {
			this.scanner= RHeuristicTokenScanner.create(getEditor().getDocumentContentInfo());
		}
		try {
			final IDocument document = getEditor().getViewer().getDocument();
			this.scanner.configure(document);
			final IRegion word = this.scanner.findRWord(offset, false, true);
			if (word != null) {
				final ITypedRegion partition = this.scanner.getPartition(word.getOffset());
				if (IRDocumentConstants.R_DEFAULT_CONTENT_CONSTRAINT.matches(partition.getType())
						|| partition.getType() == IRDocumentConstants.R_STRING_CONTENT_TYPE
						|| partition.getType() == IRDocumentConstants.R_QUOTED_SYMBOL_CONTENT_TYPE) {
					return word;
				}
			}
		}
		catch (final Exception e) {
		}
		return new Region(offset, 0);
	}
	
	@Override
	public IInformationControlCreator createInformationPresenterControlCreator() {
		return new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(final Shell parent) {
				return new RQuickOutlineInformationControl(parent, getCommandId());
			}
		};
	}
	
}
