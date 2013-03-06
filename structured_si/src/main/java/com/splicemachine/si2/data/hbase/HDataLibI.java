package com.splicemachine.si2.data.hbase;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.OperationWithAttributes;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.RowLock;
import org.apache.hadoop.hbase.client.Scan;

import java.util.List;

public interface HDataLibI {
    byte[] newRowKey(Object[] args);

    byte[] encode(Object value);
    Object decode(byte[] value, Class type);

    void addAttribute(OperationWithAttributes operation, String attributeName, byte[] value);
    byte[] getAttribute(OperationWithAttributes operation, String attributeName);

    Result newResult(byte[] key, List keyValues);
    byte[] getResultKey(Result result);
    List listResult(Result result);
    List getResultColumn(Result result, byte[] family, byte[] qualifier);
    byte[] getResultValue(Result result, byte[] family, byte[] qualifier);

    byte[] getKeyValueFamily(KeyValue keyValue);
    byte[] getKeyValueQualifier(KeyValue keyValue);
    byte[] getKeyValueValue(KeyValue keyValue);
    long getKeyValueTimestamp(KeyValue keyValue);

    Put newPut(byte[] key);
    Put newPut(byte[] key, RowLock lock);
    void addKeyValueToPut(Put put, byte[] family, byte[] qualifier, Long timestamp, byte[] value);
    List listPut(Put put);
    byte[] getPutKey(Put put);

    Get newGet(byte[] rowKey, List families, List<List> columns,
               Long effectiveTimestamp);

    Scan newScan(byte[] startRowKey, byte[] endRowKey, List families, List<List> columns,
                 Long effectiveTimestamp);
}
