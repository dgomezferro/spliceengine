package com.splicemachine.si.impl;

import com.splicemachine.si.api.txn.KeepAliveScheduler;
import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnStore;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Specific Keep-Alive Scheduler to allow us to timeout and/or keep Alive
 * transactions at our leisure, for testing purposes.
 *
 * @author Scott Fines
 *         Date: 6/25/14
 */
public class ManualKeepAliveScheduler implements KeepAliveScheduler{
    private final Map<Long, Txn> txnMap=new ConcurrentHashMap<Long, Txn>();

    private final TxnStore store;

    public ManualKeepAliveScheduler(TxnStore store){
        this.store=store;
    }

    @Override
    public void scheduleKeepAlive(Txn txn){
        txnMap.put(txn.getTxnId(),txn);
    }

    public void keepAlive(long txnId) throws IOException{
        Txn txn=txnMap.get(txnId);
        if(txn==null) return;
        if(txn.getEffectiveState()!=Txn.State.ACTIVE) return; //do nothing if we are already terminated

        store.keepAlive(txn.getTxnId());
    }

    @Override
    public void start(){

    }

    @Override
    public void stop(){

    }
}
