/*=============================================================================#
 # Copyright (c) 2009-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.nico.internal.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.osgi.util.NLS;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import de.walware.jcommons.collections.ImCollections;
import de.walware.jcommons.collections.ImList;

import de.walware.ecommons.net.resourcemapping.IResourceMapping;
import de.walware.ecommons.net.resourcemapping.IResourceMappingManager;
import de.walware.ecommons.net.resourcemapping.ResourceMappingOrder;

import de.walware.statet.nico.core.NicoCore;


public class ResourceMappingManager implements IResourceMappingManager {
	
	
	private static final String QUALIFIER= NicoCore.PLUGIN_ID + "/resoursemappings"; //$NON-NLS-1$
	
	private static final String LOCAL_KEY= "local.path"; //$NON-NLS-1$
	private static final String HOST_KEY= "host.name"; //$NON-NLS-1$
	private static final String REMOTE_KEY= "remote.path"; //$NON-NLS-1$
	
	
	public static final Comparator<IResourceMapping> DEFAULT_COMPARATOR= new Comparator<IResourceMapping>() {
		
		@Override
		public int compare(final IResourceMapping o1, final IResourceMapping o2) {
			final int diff= o1.getHost().compareTo(o2.getHost());
			if (diff != 0) {
				return diff;
			}
			return o1.getRemotePath().toPortableString().compareTo(o2.getRemotePath().toPortableString());
		}
		
	};
	
	private static final Comparator<IResourceMapping> LOCAL_COMPARATOR= new Comparator<IResourceMapping>() {
		
		@Override
		public int compare(final IResourceMapping o1, final IResourceMapping o2) {
			return - o1.getFileStore().toURI().compareTo(o2.getFileStore().toURI());
		}
		
	};
	
	private static final Comparator<IResourceMapping> REMOTE_COMPARATOR= new Comparator<IResourceMapping>() {
		
		@Override
		public int compare(final IResourceMapping o1, final IResourceMapping o2) {
			return - o1.getRemotePath().toPortableString().compareTo(o2.getRemotePath().toPortableString());
		}
		
	};
	
	
	private class UpdateJob extends Job {
		
		
		UpdateJob() {
			super("Update Resource Mappings");
			setPriority(BUILD);
		}
		
		@Override
		protected IStatus run(final IProgressMonitor monitor) {
			final List<ResourceMapping> list= ResourceMappingManager.this.list;
			if (list == null) {
				return Status.OK_STATUS;
			}
			final Map<String, List<IResourceMapping>[]> mappingsByHost= new HashMap<>();
			
			final SubMonitor progress= SubMonitor.convert(monitor, list.size() +1);
			final MultiStatus status= new MultiStatus(NicoCore.PLUGIN_ID, 0, "Update Resource Mapping", null);
			for (int i= 0; i < list.size(); i++) {
				progress.setWorkRemaining(list.size()-i +1);
				final ResourceMapping mapping= list.get(i);
				try {
					mapping.resolve();
					
					final InetAddress[] addresses= mapping.getHostAddresses();
					for (final InetAddress inetAddress : addresses) {
						final String host= inetAddress.getHostAddress();
						List<IResourceMapping>[] mappings= mappingsByHost.get(host);
						if (mappings == null) {
							mappings= new List[] { new ArrayList<>(), null };
							mappingsByHost.put(host, mappings);
						}
						mappings[0].add(mapping);
					}
				}
				catch (final UnknownHostException e) {
					status.add(new Status(IStatus.INFO, NicoCore.PLUGIN_ID, "Unknown host: " + e.getMessage(), e));
				}
			}
			for (final List<IResourceMapping>[] lists : mappingsByHost.values()) {
				final List<IResourceMapping> unsorted= lists[0];
				lists[ResourceMappingOrder.LOCAL.ordinal()]= ImCollections.toList(unsorted,
						LOCAL_COMPARATOR );
				lists[ResourceMappingOrder.REMOTE.ordinal()]= ImCollections.toList(unsorted,
						REMOTE_COMPARATOR );
			}
			
			synchronized(ResourceMappingManager.this) {
				if (ResourceMappingManager.this.list == list) {
					ResourceMappingManager.this.mappingsByHost= mappingsByHost;
				}
			}
			return status;
		}
		
	}
	
	
	private ImList<ResourceMapping> list;
	private Map<String, List<IResourceMapping>[]> mappingsByHost;
	
	private final UpdateJob updateJob;
	
	
	public ResourceMappingManager() {
		this.updateJob= new UpdateJob();
		load();
	}
	
	
	public void dispose() {
		synchronized (this) {
			this.list= null;
			this.updateJob.cancel();
		}
	}
	
	
	public List<ResourceMapping> getList() {
		return this.list;
	}
	
	public List<IResourceMapping> getMappingsFor(final String hostAddress, final ResourceMappingOrder order) {
		final Map<String, List<IResourceMapping>[]> byHost= this.mappingsByHost;
		if (byHost != null) {
			final List<IResourceMapping>[] lists= byHost.get(hostAddress);
			if (lists != null) {
				return lists[(order != null) ? order.ordinal() : 0];
			}
		}
		return null;
	}
	
	public void load() {
		try {
			final List<ResourceMapping> list= new ArrayList<>();
			
			final IEclipsePreferences rootNode= InstanceScope.INSTANCE.getNode(QUALIFIER);
			final String[] names= rootNode.childrenNames();
			for (final String name : names) {
				final ResourceMapping mapping= read(rootNode.node(name));
				if (mapping != null) {
					list.add(mapping);
				}
			}
			final ResourceMapping[] array= list.toArray(new ResourceMapping[list.size()]);
			Arrays.sort(array, DEFAULT_COMPARATOR);
			
			synchronized (this) {
				this.list= ImCollections.newList(array);
				this.updateJob.cancel();
				this.updateJob.schedule();
			}
		}
		catch (final BackingStoreException e) {
			NicoCorePlugin.logError(-1, "Failed to load resource mappings.", e);
		}
	}
	
	public void setMappings(final List<ResourceMapping> list) {
		final ImList<ResourceMapping> newMappings= ImCollections.toList(list, DEFAULT_COMPARATOR);
		try {
			final IEclipsePreferences rootNode= InstanceScope.INSTANCE.getNode(QUALIFIER);
			
			final List<String> names= new LinkedList<>(ImCollections.newList(rootNode.childrenNames()));
			final List<ResourceMapping> todo= new LinkedList<>(newMappings);
			
			int maxIdx= 0;
			for (final Iterator<ResourceMapping> iter= todo.iterator(); iter.hasNext(); ) {
				final ResourceMapping mapping= iter.next();
				final String id= mapping.getId();
				if (id != null) {
					try {
						final int idx= Integer.parseInt(id);
						if (idx > maxIdx) {
							maxIdx= idx;
						}
					}
					catch (final NumberFormatException e) {
					}
					iter.remove();
					names.remove(id);
					
					write(rootNode.node(id), mapping);
				}
			}
			for (final Iterator<ResourceMapping> iter= todo.iterator(); iter.hasNext(); ) {
				final ResourceMapping mapping= iter.next();
				final String id= Integer.toString(++maxIdx);
				mapping.setId(id);
				names.remove(id);
				write(rootNode.node(id), mapping);
			}
			for (final String name : names) {
				if (rootNode.nodeExists(name)) {
					final Preferences node= rootNode.node(name);
					node.removeNode();
				}
			}
			rootNode.flush();
			
			synchronized (this) {
				this.list= newMappings;
				this.updateJob.cancel();
				this.updateJob.schedule();
			}
		}
		catch (final BackingStoreException e) {
			NicoCorePlugin.logError(-1, "Failed to save resource mappings.", e);
		}
	}
	
	
	protected ResourceMapping read(final Preferences node) {
		final String id= node.name();
		final String local= node.get(LOCAL_KEY, null);
		final String host= node.get(HOST_KEY, null);
		final String remote= node.get(REMOTE_KEY, null);
		if (local != null && host != null && remote != null) {
			try {
				return new ResourceMapping(id, local, host, remote);
			}
			catch (final CoreException e) {
				NicoCorePlugin.logError(-1, NLS.bind("Failed to load resource mapping: ''{0}''.", id), e);
			}
		}
		return null;
	}
	
	protected void write(final Preferences node, final ResourceMapping mapping) {
		node.put(LOCAL_KEY, mapping.getLocalText());
		node.put(HOST_KEY, mapping.getHost());
		node.put(REMOTE_KEY, mapping.getRemotePath().toString());
	}
	
	
	@Override
	public List<IResourceMapping> getResourceMappingsFor(final String hostAddress, final ResourceMappingOrder order) {
		final List<IResourceMapping> mappings= getMappingsFor(hostAddress, order);
		if (mappings != null) {
			return mappings;
		}
		return Collections.emptyList();
	}
	
	@Override
	public IFileStore mapRemoteResourceToFileStore(final String hostAddress, IPath remotePath, final IPath relativeBasePath) {
		if (!remotePath.isAbsolute()) {
			if (relativeBasePath == null) {
				return null;
			}
			remotePath= relativeBasePath.append(remotePath);
		}
		final List<IResourceMapping> mappings= getResourceMappingsFor(hostAddress, ResourceMappingOrder.REMOTE);
		for (final IResourceMapping mapping : mappings) {
			final IPath remoteBase= mapping.getRemotePath();
			if (remoteBase.isPrefixOf(remotePath)) {
				final IPath subPath= remotePath.removeFirstSegments(remoteBase.segmentCount());
				final IFileStore localBaseStore= mapping.getFileStore();
				return localBaseStore.getFileStore(subPath);
			}
		}
		return null;
	}
	
	@Override
	public IPath mapFileStoreToRemoteResource(final String hostAddress, final IFileStore fileStore) {
		final List<IResourceMapping> mappings= getResourceMappingsFor(hostAddress, ResourceMappingOrder.LOCAL);
		for (final IResourceMapping mapping : mappings) {
			final IFileStore localBaseStore= mapping.getFileStore();
			if (localBaseStore.equals(fileStore)) {
				return mapping.getRemotePath();
			}
			if (localBaseStore.isParentOf(fileStore)) {
				final IPath localBasePath= new Path(localBaseStore.toURI().getPath());
				final IPath fileStorePath= new Path(fileStore.toURI().getPath());
				if (localBasePath.isPrefixOf(fileStorePath)) {
					final IPath subPath= fileStorePath.removeFirstSegments(localBasePath.segmentCount());
					final IPath remotePath= mapping.getRemotePath();
					return remotePath.append(subPath);
				}
			}
		}
		return null;
	}
	
}
