package com.splicemachine.db.impl.ast;

import com.google.common.base.*;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.MessageId;
import com.splicemachine.db.iapi.sql.compile.Visitable;
import com.splicemachine.db.impl.sql.compile.*;
import org.sparkproject.guava.collect.Lists;
import java.util.*;

/**
 * Visitor that checks for plan-time structures know to be unsupported by Splice, and
 * throws exception when found.
 *
 * Currently the only unsupported structure identified is an update or delete with a
 * materializing operation underneath.
 *
 * @author P Trolard
 *         Date: 10/02/2014
 */
public class UnsupportedFormsDetector extends AbstractSpliceVisitor {
    @Override
    public Visitable visit(DeleteNode node) throws StandardException {
        // checkForUnsupported(node);
        return node;
    }

    @Override
    public Visitable visit(UpdateNode node) throws StandardException {
        // checkForUnsupported(node);
        return node;
    }

    public static void checkForUnsupported(DMLStatementNode node) throws StandardException {
        List<ResultSetNode> sinks = Lists.newLinkedList(RSUtils.sinkingChildren(node.getResultSetNode()));
        if (sinks.size() > 0){
            throw StandardException.newException(MessageId.SPLICE_UNSUPPORTED_OPERATION,
                                                    unsupportedSinkingMsg(node, sinks));
        }
    }

    public static String unsupportedSinkingMsg(DMLStatementNode dml, List<ResultSetNode> rsns) {
        String modder = dml instanceof DeleteNode ? "A Delete" : "An Update";
        List<String> sinkingOps = Lists.transform(rsns, new Function<ResultSetNode, String>() {
            @Override
            public String apply(ResultSetNode input) {
                return RSUtils.sinkingNames.get(input.getClass());
            }
        });
        return String.format("%s over %s operations", modder,
                                StringUtils.asEnglishList(sinkingOps, "or"));

    }

}