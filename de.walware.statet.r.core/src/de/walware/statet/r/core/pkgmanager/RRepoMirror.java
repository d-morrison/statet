/*=============================================================================#
 # Copyright (c) 2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.core.pkgmanager;


public class RRepoMirror extends RRepo {
	
	
	private final String countryCode;
	
	
	public RRepoMirror(final String id, final String name, final String url, final String countryCode) {
		super(id, name, url, null);
		
		this.countryCode= (countryCode != null) ? countryCode.toLowerCase() : null;
	}
	
	
	public String getCountryCode() {
		return this.countryCode;
	}
	
}
