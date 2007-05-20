/*******************************************************************************
 * Copyright (c) 2007 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.eclipsecommons;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.osgi.util.NLS;

import de.walware.statet.base.core.StatetCore;

import de.walware.eclipsecommons.internal.fileutil.Messages;


/**
 * A configurable resource validator.
 * 
 * Validates <code>String</code> (with variables) representing a local file path 
 * or a URI and file handles of type <code>IFileStore</code> and 
 * <code>IResource</code> (for Workspace resources).
 */
public class FileValidator implements IValidator {

	
	private Object fExplicitObject;
	private IResource fWorkspaceResource;
	private IFileStore fFileStore;
	private IStatus fStatus;
	
	private String fResourceLabel;
	private int fOnEmpty;
	private int fOnNotExisting;
	private int fOnExisting;
	private int fOnLateResolve;
	private int fOnFile;
	private int fOnDirectory;

	
	/**
	 * 
	 */
	public FileValidator() {
		
		fOnNotExisting = IStatus.OK;
		fOnExisting = IStatus.OK;
		fOnEmpty = IStatus.ERROR;
		fOnLateResolve = IStatus.ERROR;
		fOnFile = IStatus.OK;
		fOnDirectory = IStatus.OK;
	}
	
	/**
	 * 
	 */
	public FileValidator(boolean existingResource) {
		
		this();
		setDefaultMode(existingResource);
	}
	
	public void setDefaultMode(boolean existingResource) {
		
		fOnNotExisting = (existingResource) ? IStatus.ERROR : IStatus.OK;
		fOnExisting = (existingResource) ? IStatus.OK : IStatus.WARNING;
	}
	
	
	public void setOnEmpty(int severity) {
		fOnEmpty = severity;
		fStatus = null;
	}
	public int getOnEmpty() {
		return fOnEmpty;
	}

	public void setOnExisting(int severity) {
		fStatus = null;
		fOnExisting = severity;
	}
	public int getOnExisting() {
		return fOnExisting;
	}

	public void setOnNotExisting(int severity) {
		fStatus = null;
		fOnNotExisting = severity;
	}
	public int getOnNotExisting() {
		return fOnNotExisting;
	}
	
	public void setOnLateResolve(int severity) {
		fStatus = null;
		fOnLateResolve = severity;
	}
	public int getOnLateResolve() {
		return fOnLateResolve;
	}
	
	public void setOnFile(int severity) {
		fStatus = null;
		fOnFile = severity;
	}
	public int getOnFile() {
		return fOnFile;
	}

	public void setOnDirectory(int severity) {
		fStatus = null;
		fOnDirectory = severity;
	}
	public int getOnDirectory() {
		return fOnDirectory;
	}

	public void setResourceLabel(String label) {
		
		fResourceLabel = label;
	}

	/**
	 * Sets explicitly the object to validate.
	 * A <code>null</code> value stops the explicit mode.  If the value is set 
	 * explicitly, the value specified in the validate(...) methods is ignored.
	 * @param value the resource to validate or <code>null</code>.
	 */
	public void setExplicit(Object value) {
		
		fFileStore = null;
		fWorkspaceResource = null;
		fStatus = null;
		fExplicitObject = value;
	}
	
	public IStatus validate(Object value) {
		
		if (!checkExplicit()) {
			if (fStatus == null) {
				fStatus = doValidate(value);
			}
		}
		return fStatus;
	}
	
	private boolean checkExplicit() {
		
		if (fExplicitObject != null) {
			if (fStatus == null) {
				fStatus = doValidate(fExplicitObject);
			}
			return true;
		}
		return false;
	}
	
	private IStatus doValidate(Object value) {
		
		fFileStore = null;
		fWorkspaceResource = null;
		
		// Resolve string
		if (value instanceof String) {
			String s = (String) value;
			if (s.trim().length() == 0) {
				return createStatus(fOnEmpty, Messages.Resource_error_NoInput_message);
			}
			try {
				s = resolveExpression(s);
			} catch (CoreException e) {
				return e.getStatus();
			}
			fWorkspaceResource = ResourcesPlugin.getWorkspace().getRoot().findMember(s, false);
			if (fWorkspaceResource == null) {
				try {
					fFileStore = findFileStore(s);
					if (fFileStore == null) {
						return createStatus(IStatus.ERROR, Messages.Resource_error_NoValidSpecification_message);
					}
				} catch (CoreException e) {
					return createStatus(IStatus.ERROR, Messages.Resource_error_NoValidSpecification_message + " " + e.getStatus().getMessage() + "."); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
		if (value instanceof IFileStore) {
			fFileStore = (IFileStore) value;
		}
		else if (value instanceof IResource) {
			fWorkspaceResource = (IResource) value;
		}

		
		if (fFileStore != null) {
			return validateFileStore();
		}
		else if (fWorkspaceResource != null) {
			return validateWorkspaceResource();
		}
		else {
			throw new IllegalArgumentException();
		}
	}
	
	protected String resolveExpression(String expression) throws CoreException {

		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		try {
			return manager.performStringSubstitution(expression);
		}
		catch (CoreException e) {
			manager.validateStringVariables(expression); // throws invalid variable
			throw new CoreException(createStatus(fOnLateResolve, e.getStatus().getMessage())); // throws runtime variable
		}
	}

	private IFileStore findFileStore(String location) throws CoreException {
		
		try {
			IFileStore store = FileUtil.getLocalFileStore(location);
			if (store != null) {
				return store;
			}
			return null; 
		}
		catch (CoreException e) {
			// not local
		}
		try {
			URI uri = new URI(location);
			if (uri.getScheme() != null) {
				return EFS.getStore(uri);
			}
		}
		catch (URISyntaxException e) {
		}
		return null;
	}
	
	private IResource findWorkspaceResource(URI location) {
		
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IResource[] found = null;
		if (fOnFile != IStatus.ERROR) {
			found = root.findFilesForLocationURI(location);
		}
		if ((found == null || found.length == 0)
				&& fOnDirectory != IStatus.ERROR) {
			found = root.findContainersForLocationURI(location);
		}
		if (found != null && found.length > 0) {
			return found[0];
		}
		return null;
	}
	
	protected IStatus validateWorkspaceResource() {
		
		
		if (fOnExisting != IStatus.OK || fOnNotExisting != IStatus.OK || fOnFile != IStatus.OK || fOnDirectory != IStatus.OK) {
			return createExistsStatus(fWorkspaceResource.exists(), (fWorkspaceResource instanceof IContainer));
		}
		return Status.OK_STATUS;
	}

	protected IStatus validateFileStore() {
		
		if (fOnExisting != IStatus.OK || fOnNotExisting != IStatus.OK) {
			IFileInfo info = fFileStore.fetchInfo();
			return createExistsStatus(info.exists(), info.isDirectory());
		}
		return Status.OK_STATUS;
	}
	
	private IStatus createExistsStatus(boolean exists, boolean isDirectory) {
		if (exists) {
			IStatus status = createStatus(fOnExisting, Messages.Resource_error_AlreadyExists_message);
			if (status.getSeverity() < fOnDirectory && isDirectory) {
				status = createStatus(fOnDirectory, Messages.Resource_error_IsDirectory_message);
			}
			if (status.getSeverity() < fOnFile && !isDirectory) {
				status = createStatus(fOnFile, Messages.Resource_error_IsFile_message);
			}
			return status;
		}
		else {
			return createStatus(fOnNotExisting, Messages.Resource_error_DoesNotExists_message);
		}
	}
	
	protected IStatus createStatus(int severity, String message) {
		
		if (severity == IStatus.OK) {
			return Status.OK_STATUS;
		}
		return new Status(severity, StatetCore.PLUGIN_ID, NLS.bind(message, fResourceLabel));
	}
	
	
	public IFileStore getFileStore() {
		
		checkExplicit();
		if (fFileStore == null && fWorkspaceResource != null) {
			IPath path = fWorkspaceResource.getLocation();
			if (path != null) {
				fFileStore = EFS.getLocalFileSystem().getStore(path);
			}
		}
		return fFileStore;
	}
	
	public IResource getWorkspaceResource() {
		
		checkExplicit();
		if (fWorkspaceResource == null && fFileStore != null) {
			fWorkspaceResource = findWorkspaceResource(fFileStore.toURI());
		}
		return fWorkspaceResource;
	}
	
	public boolean isLocalFile() {
		
		IFileStore fileStore = getFileStore();
		if (fileStore != null) {
			return fileStore.getFileSystem().equals(EFS.getLocalFileSystem());
		}
		return false;
	}
	
	public IStatus getStatus() {
		
		checkExplicit();
		return fStatus;
	}
}