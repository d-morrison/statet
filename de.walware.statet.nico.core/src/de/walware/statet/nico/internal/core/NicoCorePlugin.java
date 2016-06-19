/*=============================================================================#
 # Copyright (c) 2006-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.nico.internal.core;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

import de.walware.statet.nico.core.NicoCore;


/**
 * The activator class controls the plug-in life cycle
 */
public class NicoCorePlugin extends Plugin {
	
	
	public static final int INTERNAL_ERROR = 100;
	public static final int EXTERNAL_ERROR = 105;
	
	
	/** The shared instance. */
	private static NicoCorePlugin gPlugin;
	
	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static NicoCorePlugin getDefault() {
		return gPlugin;
	}
	
	public static void log(final IStatus status) {
		getDefault().getLog().log(status);
	}
	
	public static void logError(final int code, final String message, final Throwable e) {
		log(new Status(IStatus.ERROR, NicoCore.PLUGIN_ID, code, message, e));
	}
	
	
	/**
	 * The constructor
	 */
	public NicoCorePlugin() {
		gPlugin = this;
	}
	
	
	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
	}
	
	@Override
	public void stop(final BundleContext context) throws Exception {
		gPlugin = null;
		super.stop(context);
	}
	
}
