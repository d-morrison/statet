/*******************************************************************************
 * Copyright (c) 2005-2008 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.ui.editors;

import java.util.Map;
import java.util.Set;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.spelling.SpellingReconcileStrategy;
import org.eclipse.ui.texteditor.spelling.SpellingService;

import de.walware.ecommons.ui.text.EcoReconciler;
import de.walware.ecommons.ui.text.presentation.SingleTokenScanner;
import de.walware.ecommons.ui.text.sourceediting.ContentAssistComputerRegistry;
import de.walware.ecommons.ui.text.sourceediting.ContentAssistProcessor;
import de.walware.ecommons.ui.util.ColorManager;
import de.walware.ecommons.ui.util.ISettingsChangedHandler;

import de.walware.statet.base.ui.sourceeditors.IEditorAdapter;
import de.walware.statet.base.ui.sourceeditors.StatextSourceViewerConfiguration;
import de.walware.statet.ext.ui.text.CommentScanner;

import de.walware.statet.r.core.IRCoreAccess;
import de.walware.statet.r.core.RCodeStyleSettings;
import de.walware.statet.r.core.RCore;
import de.walware.statet.r.core.RCodeStyleSettings.IndentationType;
import de.walware.statet.r.core.rsource.IRDocumentPartitions;
import de.walware.statet.r.core.rsource.RIndentUtil;
import de.walware.statet.r.internal.ui.RUIPlugin;
import de.walware.statet.r.internal.ui.editors.RContentAssistProcessor;
import de.walware.statet.r.internal.ui.editors.RQuickAssistProcessor;
import de.walware.statet.r.internal.ui.editors.RReconcilingStrategy;
import de.walware.statet.r.ui.text.r.RCodeScanner2;
import de.walware.statet.r.ui.text.r.RCommentScanner;
import de.walware.statet.r.ui.text.r.RDoubleClickStrategy;
import de.walware.statet.r.ui.text.r.RInfixOperatorScanner;
import de.walware.statet.r.ui.text.r.RStringScanner;
import de.walware.statet.r.ui.text.r.RoxygenScanner;


/**
 * Default Configuration for SourceViewer of R code.
 */
public class RSourceViewerConfiguration extends StatextSourceViewerConfiguration {
	
	
	protected RCodeScanner2 fCodeScanner;
	protected RInfixOperatorScanner fInfixScanner;
	protected SingleTokenScanner fStringScanner;
	protected CommentScanner fCommentScanner;
	protected CommentScanner fRoxygenScanner;
	
	private RDoubleClickStrategy fDoubleClickStrategy;
	private RAutoEditStrategy fRAutoEditStrategy;
	
	private REditor fEditor;
	private IRCoreAccess fRCoreAccess;
	
	private boolean fHandleDefaultContentType;
	
	
	public RSourceViewerConfiguration(
			final IRCoreAccess rCoreAccess, final IPreferenceStore store, final ColorManager colorManager) {
		this(null, null, null, rCoreAccess, store, colorManager);
	}
	
	public RSourceViewerConfiguration(final IEditorAdapter editor,
			final IRCoreAccess rCoreAccess, final IPreferenceStore store, final ColorManager colorManager) {
		this(null, null, editor, rCoreAccess, store, colorManager);
	}
	
	public RSourceViewerConfiguration(final REditor editor,
			final IRCoreAccess rCoreAccess, final IPreferenceStore preferenceStore, final ColorManager colorManager) {
		this(null, editor, (IEditorAdapter) editor.getAdapter(IEditorAdapter.class),
				rCoreAccess, preferenceStore, colorManager);
	}
	
	protected RSourceViewerConfiguration(final StatextSourceViewerConfiguration parent,
			final REditor editor, final IEditorAdapter adapter,
			final IRCoreAccess rCoreAccess, final IPreferenceStore preferenceStore, final ColorManager colorManager) {
		super(adapter);
		fRCoreAccess = rCoreAccess;
		if (fRCoreAccess == null) {
			fRCoreAccess = RCore.getWorkbenchAccess();
		}
		fEditor = editor;
		fHandleDefaultContentType = true;
		setup(preferenceStore, colorManager);
		setScanners(initializeScanners());
	}
	
	
	public void setHandleDefaultContentType(final boolean enable) {
		fHandleDefaultContentType = enable;
	}
	
	
	public IRCoreAccess getRCoreAccess() {
		return fRCoreAccess;
	}
	
	protected REditor getEditor() {
		return fEditor;
	}
	
	/**
	 * Initializes the scanners.
	 */
	protected ITokenScanner[] initializeScanners() {
		final IPreferenceStore store = getPreferences();
		final ColorManager colorManager = getColorManager();
		fCodeScanner = new RCodeScanner2(colorManager, store);
		fInfixScanner = new RInfixOperatorScanner(colorManager, store);
		fStringScanner = new RStringScanner(colorManager, store);
		fCommentScanner = new RCommentScanner(colorManager, store, fRCoreAccess.getPrefs());
		fRoxygenScanner = new RoxygenScanner(colorManager, store, fRCoreAccess.getPrefs());
		return new ITokenScanner[] { fCodeScanner, fInfixScanner, fStringScanner, fCommentScanner, fRoxygenScanner };
	}
	
	
	@Override
	public String getConfiguredDocumentPartitioning(final ISourceViewer sourceViewer) {
		return IRDocumentPartitions.R_PARTITIONING;
	}
	
	@Override
	public String[] getConfiguredContentTypes(final ISourceViewer sourceViewer) {
		return IRDocumentPartitions.R_PARTITIONS;
	}
	
	@Override
	public ITextDoubleClickStrategy getDoubleClickStrategy(final ISourceViewer sourceViewer, final String contentType) {
		if (fDoubleClickStrategy == null) {
			fDoubleClickStrategy = new RDoubleClickStrategy();
		}
		return fDoubleClickStrategy;
	}
	
	@Override
	public IPresentationReconciler getPresentationReconciler(final ISourceViewer sourceViewer) {
		final PresentationReconciler reconciler = new PresentationReconciler();
		reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
		
		initDefaultPresentationReconciler(reconciler);
		
		return reconciler;
	}
	
	public void initDefaultPresentationReconciler(final PresentationReconciler reconciler) {
		DefaultDamagerRepairer dr = new DefaultDamagerRepairer(fCodeScanner);
		if (fHandleDefaultContentType) {
			reconciler.setDamager(dr, IRDocumentPartitions.R_DEFAULT);
			reconciler.setRepairer(dr, IRDocumentPartitions.R_DEFAULT);
		}
		reconciler.setDamager(dr, IRDocumentPartitions.R_DEFAULT_EXPL);
		reconciler.setRepairer(dr, IRDocumentPartitions.R_DEFAULT_EXPL);
		
		dr = new DefaultDamagerRepairer(fStringScanner);
		reconciler.setDamager(dr, IRDocumentPartitions.R_QUOTED_SYMBOL);
		reconciler.setRepairer(dr, IRDocumentPartitions.R_QUOTED_SYMBOL);
		
		dr = new DefaultDamagerRepairer(fInfixScanner);
		reconciler.setDamager(dr, IRDocumentPartitions.R_INFIX_OPERATOR);
		reconciler.setRepairer(dr, IRDocumentPartitions.R_INFIX_OPERATOR);
		
		dr = new DefaultDamagerRepairer(fStringScanner);
		reconciler.setDamager(dr, IRDocumentPartitions.R_STRING);
		reconciler.setRepairer(dr, IRDocumentPartitions.R_STRING);
		
		dr = new DefaultDamagerRepairer(fCommentScanner);
		reconciler.setDamager(dr, IRDocumentPartitions.R_COMMENT);
		reconciler.setRepairer(dr, IRDocumentPartitions.R_COMMENT);
		
		dr = new DefaultDamagerRepairer(fRoxygenScanner);
		reconciler.setDamager(dr, IRDocumentPartitions.R_ROXYGEN);
		reconciler.setRepairer(dr, IRDocumentPartitions.R_ROXYGEN);
	}
	
	@Override
	public int getTabWidth(final ISourceViewer sourceViewer) {
		return fRCoreAccess.getRCodeStyle().getTabSize();
	}
	
	@Override
	public String[] getDefaultPrefixes(final ISourceViewer sourceViewer, final String contentType) {
		return new String[] { "#", "" }; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	@Override
	public String[] getIndentPrefixes(final ISourceViewer sourceViewer, final String contentType) {
		final String[] prefixes = getIndentPrefixesForTab(getTabWidth(sourceViewer));
		final RCodeStyleSettings codeStyle = fRCoreAccess.getRCodeStyle();
		if (codeStyle.getIndentDefaultType() == IndentationType.SPACES) {
			for (int i = prefixes.length-2; i > 0; i--) {
				prefixes[i] = prefixes[i-1];
			}
			prefixes[0] = new String(RIndentUtil.repeat(' ', codeStyle.getIndentSpacesCount()));
		}
		return prefixes;
	}
	
	@Override
	public void handleSettingsChanged(final Set<String> groupIds, final Map<String, Object> options) {
		options.put(ISettingsChangedHandler.PREFERENCEACCESS_KEY, fRCoreAccess.getPrefs());
		super.handleSettingsChanged(groupIds, options);
	}
	
	
	@Override
	public IReconciler getReconciler(final ISourceViewer sourceViewer) {
		if (fEditor == null) { // at moment only for editors
			return null;
		}
		final EcoReconciler reconciler = new EcoReconciler(fEditor);
		reconciler.setDelay(500);
		reconciler.addReconcilingStrategy(new RReconcilingStrategy());
		
		final IReconcilingStrategy spellingStrategy = getSpellingStrategy(sourceViewer);
		if (spellingStrategy != null) {
			reconciler.addReconcilingStrategy(spellingStrategy);
		}
		
		return reconciler;
	}
	
	protected IReconcilingStrategy getSpellingStrategy(final ISourceViewer sourceViewer) {
		if (!(fRCoreAccess.getPrefs().getPreferenceValue(REditorOptions.PREF_SPELLCHECKING_ENABLED)
				&& fPreferenceStore.getBoolean(SpellingService.PREFERENCE_SPELLING_ENABLED)) ) {
			return null;
		}
		final SpellingService spellingService = EditorsUI.getSpellingService();
		if (spellingService.getActiveSpellingEngineDescriptor(fPreferenceStore) == null) {
			return null;
		}
		return new SpellingReconcileStrategy(sourceViewer, spellingService);
	}
	
	@Override
	public IAutoEditStrategy[] getAutoEditStrategies(final ISourceViewer sourceViewer, final String contentType) {
		if (getEditorAdapter() == null) {
			return super.getAutoEditStrategies(sourceViewer, contentType);
		}
		if (fRAutoEditStrategy == null) {
			fRAutoEditStrategy = createRAutoEditStrategy();
			getEditorAdapter().install(fRAutoEditStrategy);
		}
		return new IAutoEditStrategy[] { fRAutoEditStrategy };
	}
	
	protected RAutoEditStrategy createRAutoEditStrategy() {
		return new RAutoEditStrategy(fRCoreAccess, getEditorAdapter(), fEditor);
	}
	
	@Override
	protected ContentAssistant createContentAssistant(final ISourceViewer sourceViewer) {
		if (getSourceEditor() != null) {
			final ContentAssistant assistant = new ContentAssistant();
			assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
			
			initDefaultContentAssist(assistant);
			return assistant;
		}
		return null;
	}
	
	public void initDefaultContentAssist(final ContentAssistant assistant) {
		final ContentAssistComputerRegistry registry = RUIPlugin.getDefault().getREditorContentAssistRegistry();
		
		final ContentAssistProcessor codeProcessor = new RContentAssistProcessor(assistant,
				IRDocumentPartitions.R_DEFAULT_EXPL, registry, getSourceEditor());
//		codeProcessor.setCompletionProposalAutoActivationCharacters(new char[] { '$' });
		assistant.setContentAssistProcessor(codeProcessor, IRDocumentPartitions.R_DEFAULT_EXPL);
		if (fHandleDefaultContentType) {
			assistant.setContentAssistProcessor(codeProcessor, IRDocumentPartitions.R_DEFAULT);
		}
		
		final ContentAssistProcessor symbolProcessor = new RContentAssistProcessor(assistant,
				IRDocumentPartitions.R_QUOTED_SYMBOL, registry, getSourceEditor());
		assistant.setContentAssistProcessor(symbolProcessor, IRDocumentPartitions.R_QUOTED_SYMBOL);
		
		final ContentAssistProcessor stringProcessor = new RContentAssistProcessor(assistant,
				IRDocumentPartitions.R_STRING, registry, getSourceEditor());
		assistant.setContentAssistProcessor(stringProcessor, IRDocumentPartitions.R_STRING);
		
		final ContentAssistProcessor commentProcessor = new RContentAssistProcessor(assistant,
				IRDocumentPartitions.R_COMMENT, registry, getSourceEditor());
		assistant.setContentAssistProcessor(commentProcessor, IRDocumentPartitions.R_COMMENT);
		
		final ContentAssistProcessor roxygenProcessor = new RContentAssistProcessor(assistant,
				IRDocumentPartitions.R_ROXYGEN, registry, getSourceEditor());
		roxygenProcessor.setCompletionProposalAutoActivationCharacters(new char[] { '@', '\\' });
		assistant.setContentAssistProcessor(roxygenProcessor, IRDocumentPartitions.R_ROXYGEN);
	}
	
	@Override
	protected IQuickAssistAssistant createQuickAssistant(final ISourceViewer sourceViewer) {
		final QuickAssistAssistant assistant = new QuickAssistAssistant();
		assistant.setQuickAssistProcessor(new RQuickAssistProcessor(fEditor));
		return assistant;
	}
	
}
