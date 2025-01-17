/*
 * Copyright (c) 2018 Splice Machine, Inc.
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

import com.splicemachine.SpliceKryoRegistry;

/**
 * @author Mark Sirek
 *         Date: 6/7/18
 */
public class V3SerializerMap extends V2SerializerMap {

        public static final V3SerializerMap SPARSE_MAP = new V3SerializerMap(true);
        public static final V3SerializerMap DENSE_MAP = new V3SerializerMap(false);

        public static final String VERSION = "3.0";

        public static V3SerializerMap instance(boolean sparse){
                return sparse? SPARSE_MAP: DENSE_MAP;
        }

        public V3SerializerMap(boolean sparse) {
                super(sparse);
        }

        @Override
        protected void populateFactories(boolean sparse) {
                factories[0]  = NullDescriptorSerializer.nullFactory(BooleanDescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[1]  = NullDescriptorSerializer.nullFactory(ScalarDescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[2]  = NullDescriptorSerializer.floatChecker(RealDescriptorSerializer.INSTANCE_FACTORY, sparse);
                factories[3]  = NullDescriptorSerializer.doubleChecker(DoubleDescriptorSerializer.INSTANCE_FACTORY, sparse);
                factories[4]  = NullDescriptorSerializer.nullFactory(StringDescriptorSerializer.INSTANCE_FACTORY, sparse);
                factories[5]  = NullDescriptorSerializer.nullFactory(KryoDescriptorSerializer.newFactory(SpliceKryoRegistry.getInstance()),sparse);
                factories[6]  = NullDescriptorSerializer.nullFactory(DateDescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[7]  = NullDescriptorSerializer.nullFactory(TimeDescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[8]  = NullDescriptorSerializer.nullFactory(TimestampV3DescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[9]  = NullDescriptorSerializer.nullFactory(UnsortedBinaryDescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[10] = NullDescriptorSerializer.nullFactory(DecimalDescriptorSerializer.INSTANCE_FACTORY,sparse);
                factories[11] = NullDescriptorSerializer.nullFactory(RefDescriptorSerializer.INSTANCE_FACTORY, sparse);
                factories[12] = NullDescriptorSerializer.nullFactory(UDTDescriptorSerializer.INSTANCE_FACTORY, sparse);
                factories[13] = NullDescriptorSerializer.nullFactory(DecfloatDescriptorSerializer.INSTANCE_FACTORY, sparse);

                eagerFactories[0]  = NullDescriptorSerializer.nullFactory(BooleanDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[1]  = NullDescriptorSerializer.nullFactory(ScalarDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[2]  = NullDescriptorSerializer.floatChecker(RealDescriptorSerializer.INSTANCE_FACTORY, sparse);
                eagerFactories[3]  = NullDescriptorSerializer.doubleChecker(DoubleDescriptorSerializer.INSTANCE_FACTORY, sparse);
                eagerFactories[4]  = NullDescriptorSerializer.nullFactory(StringDescriptorSerializer.INSTANCE_FACTORY, sparse);
                eagerFactories[5]  = NullDescriptorSerializer.nullFactory(KryoDescriptorSerializer.newFactory(SpliceKryoRegistry.getInstance()),sparse);
                eagerFactories[6]  = NullDescriptorSerializer.nullFactory(DateDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[7]  = NullDescriptorSerializer.nullFactory(TimeDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[8]  = NullDescriptorSerializer.nullFactory(TimestampV3DescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[9]  = NullDescriptorSerializer.nullFactory(UnsortedBinaryDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[10] = NullDescriptorSerializer.nullFactory(DecimalDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[11] = NullDescriptorSerializer.nullFactory(RefDescriptorSerializer.INSTANCE_FACTORY,sparse);
                eagerFactories[12] = NullDescriptorSerializer.nullFactory(UDTDescriptorSerializer.INSTANCE_FACTORY, sparse);
                eagerFactories[13] = NullDescriptorSerializer.nullFactory(DecfloatDescriptorSerializer.INSTANCE_FACTORY, sparse);

        }
}
