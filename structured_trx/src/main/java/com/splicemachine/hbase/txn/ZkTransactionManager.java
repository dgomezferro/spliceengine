package com.splicemachine.hbase.txn;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.splicemachine.constants.ITransactionState;
import com.splicemachine.constants.TransactionStatus;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import com.splicemachine.constants.TxnConstants;
import com.splicemachine.hbase.txn.coprocessor.region.TxnUtils;
import com.splicemachine.utils.SpliceLogUtils;

public class ZkTransactionManager extends TransactionManager {
    static final Logger LOG = Logger.getLogger(ZkTransactionManager.class);
    protected String transactionPath;
    protected JtaXAResource xAResource;
  
    public ZkTransactionManager(final Configuration conf) throws IOException {
    	super(conf);
    	this.transactionPath = conf.get(TxnConstants.TRANSACTION_PATH_NAME,TxnConstants.DEFAULT_TRANSACTION_PATH);
    }

    public ZkTransactionManager(final String transactionPath, final Configuration conf) throws IOException {
    	super(conf);
    	this.transactionPath = transactionPath;
    }
    
    public ZkTransactionManager(final Configuration conf, ZooKeeperWatcher zkw, RecoverableZooKeeper rzk) throws IOException {
    	this.transactionPath = conf.get(TxnConstants.TRANSACTION_PATH_NAME,TxnConstants.DEFAULT_TRANSACTION_PATH);
    	this.zkw = zkw;
    	this.rzk = rzk;
    }

    public ZkTransactionManager(final String transactionPath, ZooKeeperWatcher zkw, RecoverableZooKeeper rzk) throws IOException {
    	this.transactionPath = transactionPath;
    	this.zkw = zkw;
    	this.rzk = rzk;
    }
    
    public TransactionState beginTransaction() throws KeeperException, InterruptedException, IOException, ExecutionException {
    	SpliceLogUtils.trace(LOG, "Begin transaction");
    	return new TransactionState(TxnUtils.beginTransaction(transactionPath, zkw));
    }
   
    public int prepareCommit(final ITransactionState iTransactionState) throws KeeperException, InterruptedException, IOException {
        TransactionState transactionState = (TransactionState) iTransactionState;
    	SpliceLogUtils.trace(LOG, "Do prepareCommit on " + transactionState.getTransactionID());
    	TxnUtils.prepareCommit(transactionState.getTransactionID(), rzk);
    	return 0;
     }

    @Override
    public void prepareCommit2(Object bonus, ITransactionState transactionState) throws KeeperException, InterruptedException, IOException {
        RecoverableZooKeeper recoverableZooKeeper = (RecoverableZooKeeper) bonus;
        recoverableZooKeeper.setData(transactionState.getTransactionID(), Bytes.toBytes(TransactionStatus.PREPARE_COMMIT.toString()), -1);
    }

    public void doCommit(final ITransactionState iTransactionState) throws KeeperException, InterruptedException, IOException  {
        TransactionState transactionState = (TransactionState) iTransactionState;
    	SpliceLogUtils.trace(LOG, "Do commit on " + transactionState.getTransactionID());
    	TxnUtils.doCommit(transactionState.getTransactionID(), rzk);
    }

    public void tryCommit(final ITransactionState iTransactionState) throws IOException, KeeperException, InterruptedException {
        TransactionState transactionState = (TransactionState) iTransactionState;
    	SpliceLogUtils.trace(LOG, "Try commit on " +transactionState.getTransactionID());
       	prepareCommit(transactionState);
       	doCommit(transactionState);
    }
    
    public void abort(final ITransactionState iTransactionState) throws IOException, KeeperException, InterruptedException {
        TransactionState transactionState = (TransactionState) iTransactionState;
    	SpliceLogUtils.trace(LOG, "Abort on " +transactionState.getTransactionID());
    	TxnUtils.abort(transactionState.getTransactionID(), rzk);
     }

    public synchronized JtaXAResource getXAResource() {
        if (xAResource == null) {
            xAResource = new JtaXAResource(this);
        }
        return xAResource;
    }
}
