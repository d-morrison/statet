/*=============================================================================#
 # Copyright (c) 2010-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.ui.dataeditor;

import java.util.List;

import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;

import de.walware.statet.r.ui.dataeditor.RDataTableColumn;


public class RDataFormatterConverter implements IDisplayConverter {
	
	
	public static class RowHeader extends RDataFormatterConverter {
		
		public RowHeader(final AbstractRDataProvider<?> dataProvider) {
			super(dataProvider);
		}
		
		@Override
		protected RDataFormatter getFormatter(final RDataTableContentDescription description,
				final ILayerCell cell) {
			final List<RDataTableColumn> columns = description.getRowHeaderColumns();
			final long index = cell.getColumnIndex();
			if (columns != null && index >= 0 && index < columns.size()) {
				return columns.get((int) index).getDefaultFormat();
			}
			return null;
		}
		
	}
	
	
	private final AbstractRDataProvider<?> fDataProvider;
	
	
	RDataFormatter fFallbackFormatter;
	
	
	public RDataFormatterConverter(final AbstractRDataProvider<?> dataProvider) {
		this.fDataProvider = dataProvider;
	}
	
	
	private RDataFormatter getFormatter(final ILayerCell cell) {
		final RDataTableContentDescription description = this.fDataProvider.getDescription();
		if (description != null) {
			final RDataFormatter formatter = getFormatter(description, cell);
			if (formatter != null) {
				return formatter;
			}
		}
		if (this.fFallbackFormatter == null) {
			this.fFallbackFormatter = new RDataFormatter();
		}
		return this.fFallbackFormatter;
	}
	
	protected RDataFormatter getFormatter(final RDataTableContentDescription description,
			final ILayerCell cell) {
		final List<RDataTableColumn> columns = description.getDataColumns();
		final long index = cell.getColumnIndex();
		if (index >= 0 && index < columns.size()) {
			return columns.get((int) index).getDefaultFormat();
		}
		return description.getDefaultDataFormat();
	}
	
	protected long getColumnIndex(final ILayerCell cell) {
		return cell.getColumnIndex();
	}
	
	
	@Override
	public Object canonicalToDisplayValue(final ILayerCell cell, final IConfigRegistry configRegistry,
			final Object canonicalValue) {
		return getFormatter(cell).modelToDisplayValue(canonicalValue);
	}
	
	@Override
	public Object displayToCanonicalValue(final ILayerCell cell, final IConfigRegistry configRegistry,
			final Object displayValue) {
		throw new UnsupportedOperationException();
	}
	
}
