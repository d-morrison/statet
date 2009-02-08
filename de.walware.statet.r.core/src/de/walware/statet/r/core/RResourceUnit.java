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

package de.walware.statet.r.core;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.AbstractDocument;

import de.walware.ecommons.ltk.AstInfo;
import de.walware.ecommons.ltk.IElementName;
import de.walware.ecommons.ltk.IModelElement;
import de.walware.ecommons.ltk.IProblemRequestor;
import de.walware.ecommons.ltk.ISourceUnit;
import de.walware.ecommons.ltk.ISourceUnitModelInfo;
import de.walware.ecommons.ltk.SourceContent;
import de.walware.ecommons.ltk.SourceDocumentRunnable;
import de.walware.ecommons.ltk.WorkingBuffer;
import de.walware.ecommons.ltk.WorkingContext;

import de.walware.statet.base.core.StatetCore;

import de.walware.statet.r.core.model.IRSourceUnit;
import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.core.model.RModel;
import de.walware.statet.r.internal.core.RCorePlugin;


/**
 * Generic source unit for R related files.
 */
public abstract class RResourceUnit implements ISourceUnit {
	
	
	public static String createResourceId(final IResource file) {
		if (file != null) {
			final IPath path = file.getFullPath();
			if (path != null) {
				return "epr:"+path.toPortableString(); // eclipse-platform-resource //$NON-NLS-1$
			}
		}
		return null;
	}
	
	public static RResourceUnit createTempUnit(final IResource file, final String modelTypeId) {
		return new RResourceUnit(file) {
			@Override
			public String getModelTypeId() {
				return modelTypeId;
			}
		};
	}
	
	
	private IResource fFile;
	private String fId;
	private IElementName fName;
	protected int fCounter;
	
	
	public RResourceUnit(final IResource file) {
		fFile = file;
		fName = RElementName.create(RElementName.RESOURCE, (file != null) ? file.getName() : "<no file info>"); //$NON-NLS-1$
		fId = createResourceId(fFile);
		if (fId == null) {
			fId = "xx:"+fName; //$NON-NLS-1$
		}
	}
	
	
	protected void init() {
	}
	
	protected void dispose() {
	}
	
	public abstract String getModelTypeId();
	
	public String getId() {
		return fId;
	}
	
	
	public WorkingContext getWorkingContext() {
		return StatetCore.PERSISTENCE_CONTEXT;
	}
	
	public boolean exists() {
		return true;
	}
	
	public boolean isReadOnly() {
		// true only for e.g. libraries, not because of read only flag
		return false;
	}
	
	public synchronized final void connect(final IProgressMonitor monitor) {
		fCounter++;
		if (fCounter == 1) {
			init();
		}
	}
	
	public synchronized final void disconnect(final IProgressMonitor monitor) {
		fCounter--;
		if (fCounter == 0) {
			dispose();
		}
	}
	
	public ISourceUnit getSourceUnit() {
		return this;
	}
	
	public ISourceUnit getUnderlyingUnit() {
		return null;
	}
	
	
	public int getElementType() {
		return C2_SOURCE_FILE;
	}
	
	public IModelElement getParent() {
		return null; // directory
	}
	
	public boolean hasChildren(final Filter filter) {
		return true;
	}
	
	public List<? extends IModelElement> getChildren(final Filter filter) {
		return NO_CHILDREN;
	}
	
	
	public IElementName getElementName() {
		return fName;
	}
	
	public IResource getResource() {
		return fFile;
	}
	
	public IPath getPath() {
		return fFile.getFullPath();
	}
	
	public AbstractDocument getDocument(final IProgressMonitor monitor) {
		return null;
	}
	
	public SourceContent getContent(final IProgressMonitor monitor) {
		return new WorkingBuffer(this).getContent(monitor);
	}
	
	public void syncExec(final SourceDocumentRunnable runnable) throws InvocationTargetException {
		throw new UnsupportedOperationException();
	}
	
	public RProject getRProject() {
		if (fFile != null) {
			final IProject proj = fFile.getProject();
			try {
				if (proj.hasNature(RProject.NATURE_ID)) {
					return (RProject) proj.getNature(RProject.NATURE_ID);
				}
			} catch (final CoreException e) {
				RCorePlugin.log(new Status(Status.ERROR, RCore.PLUGIN_ID, -1, "An error occurred while access R project nature.", e));
			}
		}
		return null;
	}
	
	public IRCoreAccess getRCoreAccess() {
		final RProject project = getRProject();
		if (project != null) {
			return project;
		}
		return RCore.getWorkbenchAccess();
	}
	
	public Object getAdapter(final Class required) {
		if (required.equals(IRCoreAccess.class)) {
			final RProject rproj = getRProject();
			if (rproj != null) {
				return rproj;
			}
			return RCore.getWorkbenchAccess();
		}
		if (required.equals(IResource.class)) {
			return getResource();
		}
		return null;
	}
	
	
	protected final void register() {
		if (getModelTypeId().equals(RModel.TYPE_ID)) {
			RCorePlugin.getDefault().getRModelManager().registerWorkingCopy((IRSourceUnit) this);
		}
		else {
			RCorePlugin.getDefault().getRModelManager().registerWorksheetCopy(this);
		}
	}
	
	protected final void unregister() {
		if (getModelTypeId().equals(RModel.TYPE_ID)) {
			RCorePlugin.getDefault().getRModelManager().removeWorkingCopy((IRSourceUnit) this);
		}
		else {
			RCorePlugin.getDefault().getRModelManager().removeWorksheetCopy(this);
		}
	}
	
	public AstInfo<?> getAstInfo(final String type, final boolean ensureSync, final IProgressMonitor monitor) {
		return null;
	}
	
	public ISourceUnitModelInfo getModelInfo(final String type, final int syncLevel, final IProgressMonitor monitor) {
		return null;
	}
	
	public IProblemRequestor getProblemRequestor() {
		return null;
	}
	
}
