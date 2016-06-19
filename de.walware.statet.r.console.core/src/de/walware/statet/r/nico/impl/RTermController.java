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

package de.walware.statet.r.nico.impl;

import static de.walware.statet.nico.core.runtime.IToolEventHandler.RUN_BLOCKING_EVENT_ID;
import static de.walware.statet.nico.core.runtime.IToolEventHandler.RUN_RUNNABLE_DATA_KEY;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamMonitor;

import de.walware.ecommons.ICommonStatusConstants;
import de.walware.ecommons.ts.ISystemRunnable;
import de.walware.ecommons.ts.IToolCommandHandler;
import de.walware.ecommons.ts.IToolRunnable;
import de.walware.ecommons.ts.IToolService;

import de.walware.statet.nico.core.runtime.IConsoleService;
import de.walware.statet.nico.core.runtime.IRequireSynch;
import de.walware.statet.nico.core.runtime.Prompt;
import de.walware.statet.nico.core.runtime.SubmitType;
import de.walware.statet.nico.core.runtime.ToolStatus;
import de.walware.statet.nico.core.runtime.ToolStreamMonitor;
import de.walware.statet.nico.core.runtime.ToolStreamProxy;

import de.walware.statet.r.console.core.AbstractRController;
import de.walware.statet.r.console.core.RProcess;
import de.walware.statet.r.console.core.RWorkspace;
import de.walware.statet.r.core.RUtil;
import de.walware.statet.r.internal.console.core.RConsoleCorePlugin;
import de.walware.statet.r.internal.nico.RNicoMessages;


/**
 * Controller for RTerm.
 */
public class RTermController extends AbstractRController implements IRequireSynch {
	
	
	private static final Pattern INT_OUTPUT_PATTERN = Pattern.compile("\\Q[1] \\E(\\d*)"); //$NON-NLS-1$
	private static final Pattern STRING_OUTPUT_PATTERN = Pattern.compile("\\Q[1] \"\\E((?:\\Q\\\"\\E|[^\"])*)\\\""); //$NON-NLS-1$
	
	
	private class ReadThread extends Thread {
		
		volatile int hasNoOutput;
		private final int SYNC_COUNT = 2;
		private final int SYNC_MS = 33;
		
		final Lock streamLock = new ReentrantLock();
		
		public ReadThread() {
			super("Rterm-Output Monitor"); //$NON-NLS-1$
		}
		
		@Override
		public void run() {
			final ToolStreamProxy streams = getStreams();
			boolean locked = false;
			try {
				boolean canRead = false;
				final char[] b = new char[1024];
				while (fProcess != null | (canRead = fProcessOutputReader.ready())) {
					fProcessOutputBuffer.available();
					if (canRead || hasNoOutput > SYNC_COUNT) {
						if (!canRead && locked) {
							streamLock.unlock();
							locked = false;
						}
						int n = fProcessOutputReader.read(b);
						if (n > 0) {
							hasNoOutput = 0;
							if (!locked) {
								streamLock.lock();
								locked = true;
							}
							final String s = new String(b, 0, n);
							streams.getOutputStreamMonitor().append(s, SubmitType.CONSOLE, 0);
							n = s.length();
							if (n >= 2 && s.charAt(--n) == ' ' && (s.charAt(--n) == '>' || s.charAt(n) == '+')) {
								hasNoOutput++;
								final Thread thread = getControllerThread();
								if (thread != null) {
									thread.interrupt();
								}
							}
							continue;
						}
						else if (n < 0) {
							onRTerminated();
							return;
						}
					}
					try {
						Thread.sleep(SYNC_MS);
						hasNoOutput++;
					}
					catch (final InterruptedException e) {
						// continue directly
					}
				}
			}
			catch (final IOException e) {
				onRTerminated();
				return;
			}
			finally {
				if (locked) {
					streamLock.unlock();
					locked = false;
				}
				try {
					fProcessOutputReader.close();
				} catch (final IOException e1) {
				}
			}
		}
		
		private void onRTerminated() {
			markAsTerminated();
			synchronized (getQueue()) {
				getQueue().notifyAll();
			}
		}
	}
	
	private class UpdateProcessIdTask extends ControllerSystemRunnable implements ISystemRunnable {
		
		
		public UpdateProcessIdTask() {
			super("r/rterm/fetch-process-id", "Fetch Process Id"); //$NON-NLS-1$
		}
		
		
		@Override
		public void run(final IToolService service,
				final IProgressMonitor monitor) throws CoreException {
			final StringBuilder output = readOutputLine("Sys.getpid()", monitor); //$NON-NLS-1$
			if (output != null) {
				final Matcher matcher = INT_OUTPUT_PATTERN.matcher(output);
				if (matcher.find()) {
					final String idString = matcher.group(1);
					if (idString != null) {
						try {
							fProcessId = Long.valueOf(idString);
						}
						catch (final NumberFormatException e) {
							fProcessId = null;
						}
					}
					else {
						fProcessId = null;
					}
				}
			}
		}
		
	}
	
	
	private final ProcessBuilder fConfig;
	private final Charset fCharset;
	private Process fProcess;
	private OutputStreamWriter fProcessInputWriter;
	private BufferedInputStream fProcessOutputBuffer;
	private InputStreamReader fProcessOutputReader;
	private ReadThread fProcessOutputThread;
	Long fProcessId;
	
	
	public RTermController(final RProcess process, final ProcessBuilder config, final Charset charset) {
		super(process, null);
		fConfig = config;
		fCharset = charset;
		
		setWorksapceData(new RWorkspace(this, null, null) {
			@Override
			protected void refreshFromTool(final AbstractRController controller, final int options, final IProgressMonitor monitor) throws CoreException {
				if ((options & RWorkspace.REFRESH_COMPLETE) != 0 || (options & RWorkspace.REFRESH_AUTO) == 0) {
					final StringBuilder output = readOutputLine("getwd()", monitor); //$NON-NLS-1$
					if (output != null) {
						final Matcher matcher = STRING_OUTPUT_PATTERN.matcher(output);
						if (matcher.find()) {
							final String wd = matcher.group(1);
							setWorkspaceDirL(EFS.getLocalFileSystem().getStore(new Path(wd)));
						}
					}
				}
				clearBriefedChanges();
			}
		});
		setWorkspaceDirL(EFS.getLocalFileSystem().fromLocalFile(config.directory()));
		initRunnableAdapterL();
	}
	
	@Override
	protected IToolRunnable createStartRunnable() {
		return new StartRunnable() {
			@Override
			public String getLabel() {
				return RNicoMessages.Rterm_StartTask_label;
			}
		};
	}
	
	@Override
	protected void startToolL(final IProgressMonitor monitor) throws CoreException {
		OutputStream processInput = null;
		InputStream processOutput;
		try {
			fConfig.redirectErrorStream(true);
			fProcess = fConfig.start();
			processOutput = fProcess.getInputStream();
			if (processOutput instanceof BufferedInputStream) {
				fProcessOutputBuffer = (BufferedInputStream) processOutput;
			}
			fProcessOutputReader = new InputStreamReader(processOutput, fCharset);
			fProcessOutputThread = new ReadThread();
			fProcessOutputThread.start();
			processInput = fProcess.getOutputStream();
			fProcessInputWriter = new OutputStreamWriter(processInput, fCharset);
			setCurrentPromptL(fDefaultPrompt);
			
			final List<IStatus> warnings= new ArrayList<>();
			
			initTracks(fConfig.directory().toString(), monitor, warnings);
			
			getQueue().add(new UpdateProcessIdTask());
			if (!this.startupsRunnables.isEmpty()) {
				getQueue().add(this.startupsRunnables);
				this.startupsRunnables.clear();
			}
			
			scheduleControllerRunnable(new ControllerSystemRunnable(
					"r/rj/start2", "Finish Initialization") { //$NON-NLS-1$
				
				@Override
				public void run(final IToolService service,
						final IProgressMonitor monitor) throws CoreException {
					for (final IStatus status : warnings) {
						handleStatus(status, monitor);
					}
				}
				
			});
		}
		catch (final IOException e) {
			if (processInput != null) {
				try {
					processInput.close();
				} catch (final IOException e1) {
				}
			}
			throw new CoreException(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID, ICommonStatusConstants.LAUNCHING,
					RNicoMessages.RTerm_error_Starting_message, e ));
		}
		
	}
	
	@Override
	protected void interruptTool() {
		runSendCtrlC();
	}
	
	@Override
	protected void postCancelTask(final int options, final IProgressMonitor monitor) throws CoreException {
		fCurrentInput = ""; //$NON-NLS-1$
		doSubmitL(monitor);
		fCurrentInput = ""; //$NON-NLS-1$
		doSubmitL(monitor);
	}
	
	@Override
	protected void killTool(final IProgressMonitor monitor) {
		final Process p = fProcess;
		if (p != null) {
			p.destroy();
			fProcess = null;
		}
	}
	
	@Override
	protected boolean isToolAlive() {
		final Process p = fProcess;
		if (p != null) {
			try {
				p.exitValue();
			}
			catch (final IllegalThreadStateException e) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	protected void clear() {
		fProcess = null;
		super.clear();
	}
	
	
	private boolean runSendCtrlC() {
		if (!Platform.getOS().startsWith("win") //$NON-NLS-1$
				|| getStatus() == ToolStatus.TERMINATED) {
			return false;
		}
		
		final IToolCommandHandler handler = getCommandHandler(RUN_BLOCKING_EVENT_ID);
		if (handler != null) {
			final RTermCancelRunnable cancelRunnable = new RTermCancelRunnable();
			final Map<String, Object> data = Collections.singletonMap(RUN_RUNNABLE_DATA_KEY, (Object) cancelRunnable);
			final IStatus status = executeHandler(RUN_BLOCKING_EVENT_ID, handler, data, null);
			return (status != null && status.isOK());
		}
		return false;
	}
	
	
//-- RunnabelAdapter
	
	@Override
	protected void doBeforeSubmitL() {
		final ToolStreamProxy streams = getStreams();
		final SubmitType submitType = getCurrentSubmitType();
		// adds control stream
		// without prompt
		try {
			fProcessOutputThread.streamLock.lock();
			streams.getInputStreamMonitor().append(fCurrentInput, submitType,
					(fCurrentPrompt.meta & IConsoleService.META_HISTORY_DONTADD) );
			streams.getInputStreamMonitor().append(getWorkspaceData().getLineSeparator(), submitType,
					IConsoleService.META_HISTORY_DONTADD);
		}
		finally {
			fProcessOutputThread.streamLock.unlock();
		}
	}
	
	@Override
	protected void doSubmitL(final IProgressMonitor monitor) {
		monitor.subTask(fDefaultPrompt.text + " " + fCurrentInput);  //$NON-NLS-1$
		
		try {
			fProcessInputWriter.write(fCurrentInput + fLineSeparator);
			fProcessInputWriter.flush();
		}
		catch (final IOException e) {
			RConsoleCorePlugin.log(new Status(IStatus.ERROR, RConsoleCorePlugin.PLUGIN_ID,
					-1, "Rterm IO error", e )); //$NON-NLS-1$
			if (!isToolAlive()) {
				markAsTerminated();
				setCurrentPromptL(Prompt.NONE);
				return;
			}
		}
		
		try {
			Thread.sleep(fProcessOutputThread.SYNC_MS*2);
		}
		catch (final InterruptedException e) {
			// continue directly
		}
		fProcessOutputThread.streamLock.lock();
		fProcessOutputThread.streamLock.unlock();
		
		setCurrentPromptL(fDefaultPrompt);
	}
	
	@Override
	public Pattern synch(final IProgressMonitor monitor) throws CoreException {
		final ToolStreamMonitor stream = getStreams().getOutputStreamMonitor();
		final String stamp = "Synch"+System.nanoTime(); //$NON-NLS-1$
		final AtomicBoolean patternFound = new AtomicBoolean(false);
		final IStreamListener listener = new IStreamListener() {
			
			private String lastLine = ""; //$NON-NLS-1$
			
			@Override
			public void streamAppended(final String text, final IStreamMonitor monitor) {
				if (text.contains(stamp)) {
					found();
					return;
				}
				final String[] lines = RUtil.LINE_SEPARATOR_PATTERN.split(text, -1);
				if ((lastLine + lines[0]).contains(stamp)) {
					found();
					return;
				}
				lastLine = lines[lines.length-1];
			}
			
			private void found() {
				stream.removeListener(this);
				patternFound.set(true);
			}
			
		};
		try {
			stream.addListener(listener);
			submitToConsole("cat(\""+stamp+"\\n\");", monitor); //$NON-NLS-1$ //$NON-NLS-2$
			while (!patternFound.get()) {
				if (monitor.isCanceled()) {
					throw cancelTask();
				}
				try {
					Thread.sleep(50);
				}
				catch (final InterruptedException e) {
					// continue directly
				}
			}
			return Pattern.compile("(?:"+Pattern.quote(getWorkspaceData().getDefaultPrompt().text) + ")?"+stamp); //$NON-NLS-1$ //$NON-NLS-2$
		}
		finally {
			stream.removeListener(listener);
		}
	}
	
	private StringBuilder readOutputLine(final String command, final IProgressMonitor monitor) throws CoreException {
		final ToolStreamMonitor stream = getStreams().getOutputStreamMonitor();
		final StringBuilder output = new StringBuilder();
		final AtomicBoolean patternFound = new AtomicBoolean(false);
		final IStreamListener listener = new IStreamListener() {
			
			@Override
			public void streamAppended(final String text, final IStreamMonitor monitor) {
				final Matcher matcher = RUtil.LINE_SEPARATOR_PATTERN.matcher(text);
				if (matcher.find()) {
					output.append(text.substring(0, matcher.start()));
					found();
				}
				else {
					output.append(text);
				}
			}
			
			private void found() {
				stream.removeListener(this);
				patternFound.set(true);
			}
			
		};
		synch(monitor);
		try {
			stream.addListener(listener);
			if (monitor.isCanceled()) {
				return null;
			}
			submitToConsole(command, monitor);
			while (!patternFound.get()) {
				if (monitor.isCanceled()) {
					throw cancelTask();
				}
				try {
					Thread.sleep(50);
				}
				catch (final InterruptedException e) {
					// continue directly
				}
			}
			return output;
		}
		finally {
			stream.removeListener(listener);
		}
	}
	
}
