/*=============================================================================#
 # Copyright (c) 2007-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.launching.ui;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import de.walware.ecommons.databinding.core.util.UpdateableErrorValidator;
import de.walware.ecommons.debug.ui.config.LaunchConfigTabWithDbc;
import de.walware.ecommons.io.FileValidator;
import de.walware.ecommons.ui.util.LayoutUtil;
import de.walware.ecommons.ui.util.MessageUtil;

import de.walware.statet.r.core.RCore;
import de.walware.statet.r.core.renv.IREnv;
import de.walware.statet.r.core.renv.IREnvConfiguration;
import de.walware.statet.r.core.renv.REnvUtil;
import de.walware.statet.r.internal.debug.ui.RLaunchingMessages;
import de.walware.statet.r.internal.ui.REnvSelectionComposite;
import de.walware.statet.r.launching.core.RLaunching;
import de.walware.statet.r.ui.RUI;


/**
 * Launch configuration tab allowing to configure the R environment
 */
public class REnvTab extends LaunchConfigTabWithDbc {
	
	
	public static String readWorkingDirectory(final ILaunchConfiguration configuration)
			throws CoreException {
		String wd= configuration.getAttribute(RLaunching.ATTR_WORKING_DIRECTORY, (String) null);
		if (wd == null) {
			wd= configuration.getAttribute(RLaunching.OLD_ATTR_WORKING_DIRECTORY, (String) null);
		}
		if (wd == null) {
			wd= ""; //$NON-NLS-1$
		}
		return wd;
	}
	
	public static void setWorkingDirectory(final ILaunchConfigurationWorkingCopy configuration,
			final String wd) {
		configuration.removeAttribute(RLaunching.OLD_ATTR_WORKING_DIRECTORY);
		if (wd != null && wd.length() > 0) {
			configuration.setAttribute(RLaunching.ATTR_WORKING_DIRECTORY, wd);
		}
		else {
			configuration.removeAttribute(RLaunching.ATTR_WORKING_DIRECTORY);
		}
	}
	
	/**
	 * Reads the setting from the configuration, resolves the path and validates the directory.
	 * @param configuration
	 * @return
	 * @throws CoreException
	 */
	public static IFileStore getWorkingDirectory(final ILaunchConfiguration configuration) throws CoreException {
		return getWorkingDirectoryValidator(configuration, true).getFileStore();
	}
	
	public static FileValidator getWorkingDirectoryValidator(final ILaunchConfiguration configuration, final boolean validate) throws CoreException {
		String path= readWorkingDirectory(configuration);
		if (path == null || path.trim().isEmpty()) {
			path= System.getProperty("user.dir"); //$NON-NLS-1$
		}
		final FileValidator validator= new FileValidator(true);
		validator.setOnDirectory(IStatus.OK);
		validator.setOnFile(IStatus.ERROR);
		validator.setResourceLabel(MessageUtil.removeMnemonics(RLaunchingMessages.REnv_Tab_WorkingDir_label));
		validator.setExplicit(path);
		if (validate && validator.validate(null).getSeverity() == IStatus.ERROR) {
			throw new CoreException(validator.getStatus());
		}
		return validator;
	}
	
	
/*-- --*/
	
	
	private final boolean fLocal;
	
	private final WritableValue rEnvSettingValue;
	private Binding rEnvBinding;
	
	private REnvSelectionComposite rEnvControl;
	
	
	public REnvTab(final boolean local) {
		super();
		
		this.fLocal= local;
		
		final Realm realm= getRealm();
		this.rEnvSettingValue= new WritableValue(realm, null, String.class);
	}
	
	
	@Override
	public String getName() {
		return RLaunchingMessages.REnv_Tab_title;
	}
	
	@Override
	public Image getImage() {
		return RUI.getImage(RUI.IMG_OBJ_R_RUNTIME_ENV);
	}
	
	@Override
	public void createControl(final Composite parent) {
		final Composite mainComposite= new Composite(parent, SWT.NONE);
		setControl(mainComposite);
		mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		mainComposite.setLayout(GridLayoutFactory.swtDefaults().create());
		
		Group group;
		group= new Group(mainComposite, SWT.NONE);
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		group.setText(RLaunchingMessages.REnv_Tab_REnvConfig_label+':');
		group.setLayout(LayoutUtil.createGroupGrid(1));
		if (this.fLocal) {
			this.rEnvControl= new REnvSelectionComposite(group) {
				@Override
				protected boolean isValid(final IREnvConfiguration rEnvConfig) {
					return super.isValid(rEnvConfig) && rEnvConfig.isLocal();
				}
			};
		}
		else {
			this.rEnvControl= new REnvSelectionComposite(group, true);
		}
		this.rEnvControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		
		Dialog.applyDialogFont(parent);
		initBindings();
	}
	
	@Override
	protected void addBindings(final DataBindingContext dbc, final Realm realm) {
		this.rEnvBinding= dbc.bindValue(this.rEnvControl.createObservable(realm), this.rEnvSettingValue,
				new UpdateValueStrategy().setAfterGetValidator(
						new UpdateableErrorValidator(this.rEnvControl.createValidator(dbc))),
				null );
	}
	
	@Override
	public void setDefaults(final ILaunchConfigurationWorkingCopy configuration) {
		if (this.fLocal) {
			configuration.setAttribute(RLaunching.ATTR_RENV_CODE, IREnv.DEFAULT_WORKBENCH_ENV_ID);
		}
	}
	
	@Override
	protected void doInitialize(final ILaunchConfiguration configuration) {
		try {
			String code= configuration.getAttribute(RLaunching.ATTR_RENV_CODE, (String) null);
			if (code == null) {
				code= configuration.getAttribute(RLaunching.OLD_ATTR_RENV_CODE, (String) null);
			}
			this.rEnvSettingValue.setValue(code);
		} catch (final CoreException e) {
			this.rEnvSettingValue.setValue(null);
			logReadingError(e);
		}
		
	}
	
	@Override
	protected void doSave(final ILaunchConfigurationWorkingCopy configuration) {
		final String code= (String) this.rEnvSettingValue.getValue();
		configuration.setAttribute(RLaunching.ATTR_RENV_CODE, code);
		configuration.removeAttribute(RLaunching.OLD_ATTR_RENV_CODE);
	}
	
	
	public IREnv getSelectedEnv() {
		if (this.rEnvBinding != null) {
			final IStatus validationStatus= (IStatus) this.rEnvBinding.getValidationStatus().getValue();
			if (validationStatus != null && validationStatus.getSeverity() < IStatus.WARNING) { // note: warning means error which can be saved
				return REnvUtil.decode((String) this.rEnvSettingValue.getValue(), RCore.getREnvManager());
			}
		}
		return null;
	}
	
}
