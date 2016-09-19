/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.derby.impl.stats;

import org.spark_project.guava.base.Function;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.types.SQLChar;
import com.splicemachine.stats.ColumnStatistics;
import com.splicemachine.stats.estimate.Distribution;
import com.splicemachine.stats.frequency.FrequencyEstimate;

/**
 * @author Scott Fines
 *         Date: 2/27/15
 */
public class CharStats extends StringStatistics {

    public CharStats() { }


    public CharStats(ColumnStatistics<String> stats,int strLen) {
        super(stats,strLen);
    }

    @Override protected DataValueDescriptor getDvd(String s) { return new SQLChar(s); }

    @Override
    protected Function<FrequencyEstimate<String>, FrequencyEstimate<DataValueDescriptor>> conversionFunction() {
        return conversionFunction;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ColumnStatistics<DataValueDescriptor> getClone() {
        return new CharStats((ColumnStatistics<String>)baseStats.getClone(),strLen);
    }

    /* ***************************************************************************************************************/
    /*private helper methods*/
    private static class CharFreq extends Freq{

        public CharFreq(FrequencyEstimate<String> intFrequencyEstimate) {
            super(intFrequencyEstimate);
        }

        @Override
        protected DataValueDescriptor getDvd(String value) {
            return new SQLChar(value);
        }
    }

    static final Function<FrequencyEstimate<String>,FrequencyEstimate<DataValueDescriptor>> conversionFunction = new Function<FrequencyEstimate<String>, FrequencyEstimate<DataValueDescriptor>>() {
        @Override
        public FrequencyEstimate<DataValueDescriptor> apply(FrequencyEstimate<String> stringFrequencyEstimate) {
            return new CharFreq(stringFrequencyEstimate);
        }
    };
}
