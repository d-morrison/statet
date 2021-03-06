/*=============================================================================#
 # Copyright (c) 2008-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.core.sourcemodel;

import de.walware.ecommons.collections.ImList;

import de.walware.statet.r.core.model.IRFrame;
import de.walware.statet.r.core.model.IRLangSourceElement;
import de.walware.statet.r.core.model.RElementAccess;
import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.core.rsource.ast.RAstNode;


public abstract class ElementAccess extends RElementAccess {
	
	
	public static final int A_READ =                        0x000000;
	public static final int A_CALL =                        0x000001;
	public static final int A_WRITE =                       0x000002;
	public static final int A_DELETE =                      0x000003;
	public static final int A_IMPORT =                      0x000004;
	
	public static final int A_SUB =                         0x000100;
	public static final int A_S4 =                          0x000200;
	
	public static final int A_FUNC =                        0x000010;
	public static final int A_ARG =                         0x000020;
	
	
	public final static class Default extends ElementAccess {
		
		public Default(final RAstNode fullNode) {
			super(fullNode, null);
		}
		
		public Default(final RAstNode fullNode, final RAstNode nameNode) {
			super(fullNode, nameNode);
		}
		
		@Override
		public int getType() {
			return RElementName.MAIN_DEFAULT;
		}
		
	}
	
	public final static class Slot extends ElementAccess {
		
		public Slot(final RAstNode fullNode) {
			super(fullNode, null);
		}
		
		public Slot(final RAstNode fullNode, final RAstNode nameNode) {
			super(fullNode, nameNode);
		}
		
		@Override
		public int getType() {
			return RElementName.SUB_NAMEDSLOT;
		}
		
	}
	
	public final static class Class extends ElementAccess {
		
		public Class(final RAstNode fullNode) {
			super(fullNode, null);
		}
		
		@Override
		public int getType() {
			return RElementName.MAIN_CLASS;
		}
		
	}
	
	public final static class Package extends ElementAccess {
		
		public Package(final RAstNode fullNode, final RAstNode nameNode) {
			super(fullNode, nameNode);
		}
		
		@Override
		public int getType() {
			return RElementName.MAIN_PACKAGE;
		}
		
	}
	
	
	int fFlags;
	RAstNode fFullNode;
	RAstNode fNameNode;
	BuildSourceFrame.ElementAccessList fShared;
	SubAbstractElementAccess fNextSegment;
	IRLangSourceElement fModelElement;
	
	
	private ElementAccess(final RAstNode fullNode, final RAstNode nameNode) {
		fFullNode = fullNode;
		fNameNode = nameNode;
	}
	
	
	@Override
	public final String getSegmentName() {
		return fShared.getName();
	}
	
	@Override
	public String getDisplayName() {
		return RElementName.createDisplayName(this, 0);
	}
	
	@Override
	public final IRFrame getFrame() {
		return fShared.frame;
	}
	
	@Override
	public RElementName getNamespace() {
		return (fShared.frame != null
				&& (fShared.frame.getFrameType() == IRFrame.PACKAGE || fShared.isCreated >= BuildSourceFrame.CREATED_RESOLVED) ) ?
						fShared.frame.getElementName() : null;
	}
	
	@Override
	public final ImList<? extends RElementAccess> getAllInUnit(final boolean includeSlaves) {
		return fShared.getAll(includeSlaves);
	}
	
	@Override
	public final boolean isWriteAccess() {
		return ((fFlags & A_WRITE) == A_WRITE); // A_WRITE | A_DELETE
	}
	
	@Override
	public boolean isCallAccess() {
		return ((fFlags & 0xf) == A_CALL);
	}
	
	public final boolean isDeletion() {
		return ((fFlags & 0xf) == A_DELETE);
	}
	
	public final boolean isImport() {
		return ((fFlags & 0xf) == A_IMPORT);
	}
	
	@Override
	public final boolean isFunctionAccess() {
		return ((fFlags & 0xf0) == A_FUNC);
	}
	
	@Override
	public final RAstNode getNode() {
		return fFullNode;
	}
	
	@Override
	public final RAstNode getNameNode() {
		return fNameNode;
	}
	
	@Override
	public final RElementAccess getNextSegment() {
		return fNextSegment;
	}
	
	final void appendSubElement(final SubAbstractElementAccess newSub) {
		if (fNextSegment == null) {
			fNextSegment = newSub;
			return;
		}
		SubAbstractElementAccess parent = fNextSegment;
		while (parent.nextSegment != null) {
			parent = parent.nextSegment;
		}
		parent.nextSegment = newSub;
	}
	
}
