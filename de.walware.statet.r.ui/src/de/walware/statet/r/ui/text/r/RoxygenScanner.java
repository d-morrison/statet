/*=============================================================================#
 # Copyright (c) 2008-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.ui.text.r;

import java.util.List;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

import de.walware.ecommons.preferences.IPreferenceAccess;
import de.walware.ecommons.text.ui.settings.TextStyleManager;

import de.walware.statet.ext.ui.text.CommentScanner;


/**
 * Scanner for Roxygen comments.
 */
public class RoxygenScanner extends CommentScanner {
	
	
	private static class RoxygenTagRule implements IPredicateRule {
		
		private final IToken fTagToken;
		private final IToken fDefaultToken;
		
		public RoxygenTagRule(final IToken tagToken, final IToken defaultToken) {
			fTagToken = tagToken;
			fDefaultToken = defaultToken;
		}
		
		@Override
		public IToken getSuccessToken() {
			return fTagToken;
		}
		
		@Override
		public IToken evaluate(final ICharacterScanner scanner) {
			return evaluate(scanner, false);
		}
		
		@Override
		public IToken evaluate(final ICharacterScanner scanner, final boolean resume) {
			int c;
			if (!resume) {
				final int c0 = scanner.read();
				if (c0 != '@') {
					if (c0 != ICharacterScanner.EOF) { 
						scanner.unread();
					}
					return Token.UNDEFINED;
				}
				c = scanner.read();
				if (c == '@') {
					return fDefaultToken;
				}
			}
			else {
				c = scanner.read();
			}
			while (c != ICharacterScanner.EOF) {
				if (c == '@' || !isRoxygenTagChar(c)) {
					scanner.unread();
					break;
				}
				c = scanner.read();
			}
			return fTagToken;
		}
		
		private boolean isRoxygenTagChar(final int c) {
			if ((c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A)) {
				return true;
			}
			final int type = Character.getType(c);
			return (type > 0) && (type < 12 || type > 19);
		}
		
	}
	
	
	public RoxygenScanner(final TextStyleManager textStyles, final IPreferenceAccess corePrefs) {
		super(textStyles, IRTextTokens.ROXYGEN_KEY, IRTextTokens.TASK_TAG_KEY, corePrefs);
	}
	
	
	@Override
	protected void createRules(final List<IRule> rules) {
		super.createRules(rules);
		
		rules.add(new RoxygenTagRule(getToken(IRTextTokens.ROXYGEN_TAG_KEY), fDefaultReturnToken));
	}
	
}
