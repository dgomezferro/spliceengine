/*
 * Copyright (c) 2012 - 2020 Splice Machine, Inc.
 *
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.splicemachine.derby.utils.marshall.dvd;

import com.splicemachine.db.iapi.types.SQLDecimal;
import com.splicemachine.encoding.Encoding;
import com.splicemachine.encoding.MultiFieldDecoder;
import com.splicemachine.encoding.MultiFieldEncoder;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.types.DataValueDescriptor;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Scott Fines
 * Date: 4/2/14
 */
class DecimalDescriptorSerializer implements DescriptorSerializer {
		private static final DescriptorSerializer INSTANCE = new DecimalDescriptorSerializer();
		public static final Factory INSTANCE_FACTORY = new Factory() {
				@Override
				public DescriptorSerializer newInstance() {
						return INSTANCE;
				}
				@Override public boolean applies(DataValueDescriptor dvd) { return dvd!=null && applies(dvd.getTypeFormatId()); }
				@Override public boolean applies(int typeFormatId) { return typeFormatId == StoredFormatIds.SQL_DECIMAL_ID; }

				@Override public boolean isScalar() { return false; }
				@Override public boolean isFloat() { return false; }
				@Override public boolean isDouble() { return false; }
		};

		private DecimalDescriptorSerializer() { }


		@Override
		public void encode(MultiFieldEncoder fieldEncoder, DataValueDescriptor dvd, boolean desc) throws StandardException {
			fieldEncoder.encodeNext((BigDecimal)dvd.getObject(),desc);
		}

		@Override
		public byte[] encodeDirect(DataValueDescriptor dvd, boolean desc) throws StandardException {
				return Encoding.encode((BigDecimal)dvd.getObject(),desc);
		}

		@Override
		public void decode(MultiFieldDecoder fieldDecoder, DataValueDescriptor destDvd, boolean desc) throws StandardException {
				destDvd.setBigDecimal(fieldDecoder.decodeNextBigDecimal(desc));
		}

		@Override
		public void decodeDirect(DataValueDescriptor dvd, byte[] data, int offset, int length, boolean desc) throws StandardException {
				SQLDecimal d = (SQLDecimal)dvd;
				int precision = d.getPrecision();
				int scale = d.getScale();
				d.setBigDecimal( Encoding.decodeBigDecimal(data,offset,length,desc) );
				d.setPrecision( precision );
				d.setScale( scale );
		}

		@Override public boolean isScalarType() { return false; }
		@Override public boolean isFloatType() { return false; }
		@Override public boolean isDoubleType() { return false; }
		@Override public void close() throws IOException {  }
}
