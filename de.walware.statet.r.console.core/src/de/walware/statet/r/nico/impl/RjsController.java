/*******************************************************************************
 * Copyright (c) 2008-2011 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.nico.impl;

import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_ADDRESS_DATA_KEY;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_CALLBACKS_DATA_KEY;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_MESSAGE_DATA_KEY;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.LOGIN_USERNAME_DATA_KEY;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

import javax.security.auth.callback.Callback;
import javax.security.auth.login.LoginException;

import com.ibm.icu.text.DateFormat;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.osgi.util.NLS;

import de.walware.ecommons.ICommonStatusConstants;
import de.walware.ecommons.io.FileUtil;
import de.walware.ecommons.net.RMIAddress;
import de.walware.ecommons.ts.IToolRunnable;
import de.walware.ecommons.ts.IToolService;

import de.walware.statet.nico.core.runtime.IRemoteEngineController;
import de.walware.statet.nico.core.runtime.IToolEventHandler;
import de.walware.statet.nico.core.runtime.ToolProcess;
import de.walware.statet.nico.core.util.TrackingConfiguration;

import de.walware.rj.RjException;
import de.walware.rj.data.RDataJConverter;
import de.walware.rj.data.RList;
import de.walware.rj.data.RObject;
import de.walware.rj.data.RObjectFactory;
import de.walware.rj.data.RReference;
import de.walware.rj.data.defaultImpl.RObjectFactoryImpl;
import de.walware.rj.server.ConsoleEngine;
import de.walware.rj.server.FxCallback;
import de.walware.rj.server.RjsComConfig;
import de.walware.rj.server.RjsStatus;
import de.walware.rj.server.Server;
import de.walware.rj.server.ServerInfo;
import de.walware.rj.server.ServerLogin;
import de.walware.rj.server.client.AbstractRJComClient;
import de.walware.rj.services.FunctionCall;
import de.walware.rj.services.RGraphicCreator;
import de.walware.rj.services.RPlatform;
import de.walware.rj.services.RServiceControlExtension;

import de.walware.statet.r.console.core.IRDataAdapter;
import de.walware.statet.r.console.core.RTool;
import de.walware.statet.r.console.core.RWorkspace;
import de.walware.statet.r.core.data.ICombinedRElement;
import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.internal.console.core.RConsoleCorePlugin;
import de.walware.statet.r.internal.nico.RNicoMessages;
import de.walware.statet.r.internal.rdata.CombinedElement;
import de.walware.statet.r.internal.rdata.CombinedFactory;
import de.walware.statet.r.nico.AbstractRController;
import de.walware.statet.r.nico.IRCombinedDataAdapter;
import de.walware.statet.r.nico.RWorkspaceConfig;


/**
 * Controller for RJ-Server
 */
public class RjsController extends AbstractRController
		implements IRemoteEngineController, IRDataAdapter, IRCombinedDataAdapter, RServiceControlExtension {
	
	
	static {
		RjsComConfig.registerRObjectFactory(CombinedFactory.FACTORY_ID, CombinedFactory.INSTANCE);
	}
	
	
	private class NicoComClient extends AbstractRJComClient {
		
		
		public NicoComClient() {
		}
		
		
		@Override
		protected void updateBusy(final boolean isBusy) {
//			try {
				fIsBusy = isBusy;
//			}
//			catch (Exception e) {
//			}
		}
		
		@Override
		protected void updatePrompt(final String text, final boolean addToHistory) {
			try {
				RjsController.this.setCurrentPromptL(text, addToHistory);
			}
			catch (final Exception e) {
			}
		}
		
		@Override
		protected void writeStdOutput(final String text) {
			try {
				fDefaultOutputStream.append(text, getCurrentSubmitType(), 0);
			}
			catch (final Exception e) {
			}
		}
		
		@Override
		protected void writeErrOutput(final String text) {
			try {
				fErrorOutputStream.append(text, getCurrentSubmitType(), 0);
			}
			catch (final Exception e) {
			}
		}
		
		@Override
		protected void showMessage(final String text) {
			try {
				fInfoStream.append(text, getCurrentSubmitType(), 0);
			}
			catch (final Exception e) {
			}
		}
		
		
		@Override
		protected RList handleUICallback(final String commandId, final RList args,
				final IProgressMonitor monitor) throws Exception {
			// TODO: allow handlers to use RJ data objects
			// TODO: allow handlers to return values
			// TODO: provide extension point for event handlers
			final IToolEventHandler handler = getEventHandler(commandId);
			if (handler != null) {
				final RDataJConverter converter = new RDataJConverter();
				converter.setKeepArray1(false);
				converter.setRObjectFactory(RObjectFactoryImpl.INSTANCE);
				
				final Map<String, Object> javaArgs = new HashMap<String, Object>();
				if (args != null) {
					for (int i = 0; i < args.getLength(); i++) {
						javaArgs.put(args.getName(i), converter.toJava(args.get(i)));
					}
				}
				
				final IStatus status = handler.handle(commandId, RjsController.this, javaArgs, monitor);
				switch (status.getSeverity()) {
				case IStatus.OK:
					break;
				default:
					throw new CoreException(status);
				}
				
				Map<String, Object> javaAnswer = null;
				if (commandId.equals("common/chooseFile")) { //$NON-NLS-1$
					javaAnswer = Collections.singletonMap(
							"filename", javaArgs.get("filename") ); //$NON-NLS-1$ //$NON-NLS-2$
				}
				
				if (javaAnswer != null) {
					final RList answer = (RList) converter.toRJ(javaAnswer);
					return answer;
				}
				else {
					return null;
				}
			}
			
			return super.handleUICallback(commandId, args, monitor);
		}
		
		@Override
		protected void log(final IStatus status) {
			RConsoleCorePlugin.log(status);
		}
		
		@Override
		protected void handleServerStatus(final RjsStatus serverStatus, final IProgressMonitor monitor) throws CoreException {
			String specialMessage = null;
			switch (serverStatus.getCode()) {
			case 0:
				return;
			case Server.S_DISCONNECTED:
				fConnectionState = Server.S_DISCONNECTED;
				//$FALL-THROUGH$
			case Server.S_LOST:
				if (fConnectionState == Server.S_DISCONNECTED) {
					specialMessage = RNicoMessages.R_Info_Disconnected_message;
					break;
				}
				else if (!fEmbedded) {
					fConnectionState = Server.S_LOST;
					specialMessage = RNicoMessages.R_Info_ConnectionLost_message;
					break;
				}
				//$FALL-THROUGH$
			case Server.S_STOPPED:
				fConnectionState = Server.S_STOPPED;
				specialMessage = RNicoMessages.R_Info_Stopped_message;
				break;
			default:
				throw new IllegalStateException();
			}
			
			if (!isClosed()) {
				markAsTerminated();
				setClosed(true);
				handleStatus(new Status(IStatus.INFO, RConsoleCorePlugin.PLUGIN_ID, addTimestampToMessage(specialMessage, System.currentTimeMillis())), monitor);
			}
			throw new CoreException(new Status(IStatus.CANCEL, RConsoleCorePlugin.PLUGIN_ID, specialMessage));
		}
		
		@Override
		protected void handleStatus(final Status status, final IProgressMonitor monitor) {
			RjsController.this.handleStatus(status, monitor);
		}
		
		@Override
		protected void processHotMode() {
			RjsController.this.runHotModeLoop();
		}
		
		@Override
		protected void scheduleConnectionCheck() {
			synchronized (fQueue) {
				if (getStatusL().isWaiting()) {
					scheduleControllerRunnable(new ControllerSystemRunnable(
							"r/check", "Connection Check") { //$NON-NLS-1$
						
						public void run(final IToolService s,
								final IProgressMonitor monitor) throws CoreException {
							fRjs.runMainLoopPing(monitor);
						}
						
					});
				}
			}
		}
		
	}
	
	
	private final RMIAddress fAddress;
	private final String[] fRArgs;
	
	private boolean fIsBusy = true;
	
	private final NicoComClient fRjs = new NicoComClient();
	private int fRjsId;
	
	private final boolean fEmbedded;
	private final boolean fStartup;
	private final Map<String, Object> fRjsProperties;
	
	private int fConnectionState;
	
	
	/**
	 * 
	 * @param process the R process the controller belongs to
	 * @param address the RMI address
	 * @param initData the initialization data
	 * @param sshPort optional sshPort
	 * @param embedded flag if running in embedded mode
	 * @param startup flag to start R (otherwise connect only)
	 * @param rArgs R arguments (required only if startup is <code>true</code>)
	 * @param initialWD
	 */
	public RjsController(final ToolProcess<RWorkspace> process,
			final RMIAddress address, final Map<String, Object> initData,
			final boolean embedded, final boolean startup, final String[] rArgs,
			final Map<String, Object> rjsProperties, final IFileStore initialWD,
			final RWorkspaceConfig workspaceConfig,
			final List<TrackingConfiguration> trackingConfigurations) {
		super(process, initData);
		if (address == null) {
			throw new IllegalArgumentException();
		}
		process.registerFeatureSet(RTool.R_DATA_FEATURESET_ID);
		process.registerFeatureSet("de.walware.rj.services.RService"); //$NON-NLS-1$
		if (!embedded) {
			process.registerFeatureSet(IRemoteEngineController.FEATURE_SET_ID);
		}
		fAddress = address;
		fEmbedded = embedded;
		fStartup = startup;
		fRArgs = rArgs;
		fRjsProperties = (rjsProperties != null) ? rjsProperties : new HashMap<String, Object>();
		fTrackingConfigurations = trackingConfigurations;
		
		fWorkspaceData = new RWorkspace(this, (embedded || address.isLocalHost()) ? null :
				address.getHostAddress().getHostAddress(), workspaceConfig );
		setWorkspaceDirL(initialWD);
		initRunnableAdapterL();
	}
	
	
	@Override
	public boolean supportsBusy() {
		return true;
	}
	
	@Override
	public boolean isBusy() {
		return fIsBusy;
	}
	
	public boolean isDisconnected() {
		return (fConnectionState == Server.S_DISCONNECTED || fConnectionState == Server.S_LOST);
	}
	
	/**
	 * This is an async operation
	 * cancel is not supported by this implementation
	 * 
	 * @param monitor a progress monitor
	 */
	public void disconnect(final IProgressMonitor monitor) throws CoreException {
		switch (getStatus()) {
		case STARTED_IDLING:
		case STARTED_PROCESSING:
		case STARTED_PAUSED:
			monitor.beginTask("Disconnecting from R remote engine...", 1);
			synchronized (fQueue) {
				beginInternalTask();
			}
			try {
				fRjs.getConsoleServer().disconnect();
				fConnectionState = Server.S_DISCONNECTED;
			}
			catch (final RemoteException e) {
				throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
						ICommonStatusConstants.LAUNCHING,
						"Disconnecting from R remote engine failed.", e));
			}
			finally {
				synchronized (fQueue) {
					scheduleControllerRunnable(new ControllerSystemRunnable(
							"common/disconnect/finish", "Disconnect") { //$NON-NLS-1$
						
						public void run(final IToolService s,
								final IProgressMonitor monitor) throws CoreException {
							if (!isTerminated()) {
								fRjs.runMainLoopPing(monitor);
								fRjs.handleServerStatus(new RjsStatus(RjsStatus.INFO, Server.S_DISCONNECTED), monitor);
							}
						}
						
					});
					endInternalTask();
				}
				monitor.done();
			}
		}
	}
	
	
	@Override
	protected IToolRunnable createStartRunnable() {
		return new StartRunnable() {
			@Override
			public String getLabel() {
				return "Connect to and load remote R engine.";
			}
		};
	}
	
	@Override
	protected void startToolL(final IProgressMonitor monitor) throws CoreException {
		final int[] clientVersion = AbstractRJComClient.version();
		clientVersion[2] = -1;
		final Server server;
		int[] version;
		try {
			server = (Server) Naming.lookup(fAddress.getAddress());
			version = server.getVersion();
		}
		catch (final MalformedURLException e) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					NLS.bind("The specified address '{0}' is invalid.", fAddress), e ));
		}
		catch (final NotBoundException e) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					"The specified R engine is not in the service registry (RMI).", e ));
		}
		catch (final RemoteException e) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					"The host/service registry (RMI) cannot be accessed.", e ));
		}
		catch (final ClassCastException e) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					NLS.bind("The specified R engine ({0}) is incompatibel to this client ({1}).", RjsUtil.getVersionString(null), RjsUtil.getVersionString(clientVersion)),
					e ));
		}
		if (version.length != 3 || version[0] != clientVersion[0] || version[1] != clientVersion[1]) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					NLS.bind("The specified R engine ({0}) is incompatibel to this client ({1}).", RjsUtil.getVersionString(version), RjsUtil.getVersionString(clientVersion)),
					null ));
		}
		
		fRjsId = RjsComConfig.registerClientComHandler(fRjs);
		fRjs.initClient(getTool(), this, fRjsProperties, fRjsId);
		try {
			final Map<String, Object> data = new HashMap<String, Object>();
			final IToolEventHandler loginHandler = getEventHandler(IToolEventHandler.LOGIN_REQUEST_EVENT_ID);
			String msg = null;
			boolean connected = false;
			while (!connected) {
				final Map<String, Object> initData = getInitData();
				final ServerLogin login = server.createLogin(Server.C_CONSOLE_CONNECT);
				try {
					final Callback[] callbacks = login.getCallbacks();
					if (callbacks != null) {
						final List<Callback> checked = new ArrayList<Callback>();
						FxCallback fx = null;
						for (final Callback callback : callbacks) {
							if (callback instanceof FxCallback) {
								fx = (FxCallback) callback;
							}
							else {
								checked.add(callback);
							}
						}
						
						if (initData != null) {
							data.putAll(initData);
						}
						data.put(LOGIN_ADDRESS_DATA_KEY, (fx != null) ? fAddress.getHost() : fAddress.getAddress());
						data.put(LOGIN_MESSAGE_DATA_KEY, msg);
						data.put(LOGIN_CALLBACKS_DATA_KEY, checked.toArray(new Callback[checked.size()]));
						
						if (loginHandler == null) {
							throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
									ICommonStatusConstants.LAUNCHING,
									"Login requested but not supported by this configuration.", null ));
						}
						if (!loginHandler.handle(IToolEventHandler.LOGIN_REQUEST_EVENT_ID, this, data, monitor).isOK()) {
							throw new CoreException(Status.CANCEL_STATUS);
						}
						
						if (fx != null) {
							RjsUtil.handleFxCallback(RjsUtil.getSession(data, new SubProgressMonitor(monitor, 1)), fx, new SubProgressMonitor(monitor, 1));
						}
					}
					
					msg = null;
					if (monitor.isCanceled()) {
						throw new CoreException(Status.CANCEL_STATUS);
					}
					
					final Map<String, Object> args = new HashMap<String, Object>();
					args.putAll(fRjsProperties);
					ConsoleEngine rjServer;
					if (fStartup) {
						args.put("args", fRArgs); //$NON-NLS-1$
						rjServer = (ConsoleEngine) server.execute(Server.C_CONSOLE_START, args, login.createAnswer());
					}
					else {
						rjServer = (ConsoleEngine) server.execute(Server.C_CONSOLE_CONNECT, args, login.createAnswer());
					}
					fRjs.setServer(rjServer, 0);
					connected = true;
					
					if (callbacks != null) {
						loginHandler.handle(IToolEventHandler.LOGIN_OK_EVENT_ID, this, data, monitor);
						if (initData != null) {
							initData.put(LOGIN_USERNAME_DATA_KEY, data.get(LOGIN_USERNAME_DATA_KEY));
						}
					}
				}
				catch (final LoginException e) {
					msg = e.getLocalizedMessage();
				}
				finally {
					if (login != null) {
						login.clearData();
					}
				}
			}
			
			final ServerInfo info = server.getInfo();
			if (fWorkspaceData.isRemote()) {
				try {
					final String wd = FileUtil.toString(fWorkspaceData.toFileStore(info.getDirectory()));
					if (wd != null) {
						setStartupWD(wd);
					}
				}
				catch (final CoreException e) {}
			}
			else {
				setStartupWD(info.getDirectory());
			}
			final long timestamp = info.getTimestamp();
			if (timestamp != 0) {
				setStartupTimestamp(timestamp);
			}
			
			final List<IStatus> warnings = new ArrayList<IStatus>();
			
			initTracks(info.getDirectory(), monitor, warnings);
			
			if (fStartup && !fStartupsRunnables.isEmpty()) {
				fQueue.add(fStartupsRunnables.toArray(new IToolRunnable[fStartupsRunnables.size()]));
				fStartupsRunnables.clear();
			}
			
			if (!fStartup) {
				handleStatus(new Status(IStatus.INFO, RConsoleCorePlugin.PLUGIN_ID,
						addTimestampToMessage(RNicoMessages.R_Info_Reconnected_message, fProcess.getConnectionTimestamp()) ),
						monitor);
			}
			fRjs.runMainLoop(null, null, monitor);
			fRjs.activateConsole();
			
			scheduleControllerRunnable(new ControllerSystemRunnable(
					"r/rj/start2", "Finish Initialization / Read Output") { //$NON-NLS-1$
				
				public void run(final IToolService s,
						final IProgressMonitor monitor) throws CoreException {
					if (!fRjs.isConsoleReady()) { // R is still working
						fRjs.runMainLoop(null, null, monitor);
					}
					for (final IStatus status : warnings) {
						handleStatus(status, monitor);
					}
				}
				
			});
		}
		catch (final RemoteException e) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					"The R engine could not be started.", e ));
		}
		catch (final RjException e) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					ICommonStatusConstants.LAUNCHING,
					"An error occured when creating login data.", e ));
		}
	}
	
//	public void controlNotification(final RjsComObject com) throws RemoteException {
//		if (com instanceof RjsStatus) {
//			final RjsStatusImpl2 serverStatus = (RjsStatusImpl2) com;
//			if (serverStatus.getCode() == Server.S_DISCONNECTED || serverStatus.getCode() == Server.S_STOPPED) {
//				scheduleControllerRunnable(new IToolRunnable() {
//					public String getTypeId() {
//						return null;
//					}
//					public String getLabel() {
//						return "Update State";
//					}
//					public SubmitType getSubmitType() {
//						return SubmitType.OTHER;
//					}
//					public void changed(final int event, final ToolProcess process) {
//					}
//					public void run(final IToolRunnableControllerAdapter tools, final IProgressMonitor monitor)
//							throws InterruptedException, CoreException {
//						if (!isTerminated()) {
//							rjsHandleStatus(serverStatus, monitor);
//						}
//					}
//					
//				});
//			}
//		}
//	}
	
	
	protected String addTimestampToMessage(final String message, final long timestamp) {
		final String datetime = DateFormat.getDateTimeInstance().format(System.currentTimeMillis());
		return datetime + " - " + message; //$NON-NLS-1$
	}
	
	@Override
	protected void requestHotMode(final boolean async) {
		fRjs.requestHotMode(async);
	}
	
	@Override
	protected boolean initilizeHotMode() {
		return fRjs.startHotMode();
	}
	
	
	@Override
	protected void interruptTool(final int hardness) throws UnsupportedOperationException {
		if (hardness < 10) {
			fRjs.runAsyncInterrupt();
		}
		if (hardness > 6) {
			super.interruptTool(hardness);
		}
	}
	
	@Override
	protected void postCancelTask(final int options, final IProgressMonitor monitor) throws CoreException {
		super.postCancelTask(options, monitor);
		fCurrentInput = ""; //$NON-NLS-1$
		doSubmitL(monitor);
		fCurrentInput = ""; //$NON-NLS-1$
		doSubmitL(monitor);
	}
	
	@Override
	protected boolean isToolAlive() {
		if (fConnectionState != 0 || !fRjs.runAsyncPing()) {
			return false;
		}
		if (Thread.currentThread() == getControllerThread() && !isInHotModeL()
				&& !fRjs.isConsoleReady()) {
			return false;
		}
		return true;
	}
	
	@Override
	protected void killTool(final IProgressMonitor monitor) {
		if (getControllerThread() == null) {
			markAsTerminated();
			return;
		}
		interruptTool(9);
		synchronized (fQueue) {
			fQueue.notifyAll();
		}
		
		fRjs.setClosed(true);
		final ToolProcess consoleProcess = getTool();
		// TODO: kill remote command?
		final IProcess[] processes = consoleProcess.getLaunch().getProcesses();
		for (int i = 0; i < processes.length; i++) {
			if (processes[i] != consoleProcess && !processes[i].isTerminated()) {
				try {
					processes[i].terminate();
				}
				catch (final Exception e) {
				}
			}
		}
		interruptTool(10);
		markAsTerminated();
	}
	
	@Override
	protected void clear() {
		fRjs.setClosed(true);
		
		super.clear();
		
		if (fEmbedded && !isDisconnected()) {
			try {
				Naming.unbind(fAddress.getAddress());
			}
			catch (final Throwable e) {
			}
		}
		fRjs.disposeAllGraphics();
		if (fRjsId > 0) {
			RjsComConfig.unregisterClientComHandler(fRjsId);
			fRjsId = 0;
		}
	}
	
	@Override
	protected int finishToolL() {
		int exitCode = 0;
		if (isDisconnected()) {
			exitCode = ToolProcess.EXITCODE_DISCONNECTED;
		}
		return exitCode;
	}
	
	
	@Override
	protected void doSubmitL(final IProgressMonitor monitor) throws CoreException {
		fRjs.answerConsole(fCurrentInput + fLineSeparator, monitor);
	}
	
	
	public RPlatform getPlatform() {
		return fRjs.getRPlatform();
	}
	
	public void evalVoid(final String command, final IProgressMonitor monitor) throws CoreException {
		fRjs.evalVoid(command, monitor);
	}
	
	public RObject evalData(final String command, final IProgressMonitor monitor) throws CoreException {
		return fRjs.evalData(command, null, 0, -1, monitor);
	}
	
	public RObject evalData(final String command, final String factoryId,
			final int options, final int depth, final IProgressMonitor monitor) throws CoreException {
		return fRjs.evalData(command, factoryId, options, depth, monitor);
	}
	
	public RObject evalData(final RReference reference, final IProgressMonitor monitor) throws CoreException {
		return fRjs.evalData(reference, null, 0, -1, monitor);
	}
	
	public RObject evalData(final RReference reference, final String factoryId,
			final int options, final int depth, final IProgressMonitor monitor) throws CoreException {
		return fRjs.evalData(reference, factoryId, options, depth, monitor);
	}
	
	public ICombinedRElement evalCombinedStruct(final String command,
			final int options, final int depth, final RElementName name, final IProgressMonitor monitor) throws CoreException {
		final RObject data = evalData(command, CombinedFactory.FACTORY_ID, (options | RObjectFactory.F_ONLY_STRUCT), depth, monitor);
		if (data instanceof CombinedElement) {
			final CombinedElement e = (CombinedElement) data;
			CombinedFactory.INSTANCE.setElementName(e, name);
			return e;
		}
		return null;
	}
	
	public ICombinedRElement evalCombinedStruct(final RElementName name,
			final int options, final int depth, final IProgressMonitor monitor) throws CoreException {
		final String command = RElementName.createDisplayName(name, RElementName.DISPLAY_NS_PREFIX | RElementName.DISPLAY_EXACT);
		if (command == null) {
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID, 0, "Illegal R element name.", null));
		}
		return evalCombinedStruct(command, options, depth, name, monitor);
	}
	
	public ICombinedRElement evalCombinedStruct(final RReference reference,
			final int options, final int depth, final RElementName name, final IProgressMonitor monitor) throws CoreException {
		final RObject data = evalData(reference, CombinedFactory.FACTORY_ID, (options | RObjectFactory.F_ONLY_STRUCT), depth, monitor);
		if (data instanceof CombinedElement) {
			final CombinedElement e = (CombinedElement) data;
			CombinedFactory.INSTANCE.setElementName(e, name);
			return e;
		}
		return null;
	}
	
	public void assignData(final String expression, final RObject data, final IProgressMonitor monitor) throws CoreException {
		fRjs.assignData(expression, data, monitor);
	}
	
	public void downloadFile(final OutputStream out, final String fileName, final int options, final IProgressMonitor monitor) throws CoreException {
		fRjs.downloadFile(out, fileName, options, monitor);
	}
	
	public byte[] downloadFile(final String fileName, final int options, final IProgressMonitor monitor) throws CoreException {
		return fRjs.downloadFile(fileName, options, monitor);
	}
	
	public void uploadFile(final InputStream in, final long length, final String fileName, final int options, final IProgressMonitor monitor) throws CoreException {
		fRjs.uploadFile(in, length, fileName, options, monitor);
	}
	
	public FunctionCall createFunctionCall(final String name) {
		throw new UnsupportedOperationException();
	}
	
	public RGraphicCreator createRGraphicCreator(final int options) {
		throw new UnsupportedOperationException();
	}
	
	
	public void addCancelHandler(final Callable<Boolean> handler) {
		fRjs.addCancelHandler(handler);
	}
	
	public void removeCancelHandler(final Callable<Boolean> handler) {
		fRjs.removeCancelHandler(handler);
	}
	
	public Lock getWaitLock() {
		return fRjs.getWaitLock();
	}
	
	public void waitingForUser(final IProgressMonitor monitor) {
		fRjs.waitingForUser();
	}
	
	public void resume() {
		fRjs.resume();
	}
	
}
