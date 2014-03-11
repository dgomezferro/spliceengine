package com.splicemachine.si.impl;

import com.splicemachine.hbase.KeyValueUtils;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.OperationWithAttributes;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.log4j.Logger;
import java.io.IOException;

public class FilterStatePacked<Result, Put extends OperationWithAttributes, Delete, Get extends OperationWithAttributes,
        Scan, Lock, OperationStatus, Mutation, IHTable> implements IFilterState {
    static final Logger LOG = Logger.getLogger(FilterStatePacked.class);
    protected final FilterState<Result, Put, Delete, Get, Scan, Lock, OperationStatus,
						Mutation, IHTable> simpleFilter;
    protected final RowAccumulator accumulator;
    private KeyValue lastValidKeyValue;
    protected boolean excludeRow = false;

    public FilterStatePacked(FilterState<Result, Put, Delete, Get, Scan, Lock,
                            OperationStatus, Mutation, IHTable> simpleFilter,
                             RowAccumulator accumulator) {
        this.simpleFilter = simpleFilter;
        this.accumulator = accumulator;
    }

		public RowAccumulator getAccumulator(){
				return accumulator;
		}

    @Override
    public Filter.ReturnCode filterKeyValue(org.apache.hadoop.hbase.KeyValue dataKeyValue) throws IOException {
        simpleFilter.setKeyValue(dataKeyValue);
        final Filter.ReturnCode returnCode = simpleFilter.filterByColumnType();
        switch (simpleFilter.type) {
            case COMMIT_TIMESTAMP:
                return returnCode; // These are always skip...
            case USER_DATA:
                switch (returnCode) {
                	case INCLUDE:
                	case INCLUDE_AND_NEXT_COL:
											return doAccumulate(dataKeyValue);
                    case SKIP:
                    case NEXT_COL:
                    case NEXT_ROW:
                    	return Filter.ReturnCode.SKIP;
                    default:
                    	throw new RuntimeException("unknown return code");
                }
            case TOMBSTONE:
            case ANTI_TOMBSTONE:
            case OTHER:
                return returnCode; // These are always skip...

            default:
            	throw new RuntimeException("unknown key value type");
        }
    }

		protected Filter.ReturnCode doAccumulate(KeyValue dataKeyValue) throws IOException {
				if (!accumulator.isFinished() && !excludeRow && accumulator.isOfInterest(dataKeyValue)) {
						if (!accumulator.accumulate(simpleFilter.keyValue.keyValue())) {
								excludeRow = true;
						}
				}
				if (lastValidKeyValue == null) {
						lastValidKeyValue = dataKeyValue;
						return Filter.ReturnCode.INCLUDE;
				}
				return Filter.ReturnCode.SKIP;
		}


		@Override
		public KeyValue produceAccumulatedKeyValue() {
				if (accumulator.isCountStar())
						return lastValidKeyValue;
				if (lastValidKeyValue == null)
						return null;
				final byte[] resultData = accumulator.result();
				if (resultData != null) {
						return KeyValueUtils.newKeyValue(lastValidKeyValue, resultData);
				} else {
						return null;
				}
		}

		@Override
		public boolean getExcludeRow() {
				return excludeRow || lastValidKeyValue == null;
		}

    @Override
    public void nextRow() {
        simpleFilter.nextRow();
        lastValidKeyValue = null;
        excludeRow = false;
    }

}
