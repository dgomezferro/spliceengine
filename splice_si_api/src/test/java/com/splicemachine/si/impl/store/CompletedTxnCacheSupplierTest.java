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

package com.splicemachine.si.impl.store;

import com.splicemachine.concurrent.IncrementingClock;
import com.splicemachine.si.api.txn.*;
import com.splicemachine.si.impl.txn.WritableTxn;
import com.splicemachine.si.testenv.ArchitectureIndependent;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static com.splicemachine.si.impl.TxnTestUtils.assertTxnsMatch;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Scott Fines
 *         Date: 7/1/14
 */
@Category(ArchitectureIndependent.class)
public class CompletedTxnCacheSupplierTest{

    @Test
    public void testDoesNotCacheActiveTransactions() throws Exception{
        final AtomicLong al=new AtomicLong(0l);
        TxnLifecycleManager tc=mock(TxnLifecycleManager.class);
        when(tc.commit(anyLong())).thenAnswer(new Answer<Long>(){

            @Override
            public Long answer(InvocationOnMock invocationOnMock) throws Throwable{
                return al.incrementAndGet();
            }
        });
        Txn txn=new WritableTxn(0x100l,0x100l,null,Txn.IsolationLevel.SNAPSHOT_ISOLATION,Txn.ROOT_TRANSACTION,tc,false,null);

        final boolean[] called=new boolean[]{false};
        TxnStore backStore=new TestingTxnStore(new IncrementingClock(),new TestingTimestampSource(),null,Long.MAX_VALUE){
            @Override
            public TxnView getTransaction(long txnId,boolean getDestinationTables) throws IOException{
                Assert.assertFalse("Item should have been fed from cache!",called[0]);
                called[0]=true;
                return super.getTransaction(txnId,getDestinationTables);
            }
        };
        backStore.recordNewTransaction(txn);

        TxnSupplier store=new CompletedTxnCacheSupplier(backStore,10,16);


        //fetch the transaction from the underlying store
        Assert.assertFalse("Cache thinks it already has the item!",store.transactionCached(txn.getTxnId()));

        TxnView fromStore=store.getTransaction(txn.getTxnId());
        assertTxnsMatch("Transaction from store is not correct!",txn,fromStore);

        Assert.assertFalse("Cache does not think it is present!",store.transactionCached(txn.getTxnId()));
    }

    @Test
    public void testCachesRolledBackTransactions() throws Exception{
        final AtomicLong al=new AtomicLong(0l);
        TxnLifecycleManager tc=mock(TxnLifecycleManager.class);
        when(tc.commit(anyLong())).thenAnswer(new Answer<Long>(){

            @Override
            public Long answer(InvocationOnMock invocationOnMock) throws Throwable{
                return al.incrementAndGet();
            }
        });
        Txn txn=new WritableTxn(0x100l,0x100l,null,Txn.IsolationLevel.SNAPSHOT_ISOLATION,Txn.ROOT_TRANSACTION,tc,false,null);
        txn.rollback();

        final boolean[] called=new boolean[]{false};
        TxnStore backStore=new TestingTxnStore(new IncrementingClock(),new TestingTimestampSource(),null,Long.MAX_VALUE){
            @Override
            public TxnView getTransaction(long txnId,boolean getDestinationTables) throws IOException{
                Assert.assertFalse("Item should have been fed from cache!",called[0]);
                called[0]=true;
                return super.getTransaction(txnId,getDestinationTables);
            }
        };
        backStore.recordNewTransaction(txn);

        TxnSupplier store=new CompletedTxnCacheSupplier(backStore,10,16);


        //fetch the transaction from the underlying store
        Assert.assertFalse("Cache thinks it already has the item!",store.transactionCached(txn.getTxnId()));

        TxnView fromStore=store.getTransaction(txn.getTxnId());
        assertTxnsMatch("Transaction from store is not correct!",txn,fromStore);

        Assert.assertTrue("Cache does not think it is present!",store.transactionCached(txn.getTxnId()));

        TxnView fromCache=store.getTransaction(txn.getTxnId());
        assertTxnsMatch("Transaction from store is not correct!",txn,fromCache);
    }

    @Test
    public void testCachesCommittedTransactions() throws Exception{
        final AtomicLong al=new AtomicLong(0l);
        TxnLifecycleManager tc=mock(TxnLifecycleManager.class);
        when(tc.commit(anyLong())).thenAnswer(new Answer<Long>(){

            @Override
            public Long answer(InvocationOnMock invocationOnMock) throws Throwable{
                return al.incrementAndGet();
            }
        });
        Txn txn=new WritableTxn(0x100l,0x100l,null,Txn.IsolationLevel.SNAPSHOT_ISOLATION,Txn.ROOT_TRANSACTION,tc,false,null);
        txn.commit();

        final boolean[] called=new boolean[]{false};
        TxnStore backStore=new TestingTxnStore(new IncrementingClock(),new TestingTimestampSource(),null,Long.MAX_VALUE){
            @Override
            public TxnView getTransaction(long txnId,boolean getDestinationTables) throws IOException{
                Assert.assertFalse("Item should have been fed from cache!",called[0]);
                called[0]=true;
                return super.getTransaction(txnId,getDestinationTables);
            }

            @Override
            public Txn getTransactionFromCache(long txnId){
                return null;
            }
        };
        backStore.recordNewTransaction(txn);

        TxnSupplier store=new CompletedTxnCacheSupplier(backStore,10,16);


        //fetch the transaction from the underlying store
        Assert.assertFalse("Cache thinks it already has the item!",store.transactionCached(txn.getTxnId()));

        TxnView fromStore=store.getTransaction(txn.getTxnId());
        assertTxnsMatch("Transaction from store is not correct!",txn,fromStore);

        Assert.assertTrue("Cache does not think it is present!",store.transactionCached(txn.getTxnId()));

        TxnView fromCache=store.getTransaction(txn.getTxnId());
        assertTxnsMatch("Transaction from store is not correct!",txn,fromCache);
    }
}
