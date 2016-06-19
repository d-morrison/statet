/*=============================================================================#
 # Copyright (c) 2008-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.console.ui.launching;

import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_SSH_HOST_DATA_KEY;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_SSH_PORT_DATA_KEY;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_USERNAME_DATA_KEY;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_USERNAME_FORCE_DATA_KEY;
import static de.walware.statet.r.internal.console.ui.launching.RConsoleRJLaunchDelegate.TIMEOUT;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.variables.IStringVariable;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;

import de.walware.ecommons.ICommonStatusConstants;
import de.walware.ecommons.debug.core.util.LaunchUtils;
import de.walware.ecommons.debug.core.util.OverlayLaunchConfiguration;
import de.walware.ecommons.debug.ui.util.UnterminatedLaunchAlerter;
import de.walware.ecommons.io.FileValidator;
import de.walware.ecommons.net.RMIAddress;
import de.walware.ecommons.net.RMIUtil;
import de.walware.ecommons.net.resourcemapping.IResourceMappingManager;
import de.walware.ecommons.net.resourcemapping.ResourceMappingUtils;
import de.walware.ecommons.preferences.PreferencesUtil;
import de.walware.ecommons.preferences.core.Preference;
import de.walware.ecommons.preferences.core.Preference.StringPref;
import de.walware.ecommons.ui.util.UIAccess;
import de.walware.ecommons.variables.core.StringVariable;

import com.jcraft.jsch.Session;

import de.walware.statet.nico.core.runtime.IRemoteEngineController;
import de.walware.statet.nico.core.runtime.IToolEventHandler;
import de.walware.statet.nico.core.runtime.Queue;
import de.walware.statet.nico.core.runtime.ToolController.IToolStatusListener;
import de.walware.statet.nico.core.runtime.ToolProcess;
import de.walware.statet.nico.core.runtime.ToolRunner;
import de.walware.statet.nico.core.runtime.ToolStatus;
import de.walware.statet.nico.core.util.HistoryTrackingConfiguration;
import de.walware.statet.nico.core.util.TrackingConfiguration;
import de.walware.statet.nico.ui.NicoUITools;
import de.walware.statet.nico.ui.console.NIConsoleColorAdapter;
import de.walware.statet.nico.ui.util.LoginHandler;
import de.walware.statet.nico.ui.util.WorkbenchStatusHandler;

import de.walware.rj.server.RjsComConfig;
import de.walware.rj.server.Server;

import de.walware.statet.r.console.core.RProcess;
import de.walware.statet.r.console.ui.RConsole;
import de.walware.statet.r.console.ui.launching.AbstractRConsoleLaunchDelegate;
import de.walware.statet.r.console.ui.launching.RConsoleLaunching;
import de.walware.statet.r.core.renv.IREnvConfiguration;
import de.walware.statet.r.internal.console.ui.Messages;
import de.walware.statet.r.internal.console.ui.RConsoleUIPlugin;
import de.walware.statet.r.launching.RRunDebugPreferenceConstants;
import de.walware.statet.r.launching.core.ILaunchDelegateAddon;
import de.walware.statet.r.launching.core.RLaunching;
import de.walware.statet.r.launching.ui.REnvTab;
import de.walware.statet.r.nico.impl.RjsController;
import de.walware.statet.r.nico.impl.RjsController.RjsConnection;
import de.walware.statet.r.nico.impl.RjsUtil;


/**
 * 
 * 
 * TODO: externalize error message strings
 */
public class RRemoteConsoleLaunchDelegate extends AbstractRConsoleLaunchDelegate {
	
	
	public static final int DEFAULT_SSH_PORT = 22;
	
	public static final IStringVariable WD_VARIABLE = new StringVariable(RLaunching.WORKING_DIRECTORY_VARNAME, "The configured R working directory (converted to remote path)");
	private static final Pattern WD_PATTERN = Pattern.compile("\\Q${"+RLaunching.WORKING_DIRECTORY_VARNAME+"}\\E"); //$NON-NLS-1$ //$NON-NLS-2$
	
	public static final String ADDRESS_VARIABLE_NAME = "address"; //$NON-NLS-1$;
	public static final IStringVariable ADDRESS_VARIABLE = new StringVariable(ADDRESS_VARIABLE_NAME, "The address of the remote R engine");
	private static final Pattern ADDRESS_PATTERN = Pattern.compile("\\Q${"+ADDRESS_VARIABLE_NAME+"}\\E"); //$NON-NLS-1$ //$NON-NLS-2$
	
	public static final String NAME_VARIABLE_NAME = "name"; //$NON-NLS-1$;
	public static final IStringVariable NAME_VARIABLE = new StringVariable(NAME_VARIABLE_NAME, "The name of the remote R engine (last segment of the address)");
	private static final Pattern NAME_PATTERN = Pattern.compile("\\Q${"+NAME_VARIABLE_NAME+"}\\E"); //$NON-NLS-1$ //$NON-NLS-2$
	
	public static final String DEFAULT_COMMAND;
	private static final Preference<String> DEFAULT_COMMAND_PATH = new StringPref(
			RRunDebugPreferenceConstants.CAT_RREMOTE_LAUNCHING_QUALIFIER,
			"rj.startupscript.path"); //$NON-NLS-1$
	
	static {
		String path = PreferencesUtil.getInstancePrefs().getPreferenceValue(DEFAULT_COMMAND_PATH);
		if (path == null || path.isEmpty()) {
			path = "~/.RJServer/startup.sh"; //$NON-NLS-1$
		}
		DEFAULT_COMMAND = path
				+ " \"${"+ADDRESS_VARIABLE_NAME+"}\"" //$NON-NLS-1$ //$NON-NLS-2$
				+ " -wd=\"${"+RLaunching.WORKING_DIRECTORY_VARNAME+"}\""; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	
	private static final int TODO_START_SERVER = 1;
	private static final int TODO_START_R = 2;
	private static final int TODO_CONNECT = 3;
	
	
	private ILaunchDelegateAddon fAddon;
	
	
	public RRemoteConsoleLaunchDelegate() {
	}
	
	public RRemoteConsoleLaunchDelegate(final ILaunchDelegateAddon addon) {
		fAddon = addon;
	}
	
	
	@Override
	public void launch(final ILaunchConfiguration configuration, final String mode, final ILaunch launch,
			final IProgressMonitor monitor) throws CoreException {
		try {
			if (monitor.isCanceled()) {
				return;
			}
			
			final String type = configuration.getAttribute(RConsoleLaunching.ATTR_TYPE, "").trim(); //$NON-NLS-1$
			if (type.equals(RConsoleLaunching.REMOTE_RJS) || type.equals(RConsoleLaunching.REMOTE_RJS_SSH)) { 
				launchRjsJriRemote(configuration, mode, launch, monitor);
				return;
			}
			if (type.equals(RConsoleLaunching.REMOTE_RJS_RECONNECT)) {
				if (configuration.hasAttribute(IRemoteEngineController.LAUNCH_RECONNECT_ATTRIBUTE)) {
					launchRjsJriRemote(configuration, mode, launch, monitor);
					return;
				}
				
				final AtomicReference<String> address = new AtomicReference<>();
				final String username = configuration.getAttribute(RConsoleLaunching.ATTR_LOGIN_NAME, (String) null);
				UIAccess.getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						final RRemoteConsoleSelectionDialog dialog = new RRemoteConsoleSelectionDialog(null, true);
						dialog.setUser(username);
						if (dialog.open() == Dialog.OK) {
							address.set((String) dialog.getFirstResult());
						}
					}
				});
				if (address.get() != null) {
					final Map<String, Object> map = new HashMap<>();
					map.put(IRemoteEngineController.LAUNCH_RECONNECT_ATTRIBUTE, Collections.EMPTY_MAP);
					map.put(RConsoleLaunching.ATTR_ADDRESS, address.get());
					launchRjsJriRemote(new OverlayLaunchConfiguration(configuration, map), mode, launch, monitor);
					return;
				}
				throw new CoreException(new Status(IStatus.CANCEL, RConsoleUIPlugin.PLUGIN_ID, ""));
			}
			throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHCONFIG_ERROR,
					NLS.bind("R Remote Console launch type ''{0}'' is not available.", type),
					null ));
		}
		finally {
			monitor.done();
		}
	}
	
	private void launchRjsJriRemote(final ILaunchConfiguration configuration, final String mode, final ILaunch launch,
			final IProgressMonitor monitor) throws CoreException {
		final SubMonitor progress= LaunchUtils.initProgressMonitor(configuration, monitor, 25);
		final long timestamp= System.currentTimeMillis();
		
		final IWorkbenchPage page = UIAccess.getActiveWorkbenchPage(false);
		
		final String type = configuration.getAttribute(RConsoleLaunching.ATTR_TYPE, (String) null).trim();
		final String username = configuration.getAttribute(RConsoleLaunching.ATTR_LOGIN_NAME, (String) null);
		
		progress.worked(1);
		if (progress.isCanceled()) {
			return;
		}
		
		IREnvConfiguration rEnv = null;
		try {
			rEnv = RLaunching.getREnvConfig(configuration, false);
		}
		catch (final Exception e) {}
		
		// load tracking configurations
		final List<TrackingConfiguration> trackingConfigs;
		{	final List<String> trackingIds = configuration.getAttribute(RConsoleOptionsTab.TRACKING_ENABLED_IDS, Collections.EMPTY_LIST);
			trackingConfigs = new ArrayList<>(trackingIds.size());
			for (final String id : trackingIds) {
				final TrackingConfiguration trackingConfig;
				if (id.equals(HistoryTrackingConfiguration.HISTORY_TRACKING_ID)) {
					trackingConfig = new HistoryTrackingConfiguration(id);
				}
				else {
					trackingConfig = new TrackingConfiguration(id);
				}
				RConsoleOptionsTab.TRACKING_UTIL.load(trackingConfig, configuration);
				trackingConfigs.add(trackingConfig);
			}
		}
		
		progress.worked(1);
		if (progress.isCanceled()) {
			return;
		}
		
		final Map reconnect = configuration.getAttribute(IRemoteEngineController.LAUNCH_RECONNECT_ATTRIBUTE, (Map) null);
		final ToolProcess prevProcess;
		boolean prevProcessDisposeFinally = true;
		if (reconnect != null) {
			prevProcess = (ToolProcess) reconnect.get("process"); //$NON-NLS-1$
		}
		else {
			prevProcess = null;
		}
		
		progress.worked(1);
		if (progress.isCanceled()) {
			return;
		}
		
		try {
			// r env
//			REnvConfiguration renv = REnvTab.getREnv(configuration);
//			renv.validate();
//			
//			progress.worked(1);
//			if (monitor.isCanceled()) {
//				return;
//			}
			
			// arguments
			String address;
			if (reconnect != null && reconnect.containsKey("address")) { //$NON-NLS-1$
				address = (String) reconnect.get("address"); //$NON-NLS-1$
			}
			else {
				address = configuration.getAttribute(RConsoleLaunching.ATTR_ADDRESS, (String) null);
			}
			if (address == null || address.isEmpty()) {
				throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID,
						ICommonStatusConstants.LAUNCHCONFIG_ERROR,
						Messages.LaunchDelegate_error_MissingAddress_message, null ));
			}
			if (!(address.startsWith("//") || address.startsWith("rmi:"))) { //$NON-NLS-1$ //$NON-NLS-2$
				address = "//" + address; //$NON-NLS-1$
			}
			// Working directory
			final FileValidator validator = REnvTab.getWorkingDirectoryValidator(configuration, false);
			final IFileStore workingDirectory = (validator.validate(null).getSeverity() != IStatus.ERROR) ?
					validator.getFileStore() : null;
			{	// Replace variable in address
				final Matcher matcher = WD_PATTERN.matcher(address);
				if (matcher.find()) {
					if (workingDirectory == null) {
						throw new CoreException(validator.getStatus());
					}
					address = matcher.replaceAll(workingDirectory.getName());
				}
			}
			
			final boolean sshTunnel = configuration.getAttribute(RConsoleLaunching.ATTR_SSH_TUNNEL_ENABLED, false);
			final Map<String, Object> loginData = new HashMap<>();
			
			RMIAddress rmiAddress = null;
			RMIClientSocketFactory socketFactory = null;
			Session sshSession = null;
			int todo = TODO_START_SERVER;
			Exception todoException = null;
			Registry registry = null;
			boolean registryOK = false;
			try {
				progress.subTask(Messages.LaunchDelegate_CheckingRegistry_subtask);
				rmiAddress = new RMIAddress(address);
				
				// init login data
				loginData.put(LOGIN_USERNAME_DATA_KEY, username);
				if (type.equals(RConsoleLaunching.REMOTE_RJS_SSH)) {
					loginData.put(LOGIN_USERNAME_FORCE_DATA_KEY, true);
				}
				final int sshPort = configuration.getAttribute(RConsoleLaunching.ATTR_SSH_PORT, DEFAULT_SSH_PORT);
				loginData.put(LOGIN_SSH_HOST_DATA_KEY, rmiAddress.getHostAddress().getHostAddress());
				loginData.put(LOGIN_SSH_PORT_DATA_KEY, Integer.valueOf(sshPort));
				
				final Remote remote;
				if (sshTunnel) {
					if (sshSession == null) {
						sshSession = RjsUtil.getSession(loginData, progress.newChild(5));
					}
					
					socketFactory = RjsUtil.createRMIOverSshClientSocketFactory(sshSession);
					RjsComConfig.setRMIClientSocketFactory(socketFactory);
					registry = LocateRegistry.getRegistry("127.0.0.1", rmiAddress.getPortNum(), //$NON-NLS-1$
							socketFactory );
					remote = registry.lookup(rmiAddress.getName());
					registryOK = true;
				}
				else {
					RMIUtil.checkRegistryAccess(rmiAddress);
					RjsComConfig.setRMIClientSocketFactory(null);
					registry = LocateRegistry.getRegistry(rmiAddress.getHost(), rmiAddress.getPortNum(),
							socketFactory );
					remote = registry.lookup(rmiAddress.getName());
					registryOK = true;
				}
				if (remote instanceof Server) {
					final Server server = (Server) remote;
					final int state = server.getState();
					if (state <= Server.S_NOT_STARTED) {
						todo = TODO_START_R;
						if (reconnect != null) {
							throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID, 0,
									NLS.bind("Cannot reconnect, the R engine at ''{0}'' is not yet started.", address),
									null ));
						}
					}
					else if (state == Server.S_CONNECTED) {
						todo = TODO_CONNECT;
						if (reconnect != null) {
						}
						else {
							final Shell shell = page.getWorkbenchWindow().getShell();
							final Display display = UIAccess.getDisplay(shell);
							final String msg = NLS.bind("It seems, a client is already connected to the remote R engine (''{0}'').\n Do you want to disconnect this client and connect to the engine?", address);
							final AtomicBoolean force = new AtomicBoolean(false);
							display.syncExec(new Runnable() {
								@Override
								public void run() {
									force.set(MessageDialog.openQuestion(shell, "Connect", msg));
								}
							});
							if (!force.get()) {
								monitor.setCanceled(true);
								throw new CoreException(Status.CANCEL_STATUS);
							}
						}
					}
					else if (state <= Server.S_LOST) {
						todo = TODO_CONNECT;
					}
					else if (state == Server.S_STOPPED) {
						if (reconnect != null) {
							throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID, 0,
									NLS.bind("Cannot reconnect, the R engine at ''{0}'' is terminated.", address),
									null ));
						}
						todo = TODO_START_SERVER;
					}
					else {
						throw new IllegalStateException("Server state: " + state);
					}
				}
			}
			catch (final UnknownHostException e) {
				throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID,
						ICommonStatusConstants.LAUNCHCONFIG_ERROR,
						Messages.LaunchDelegate_error_InvalidAddress_message, e ));
			}
			catch (final MalformedURLException e) {
				throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID,
						ICommonStatusConstants.LAUNCHCONFIG_ERROR,
						Messages.LaunchDelegate_error_InvalidAddress_message, e ));
			}
			catch (final RemoteException e) {
				if (!registryOK) {
					registry = null;
				}
				todoException = e;
				todo = TODO_START_SERVER;
			}
			catch (final NotBoundException e) {
				todoException = e;
				todo = TODO_START_SERVER;
			}
			
			progress.worked(5);
			if (progress.isCanceled()) {
				return;
			}
			
			final String[] args = LaunchUtils.getProcessArguments(configuration, RConsoleLaunching.ATTR_OPTIONS);
			
			if (reconnect != null) {
				final Map<String, String> reconnectData = (Map<String, String>) reconnect.get("initData"); //$NON-NLS-1$
				if (reconnectData != null) {
					loginData.putAll(reconnectData);
				}
			}
			
			String command = null;
			if (todo == TODO_START_SERVER) {
				progress.subTask(Messages.LaunchDelegate_StartREngine_subtask);
				progress.setWorkRemaining(21);
				if (type.equals(RConsoleLaunching.REMOTE_RJS_SSH)) {
					command = configuration.getAttribute(RConsoleLaunching.ATTR_COMMAND, ""); //$NON-NLS-1$
					if (command.isEmpty()) {
						throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID,
								ICommonStatusConstants.LAUNCHCONFIG_ERROR,
								"Command to startup R over SSH is missing.", null )); //$NON-NLS-1$
					}
					
					final Matcher addressMatcher = ADDRESS_PATTERN.matcher(command);
					if (addressMatcher.find()) {
						command = addressMatcher.replaceAll(rmiAddress.getAddress());
					}
					
					final Matcher nameMatcher = NAME_PATTERN.matcher(command);
					if (nameMatcher.find()) {
						command = nameMatcher.replaceAll(rmiAddress.getName());
					}
					
					final Matcher wdMatcher = WD_PATTERN.matcher(command);
					if (wdMatcher.find()) {
						if (workingDirectory == null) {
							throw new CoreException(validator.getStatus());
						}
						final IResourceMappingManager rmManager = ResourceMappingUtils.getManager();
						final IPath path = (rmManager != null) ? rmManager
								.mapFileStoreToRemoteResource(rmiAddress.getHostAddress().getHostAddress(), workingDirectory) :
								null;
						if (path == null) {
							throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID,
									NLS.bind("Cannot resolve working directory ''{0}'' to remote path.", workingDirectory.toString()) ));
						}
						command = wdMatcher.replaceAll(path.toString());
					}
					
					final Hashtable<String, String> envp = new Hashtable<>();
					envp.put("LC_ALL", "C"); //$NON-NLS-1$ //$NON-NLS-2$
					envp.put("LANG", "C"); //$NON-NLS-1$ //$NON-NLS-2$
					envp.put("LC_NUMERIC", "C"); //$NON-NLS-1$ //$NON-NLS-2$
					RjsUtil.startRemoteServerOverSsh(RjsUtil.getSession(loginData, progress.newChild(5)), command, envp, progress.newChild(5));
					
					progress.subTask(Messages.LaunchDelegate_WaitForR_subtask);
					final long t = System.nanoTime();
					WAIT: for (int i = 0; true; i++) {
						if (progress.isCanceled()) {
							throw new CoreException(Status.CANCEL_STATUS);
						}
						try {
							if (registry == null) {
								if (sshTunnel) {
									if (sshSession == null) {
										sshSession = RjsUtil.getSession(loginData, progress.newChild(5));
									}
									if (socketFactory == null) {
										socketFactory = RjsUtil.createRMIOverSshClientSocketFactory(sshSession);
									}
									registry = LocateRegistry.getRegistry("127.0.0.1", rmiAddress.getPortNum(), //$NON-NLS-1$
											socketFactory );
								}
								else {
									RMIUtil.checkRegistryAccess(rmiAddress);
									registryOK = true;
									registry = LocateRegistry.getRegistry(rmiAddress.getHost(), rmiAddress.getPortNum());
								}
							}
							final String[] list = registry.list();
							registryOK = true;
							for (final String entry : list) {
								if (entry.equals(rmiAddress.getName())) {
									break WAIT;
								}
							}
							if (i > 1 && System.nanoTime() - t > TIMEOUT) {
								break WAIT;
							}
						}
						catch (final RemoteException e) {
							if (i > 0 && System.nanoTime() - t > TIMEOUT / 3) {
								if (registry == null) {
									RjsController.lookup(null, e, rmiAddress);
								}
								break WAIT;
							}
							if (!registryOK) {
								registry = null;
							}
						}
						try {
							Thread.sleep(333);
						}
						catch (final InterruptedException e) {
							// continue, monitor is checked
						}
					}
					progress.worked(5);
					
					todo = TODO_START_R;
				}
				else {
					if (reconnect != null) {
						throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID, 0,
								NLS.bind("Cannot reconnect to server, no R engine is available at ''{0}''.", address),
								todoException ));
					}
					else {
						throw new CoreException(new Status(IStatus.ERROR, RConsoleUIPlugin.PLUGIN_ID, 0,
								NLS.bind("Cannot start or reconnect to server, to R engine at ''{0}''. You have to restart the server (manually or using SSH automation).", address),
								todoException ));
					}
				}
			}
			
			final RjsConnection connection = RjsController.lookup(registry, null, rmiAddress);
			
			// create process
			UnterminatedLaunchAlerter.registerLaunchType(RConsoleLaunching.R_REMOTE_CONSOLE_CONFIGURATION_TYPE_ID);
			final boolean startup = (todo == TODO_START_R);
			
			final RProcess process = new RProcess(launch, rEnv,
					LaunchUtils.createLaunchPrefix(configuration),
					((rEnv != null) ? rEnv.getName() : "-") + " / RJ " + rmiAddress.toString() + ' ' + LaunchUtils.createProcessTimestamp(timestamp), //$NON-NLS-1$ //$NON-NLS-2$
					rmiAddress.toString(),
					(workingDirectory != null) ? workingDirectory.toString() : null,
					timestamp );
			process.setAttribute(IProcess.ATTR_CMDLINE, rmiAddress.toString() + '\n'
					+ ((startup) ? Arrays.toString(args) : "rjs-reconnect")); //$NON-NLS-1$
			
			final HashMap<String, Object> rjsProperties = new HashMap<>();
			rjsProperties.put(RjsComConfig.RJ_DATA_STRUCTS_LISTS_MAX_LENGTH_PROPERTY_ID,
					configuration.getAttribute(RConsoleLaunching.ATTR_OBJECTDB_LISTS_MAX_LENGTH, 10000));
			rjsProperties.put(RjsComConfig.RJ_DATA_STRUCTS_ENVS_MAX_LENGTH_PROPERTY_ID,
					configuration.getAttribute(RConsoleLaunching.ATTR_OBJECTDB_ENVS_MAX_LENGTH, 10000));
			rjsProperties.put("rj.session.startup.time", timestamp); //$NON-NLS-1$
			final RjsController controller = new RjsController(process, rmiAddress, connection, loginData,
					false, startup, args, rjsProperties, null,
					RConsoleRJLaunchDelegate.createWorkspaceConfig(configuration), trackingConfigs);
			
			// move all tasks, if started
			if (reconnect != null && prevProcess != null) {
				controller.addToolStatusListener(new IToolStatusListener() {
					@Override
					public void controllerStatusChanged(final ToolStatus oldStatus, final ToolStatus newStatus, final List<DebugEvent> eventCollection) {
						if (newStatus != ToolStatus.TERMINATED) {
							final Queue prevQueue = prevProcess.getQueue();
							prevQueue.moveAll(process.getQueue());
						}
						prevProcess.restartCompleted(reconnect);
						controller.removeToolStatusListener(this);
					}
				});
			}
			process.init(controller);
			
			RConsoleLaunching.registerDefaultHandlerTo(controller);
			controller.addCommandHandler(IToolEventHandler.LOGIN_REQUEST_EVENT_ID, new LoginHandler());
			
			progress.worked(5);
			
			RConsoleRJLaunchDelegate.initConsoleOptions(controller, rEnv, configuration, startup);
			
			if (fAddon != null) {
				fAddon.init(configuration, mode, controller, monitor);
			}
			
			final RConsole console = new RConsole(process, new NIConsoleColorAdapter());
			NicoUITools.startConsoleLazy(console, page,
					configuration.getAttribute(RConsoleLaunching.ATTR_PIN_CONSOLE, false));
			// start
			new ToolRunner().runInBackgroundThread(process, new WorkbenchStatusHandler());
			prevProcessDisposeFinally = false;
		}
		finally {
			RjsComConfig.clearRMIClientSocketFactory();
			if (prevProcessDisposeFinally && reconnect != null && prevProcess != null) {
				prevProcess.restartCompleted(reconnect);
			}
		}
	}
	
}
