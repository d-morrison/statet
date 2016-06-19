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

package de.walware.statet.r.console.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.variables.IStringVariable;

import de.walware.jcommons.collections.ImCollections;
import de.walware.jcommons.collections.ImList;

import de.walware.statet.nico.core.NicoVariables;
import de.walware.statet.nico.core.runtime.IConsoleService;
import de.walware.statet.nico.core.runtime.Prompt;
import de.walware.statet.nico.core.runtime.ToolWorkspace;

import de.walware.rj.data.RDataUtil;
import de.walware.rj.data.REnvironment;
import de.walware.rj.data.RList;
import de.walware.rj.data.RObject;
import de.walware.rj.data.RReference;
import de.walware.rj.services.RService;

import de.walware.statet.r.core.RCore;
import de.walware.statet.r.core.data.ICombinedRElement;
import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.core.model.RModel;
import de.walware.statet.r.core.pkgmanager.IRPkgChangeSet;
import de.walware.statet.r.core.pkgmanager.IRPkgManager;
import de.walware.statet.r.core.pkgmanager.IRPkgManager.Event;
import de.walware.statet.r.core.renv.IREnv;
import de.walware.statet.r.core.tool.IRConsoleService;
import de.walware.statet.r.internal.console.core.RObjectDB;
import de.walware.statet.r.internal.rdata.REnvironmentVar;
import de.walware.statet.r.internal.rdata.RReferenceVar;
import de.walware.statet.r.internal.rdata.VirtualMissingVar;
import de.walware.statet.r.nico.ICombinedRDataAdapter;
import de.walware.statet.r.nico.RWorkspaceConfig;


/**
 * R Tool Workspace
 */
public class RWorkspace extends ToolWorkspace {
	
	
	public static final int REFRESH_AUTO=                   IRConsoleService.AUTO_CHANGE;
	public static final int REFRESH_COMPLETE=               0x02;
	public static final int REFRESH_PACKAGES=               IRConsoleService.PACKAGE_CHANGE;
	
	
	public static final int RESOLVE_UPTODATE=               1 << 1;
	public static final int RESOLVE_FORCE=                  1 << 2;
	
	public static final int RESOLVE_INDICATE_NA=            1 << 5;
	
	
	public static interface ICombinedRList extends RList, ICombinedRElement {
		
		
		@Override
		ICombinedRElement get(int idx);
		@Override
		ICombinedRElement get(long idx);
		@Override
		ICombinedRElement get(String name);
		
	}
	
	public static interface ICombinedREnvironment extends REnvironment, ICombinedRList {
		
		
		RProcess getSource();
		
		int getStamp();
		
	}
	
	public static final ImList<IStringVariable> ADDITIONAL_R_VARIABLES= ImCollections.<IStringVariable>newList(
			NicoVariables.SESSION_STARTUP_DATE_VARIABLE,
			NicoVariables.SESSION_STARTUP_TIME_VARIABLE,
			NicoVariables.SESSION_CONNECTION_DATE_VARIABLE,
			NicoVariables.SESSION_CONNECTION_TIME_VARIABLE,
			NicoVariables.SESSION_STARTUP_WD_VARIABLE );
	
	
	protected static final class Changes {
		
		private final int changeFlags;
		private final Set<RElementName> changedEnvirs;
		
		public Changes(final int changeFlags, final Set<RElementName> changedEnvirs) {
			this.changeFlags= changeFlags;
			this.changedEnvirs= changedEnvirs;
		}
		
	}
	
	
	private static RReferenceVar verifyVar(final RElementName fullName,
			final ICombinedRDataAdapter r, final IProgressMonitor monitor) {
		try {
			return (RReferenceVar) r.evalCombinedStruct(fullName, 0, RService.DEPTH_REFERENCE,
					monitor );
		}
		catch (final Exception e) {
			return null;
		}
	}
	
	
	private boolean rObjectDBEnabled;
	private RObjectDB rObjectDB;
	private boolean autoRefreshDirty;
	
	private IRPkgManager pkgManager;
	private IRPkgManager.Listener pkgManagerListener;
	
	private final HashSet<RElementName> changedEnvirs= new HashSet<>();
	
	
	public RWorkspace(final AbstractRController controller, final String remoteHost,
			final RWorkspaceConfig config) {
		super(  controller,
				new Prompt("> ", IConsoleService.META_PROMPT_DEFAULT),  //$NON-NLS-1$
				"\n", (char) 0,
				remoteHost);
		if (config != null) {
			this.rObjectDBEnabled= config.getEnableObjectDB();
			setAutoRefresh(config.getEnableAutoRefresh());
		}
		
		final IREnv rEnv= (IREnv) getProcess().getAdapter(IREnv.class);
		if (rEnv != null) {
			this.pkgManager= RCore.getRPkgManager(rEnv);
			if (this.pkgManager != null) {
				this.pkgManagerListener= new IRPkgManager.Listener() {
					@Override
					public void handleChange(final Event event) {
						if ((event.pkgsChanged() & IRPkgManager.INSTALLED) != 0) {
							final IRPkgChangeSet changeSet= event.getInstalledPkgChangeSet();
							if (changeSet != null && !changeSet.getNames().isEmpty()) {
								final RObjectDB db= RWorkspace.this.rObjectDB;
								if (db != null) {
									db.handleRPkgChange(changeSet.getNames());
								}
							}
						}
					}
				};
				this.pkgManager.addListener(this.pkgManagerListener);
			}
		}
		
		controlBriefChanged(null, RWorkspace.REFRESH_COMPLETE);
	}
	
	
	@Override
	public RProcess getProcess() {
		return (RProcess) super.getProcess();
	}
	
	private AbstractRController getController() {
		return (AbstractRController) getProcess().getController();
	}
	
	@Override
	public IFileStore toFileStore(final IPath toolPath) throws CoreException {
		if (!toolPath.isAbsolute() && toolPath.getDevice() == null && "~".equals(toolPath.segment(0))) { //$NON-NLS-1$
			final AbstractRController controller= getController();
			if (controller != null) {
				final IPath homePath= createToolPath(controller.getProperty("R:file.~")); //$NON-NLS-1$
				if (homePath != null) {
					return super.toFileStore(homePath.append(toolPath.removeFirstSegments(1)));
				}
			}
			return null;
		}
		return super.toFileStore(toolPath);
	}
	
	@Override
	public IFileStore toFileStore(final String toolPath) throws CoreException {
		if (toolPath.startsWith("~/")) { //$NON-NLS-1$
			return toFileStore(createToolPath(toolPath));
		}
		return super.toFileStore(toolPath);
	}
	
	
	public boolean hasRObjectDB() {
		return (this.rObjectDBEnabled);
	}
	
	protected void enableRObjectDB(final boolean enable) {
		this.rObjectDBEnabled= enable;
		if (enable) {
			final AbstractRController controller= getController();
			if (controller != null) {
				controller.briefChanged(REFRESH_COMPLETE);
			}
		}
		else {
			this.rObjectDB= null;
		}
	}
	
	private int getStamp() {
		final AbstractRController controller= getController();
		return (controller != null) ? controller.getChangeStamp() : 0;
	}
	
	
	@Override
	protected void controlBriefChanged(final Object obj, final int flags) {
		if (obj instanceof Collection) {
			final Collection<?> collection = (Collection<?>) obj;
			for (final Object child : collection) {
				controlBriefChanged(child, flags);
			}
			return;
		}
		if (obj instanceof RElementName) {
			final RElementName name = (RElementName) obj;
			if (RElementName.isSearchScopeType(name.getType())) {
				this.changedEnvirs.add(name);
				return;
			}
		}
		super.controlBriefChanged(obj, flags);
	}
	
	protected Changes saveChanges() {
		return new Changes(getChangeFlags(),
				(!this.changedEnvirs.isEmpty()) ?
						(Set<RElementName>) this.changedEnvirs.clone() :
						Collections.EMPTY_SET );
	}
	
	protected void restoreChanges(final Changes changes) {
		super.controlBriefChanged(null, changes.changeFlags);
		this.changedEnvirs.addAll(changes.changedEnvirs);
	}
	
	private boolean hasBriefedChanges() {
		return (getChangeFlags() != 0 || !this.changedEnvirs.isEmpty());
	}
	
	@Override
	protected void clearBriefedChanges() {
		super.clearBriefedChanges();
		this.changedEnvirs.clear();
	}
	
	
	public List<? extends ICombinedREnvironment> getRSearchEnvironments() {
		final RObjectDB db= this.rObjectDB;
		return (db != null) ? db.getSearchEnvs(): null;
	}
	
	private boolean checkResolve(final ICombinedRElement resolved, final int resolve) {
		final int stamp;
		return ((resolve & RESOLVE_UPTODATE) == 0
						|| (resolved instanceof REnvironmentVar
								&& (stamp= getStamp()) != 0
								&& ((REnvironmentVar) resolved).getStamp() == stamp ));
	}
	
	private ICombinedRElement filterResolve(final ICombinedRElement resolved, final int resolve) {
		return ((resolve & RESOLVE_INDICATE_NA) == 0 && resolved instanceof VirtualMissingVar) ?
				null : resolved;
	}
	
	private ICombinedRElement doResolve(final RObjectDB db,
			final RReference reference, final int resolve) {
		ICombinedRElement resolved;
		
		resolved= db.getEnv(reference.getHandle());
		if (resolved != null) {
			if (checkResolve(resolved, resolve)) {
				return resolved;
			}
		}
		else if (reference instanceof ICombinedRElement) {
			resolved= db.getByName(((ICombinedRElement) reference).getElementName());
			if (resolved != null && checkResolve(resolved, resolve)) {
				return resolved;
			}
		}
		return null;
	}
	
	public ICombinedRElement resolve(final RReference reference, final int resolve) {
		final RObjectDB db= this.rObjectDB;
		if (db != null) {
			return filterResolve(doResolve(db, reference, resolve), resolve);
		}
		return null;
	}
	
	public ICombinedRElement resolve(final RElementName name, final int resolve) {
		final RObjectDB db= this.rObjectDB;
		if (db != null) {
			ICombinedRElement resolved;
			
			resolved= db.getByName(name);
			if (resolved != null && checkResolve(resolved, resolve)) {
				return filterResolve(resolved, resolve);
			}
		}
		return null;
	}
	
	public boolean isNamespaceLoaded(final String name) {
		final RObjectDB db= this.rObjectDB;
		return (db != null && db.isNamespaceLoaded(name));
	}
	
	public boolean isUptodate(ICombinedRElement element) {
		final AbstractRController controller= getController();
		if (controller != null) {
			if (element instanceof VirtualMissingVar) {
				final VirtualMissingVar var= (VirtualMissingVar) element;
				return (var.getSource() == controller.getTool()
						&& var.getStamp() == controller.getChangeStamp() );
			}
			while (element != null) {
				if (element.getRObjectType() == RObject.TYPE_ENV) {
					final REnvironmentVar var= (REnvironmentVar) element;
					return (var.getSource() == controller.getTool()
							&& var.getStamp() == controller.getChangeStamp() );
				}
				element= element.getModelParent();
			}
		}
		return false;
	}
	
	public boolean isNA(final ICombinedRElement element) {
		return (element instanceof VirtualMissingVar);
	}
	
	
	public RReference createReference(final long handle, final RElementName name,
			final byte type, final String className) {
		return new RReferenceVar(handle, type, className, null, name);
	}
	
	
	public ICombinedRElement resolve(final RReference reference, final int resolve,
			final int loadOptions, final IProgressMonitor monitor) throws CoreException {
		final AbstractRController controller= getController();
		if (controller == null || !(controller instanceof ICombinedRDataAdapter)) {
			return null;
		}
		
		RObjectDB db= this.rObjectDB;
		
		if (db != null && (resolve & RESOLVE_FORCE) == 0) {
			final ICombinedRElement resolved= doResolve(db, reference, resolve);
			if (resolved != null) {
				return filterResolve(resolved, resolve);
			}
		}
		
		RReferenceVar ref= null;
		if (reference instanceof RReferenceVar) {
			ref= (RReferenceVar) reference;
			if (ref.getHandle() == 0 || controller.getHotTasksState() > 1 || !isUptodate(ref)) {
				ref= verifyVar(RModel.getFQElementName(ref),
						(ICombinedRDataAdapter) controller, monitor );
			}
		}
		if (ref == null) {
			return null;
		}
		
		if (db == null) {
			db= new RObjectDB(this, controller.getChangeStamp() - 1000,
					controller, monitor );
			this.rObjectDB= db;
		}
		else if (db.getLazyEnvsStamp() != controller.getChangeStamp()) {
			db.updateLazyEnvs(controller, monitor);
		}
		
		return db.resolve(ref, loadOptions, false,
				(ICombinedRDataAdapter) controller, monitor );
	}
	
	public ICombinedRElement resolve(final RElementName name, final int resolve,
			final int loadOptions, final IProgressMonitor monitor) throws CoreException {
		final AbstractRController controller= getController();
		if (controller == null || !(controller instanceof ICombinedRDataAdapter)) {
			return null;
		}
		
		RObjectDB db= this.rObjectDB;
		
		if ((resolve & RESOLVE_FORCE) == 0 && db != null) {
			ICombinedRElement resolved;
			
			resolved= db.getByName(name);
			if (resolved != null && checkResolve(resolved, resolve)) {
				return filterResolve(resolved, resolve);
			}
		}
		
		final RReferenceVar ref;
		if (name.getNextSegment() == null
				&& name.getType() == RElementName.SCOPE_NS ) {
			ref= new RReferenceVar(0, RObject.TYPE_ENV, RObject.CLASSNAME_ENV, null, name);
		}
		else {
			ref= verifyVar(name, (ICombinedRDataAdapter) controller, monitor);
			if (ref != null && db != null) {
				ICombinedRElement resolved;
				
				resolved= db.getEnv(ref.getHandle());
				if (resolved != null && checkResolve(resolved, resolve)) {
					return filterResolve(resolved, resolve);
				}
			}
		}
		if (ref == null) {
			return null;
		}
		
		
		if (db == null) {
			db= new RObjectDB(this, controller.getChangeStamp() - 1000,
					controller, monitor );
			this.rObjectDB= db;
		}
		else if (db.getLazyEnvsStamp() != controller.getChangeStamp()) {
			db.updateLazyEnvs(controller, monitor);
		}
		
		return db.resolve(ref, loadOptions, false,
				(ICombinedRDataAdapter) controller, monitor );
	}
	
	
	public boolean isRObjectDBDirty() {
		return this.autoRefreshDirty;
	}
	
	@Override
	protected final void autoRefreshFromTool(final IConsoleService adapter,
			final IProgressMonitor monitor) throws CoreException {
		final AbstractRController controller= getController();
		if (hasBriefedChanges() || controller.isSuspended()) {
			refreshFromTool(controller, getChangeFlags(), monitor);
		}
	}
	
	@Override
	protected final void refreshFromTool(int options, final IConsoleService adapter,
			final IProgressMonitor monitor) throws CoreException {
		final AbstractRController controller= getController();
		if ((options & (REFRESH_AUTO | REFRESH_COMPLETE)) != 0) {
			options |= getChangeFlags();
		}
		refreshFromTool(controller, options, monitor);
	}
	
	protected void refreshFromTool(final AbstractRController controller, final int options,
			final IProgressMonitor monitor) throws CoreException {
		monitor.subTask("Update Workspace Data");
		if (controller.getTool().isProvidingFeatureSet(RConsoleTool.R_DATA_FEATURESET_ID)) {
			final IRDataAdapter r= (IRDataAdapter) controller;
			updateWorkspaceDir(r, monitor);
			updateOptions(r, monitor);
			if (this.rObjectDBEnabled) {
				final Set<RElementName> elements= this.changedEnvirs;
				if ( ((options & REFRESH_COMPLETE) != 0)
						|| ( ((((options & REFRESH_AUTO)) != 0) || !elements.isEmpty()
								|| controller.isSuspended() )
								&& isAutoRefreshEnabled() ) ) {
					updateREnvironments(r, elements, ((options & REFRESH_COMPLETE) != 0), monitor);
					clearBriefedChanges();
				}
			}
			else {
				clearBriefedChanges();
			}
		}
		else {
			clearBriefedChanges();
		}
		
		final boolean dirty= !isAutoRefreshEnabled() && hasBriefedChanges();
		if (dirty != this.autoRefreshDirty) {
			this.autoRefreshDirty= dirty;
			addPropertyChanged("RObjectDB.dirty", dirty);
		}
	}
	
	private void updateWorkspaceDir(final IRDataAdapter r,
			final IProgressMonitor monitor) throws CoreException {
		final RObject rWd= r.evalData("getwd()", monitor); //$NON-NLS-1$
		if (RDataUtil.isSingleString(rWd)) {
			final String wd= rWd.getData().getChar(0);
			if (!isRemote()) {
				controlSetWorkspaceDir(EFS.getLocalFileSystem().getStore(createToolPath(wd)));
			}
			else {
				controlSetRemoteWorkspaceDir(createToolPath(wd));
			}
		}
	}
	
	private void updateOptions(final IRDataAdapter r,
			final IProgressMonitor monitor) throws CoreException {
		final RList rOptions= (RList) r.evalData("options(\"prompt\", \"continue\")", monitor); //$NON-NLS-1$
		final RObject rPrompt= rOptions.get("prompt"); //$NON-NLS-1$
		if (RDataUtil.isSingleString(rPrompt)) {
			if (!rPrompt.getData().isNA(0)) {
				((AbstractRController) r).setDefaultPromptTextL(rPrompt.getData().getChar(0));
			}
		}
		final RObject rContinue= rOptions.get("continue"); //$NON-NLS-1$
		if (RDataUtil.isSingleString(rContinue)) {
			if (!rContinue.getData().isNA(0)) {
				((AbstractRController) r).setContinuePromptText(rContinue.getData().getChar(0));
			}
		}
	}
	
	private void updateREnvironments(final IRDataAdapter r, final Set<RElementName> envirs, boolean force,
			final IProgressMonitor monitor) throws CoreException {
		if (!(r instanceof ICombinedRDataAdapter)) {
			return;
		}
		
		final AbstractRController controller= getController();
		
//		final long time= System.nanoTime();
//		System.out.println(controller.getCounter() + " update");
		
		final RObjectDB previous= this.rObjectDB;
		force|= (previous == null || previous.getSearchEnvs() == null);
		if (!force && previous.getSearchEnvsStamp() == controller.getChangeStamp() && envirs.isEmpty()) {
			return;
		}
		final RObjectDB db= new RObjectDB(this, controller.getChangeStamp(),
				controller, monitor );
		final List<REnvironmentVar> updateEnvs= db.update(
				envirs, previous, force,
				(ICombinedRDataAdapter) r, monitor );
		
		if (monitor.isCanceled()) {
			return;
		}
		this.rObjectDB= db;
		addPropertyChanged("REnvironments", updateEnvs);
		
//		System.out.println("RSearch Update: " + (System.nanoTime() - time));
//		System.out.println("count: " + db.getSearchEnvsElementCount());
	}
	
	@Override
	protected void dispose() {
		if (this.pkgManagerListener != null) {
			this.pkgManager.removeListener(this.pkgManagerListener);
			this.pkgManagerListener= null;
		}
		
		super.dispose();
		this.rObjectDB= null;
	}
	
}
