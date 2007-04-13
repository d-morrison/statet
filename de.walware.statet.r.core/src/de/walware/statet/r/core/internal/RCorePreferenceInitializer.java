/*******************************************************************************
 * Copyright (c) 2007 StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.core.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;

import de.walware.eclipsecommons.preferences.Preference;
import de.walware.eclipsecommons.preferences.PreferencesUtil;

import de.walware.statet.r.core.RCodeStyleSettings;


/**
 * Preference Initializer for the preferences of 'StatET R Core' plug-in
 */
public class RCorePreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		
		DefaultScope defaultScope = new DefaultScope();
		Map<Preference, Object> defaults = new HashMap<Preference, Object>();
		
		new RCodeStyleSettings().deliverToPreferencesMap(defaults);
		
		for (Preference<Object> unit : defaults.keySet()) {
			PreferencesUtil.setPrefValue(defaultScope, unit, defaults.get(unit));
		}
	}

}
