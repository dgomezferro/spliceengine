/*
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * Some parts of this source code are based on Apache Derby, and the following notices apply to
 * Apache Derby:
 *
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified the Apache Derby code in this file.
 *
 * All such Splice Machine modifications are Copyright 2012 - 2020 Splice Machine, Inc.,
 * and are licensed to you under the GNU Affero General Public License.
 */

package com.splicemachine.db.impl.sql;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.Property;
import com.splicemachine.db.iapi.reference.GlobalDBProperties;
import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.services.loader.GeneratedClass;
import com.splicemachine.db.iapi.services.property.PropertyUtil;
import com.splicemachine.db.iapi.services.sanity.SanityManager;
import com.splicemachine.db.iapi.sql.PreparedStatement;
import com.splicemachine.db.iapi.sql.Statement;
import com.splicemachine.db.iapi.sql.compile.*;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.sql.conn.StatementContext;
import com.splicemachine.db.iapi.sql.depend.Dependency;
import com.splicemachine.db.iapi.sql.dictionary.DataDictionary;
import com.splicemachine.db.iapi.sql.dictionary.SchemaDescriptor;
import com.splicemachine.db.iapi.sql.execute.ExecutionContext;
import com.splicemachine.db.iapi.types.DataTypeDescriptor;
import com.splicemachine.db.iapi.types.FloatingPointDataType;
import com.splicemachine.db.iapi.types.SQLTimestamp;
import com.splicemachine.db.iapi.util.ByteArray;
import com.splicemachine.db.iapi.util.InterruptStatus;
import com.splicemachine.db.impl.ast.JsonTreeBuilderVisitor;
import com.splicemachine.db.impl.sql.compile.CharTypeCompiler;
import com.splicemachine.db.impl.sql.compile.ExplainNode;
import com.splicemachine.db.impl.sql.compile.StatementNode;
import com.splicemachine.db.impl.sql.compile.TriggerReferencingStruct;
import com.splicemachine.db.impl.sql.conn.GenericLanguageConnectionContext;
import com.splicemachine.db.impl.sql.misc.CommentStripper;
import com.splicemachine.system.SimpleSparkVersion;
import com.splicemachine.system.SparkVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static com.splicemachine.db.iapi.reference.Property.SPLICE_SPARK_COMPILE_VERSION;
import static com.splicemachine.db.iapi.reference.Property.SPLICE_SPARK_VERSION;
import static com.splicemachine.db.iapi.sql.compile.CompilerContext.MAX_DERIVED_CNF_PREDICATES_MAX_VALUE;
import static com.splicemachine.db.iapi.sql.compile.CompilerContext.MAX_MULTICOLUMN_PROBE_VALUES_MAX_VALUE;
import static com.splicemachine.db.impl.sql.compile.CharTypeCompiler.getCurrentCharTypeCompiler;

@SuppressWarnings("SynchronizeOnNonFinalField")
@SuppressFBWarnings(value = {"IS2_INCONSISTENT_SYNC", "ML_SYNC_ON_FIELD_TO_GUARD_CHANGING_THAT_FIELD"}, justification = "FIXME: DB-10223")
public class GenericStatement implements Statement{
    protected static final AtomicInteger jsonIncrement = new AtomicInteger(0);
    protected int actualJsonIncrement = -1;
    private static final Logger JSON_TREE_LOG = Logger.getLogger(JsonTreeBuilderVisitor.class);

    // these fields define the identity of the statement
    private final SchemaDescriptor compilationSchema;
    private final String statementText;
    private final boolean isForReadOnly;
    private int prepareIsolationLevel;
    private GenericStorablePreparedStatement preparedStmt;
    private String sessionPropertyValues = "null";
    private final String statementTextTrimed;

    /**
     * Constructor for a Statement given the text of the statement in a String
     *
     * @param compilationSchema schema
     * @param statementText     The text of the statement
     * @param isForReadOnly     if the statement is opened with level CONCUR_READ_ONLY
     */

    public GenericStatement(SchemaDescriptor compilationSchema,String statementText,boolean isForReadOnly, LanguageConnectionContext lcc) throws StandardException{
        this.compilationSchema=compilationSchema;
        this.statementText=statementText;
        this.isForReadOnly=isForReadOnly;
        if (lcc.getIgnoreCommentOptEnabled()) {
            this.statementTextTrimed = filterComment(statementText, lcc);
        } else {
            this.statementTextTrimed = statementText;
        }
    }

    public String getStatementText() {
        return statementTextTrimed;
    }

    public PreparedStatement prepare(LanguageConnectionContext lcc) throws StandardException{
        /*
        ** Note: don't reset state since this might be
        ** a recompilation of an already prepared statement.
        */
        return prepare(lcc,false);
    }

    public PreparedStatement prepare(LanguageConnectionContext lcc,boolean forMetaData) throws StandardException{
        return prepare(lcc, forMetaData, null);
    }

    // If boundAndOptimizedStatement is passed in, we don't try to
    // parse, bind and optimize the statement from its SQL text
    // before passing to code generation.
    // Instead we just directly compile boundAndOptimizedStatement.
    public PreparedStatement prepare(LanguageConnectionContext lcc,
                                     boolean forMetaData,
                                     StatementNode boundAndOptimizedStatement) throws StandardException{
        /*
        ** Note: don't reset state since this might be
        ** a recompilation of an already prepared statement.
        */

        final int depth=lcc.getStatementDepth();
        boolean recompile=false;
        try{
            return prepMinion(lcc,true,null,null,forMetaData, boundAndOptimizedStatement);
        } catch(Throwable t){
            StandardException se = StandardException.getOrWrap(t);
            // There is a chance that we didn't see the invalidation
            // request from a DDL operation in another thread because
            // the statement wasn't registered as a dependent until
            // after the invalidation had been completed. Assume that's
            // what has happened if we see a conglomerate does not exist
            // error, and force a retry even if the statement hasn't been
            // invalidated.
            if(SQLState.STORE_CONGLOMERATE_DOES_NOT_EXIST.equals(se.getMessageId())){
                // Request a recompile of the statement
                recompile=true;
            }
            throw se;
        }finally{
            // Check if the statement was invalidated while it was
            // compiled. If so, the newly compiled plan may not be
            // up to date anymore, so we recompile the statement
            // if this happens. Note that this is checked in a finally
            // block, so we also retry if an exception was thrown. The
            // exception was probably thrown because of the changes
            // that invalidated the statement. If not, recompiling
            // will also fail, and the exception will be exposed to
            // the caller.
            //
            // invalidatedWhileCompiling and isValid are protected by
            // synchronization on the prepared statement.
            synchronized(preparedStmt){
                if(recompile || preparedStmt.invalidatedWhileCompiling){
                    preparedStmt.isValid=false;
                    preparedStmt.invalidatedWhileCompiling=false;
                    recompile=true;
                }
            }

            if(recompile){
                // A new statement context is pushed while compiling.
                // Typically, this context is popped by an error
                // handler at a higher level. But since we retry the
                // compilation, the error handler won't be invoked, so
                // the stack must be reset to its original state first.
                while(lcc.getStatementDepth()>depth){
                    lcc.popStatementContext(
                            lcc.getStatementContext(),null);
                }
            }
        }
    }

    /**
     * Generates an execution plan given a set of named parameters.
     * Does so for a storable prepared statement.
     *
     * @return A PreparedStatement that allows execution of the execution
     * plan.
     * @throws StandardException Thrown if this is an
     *                           execution-only version of the module (the prepare() method
     *                           relies on compilation).
     * @param    paramDefaults        Parameter defaults
     */
    public PreparedStatement prepareStorable(LanguageConnectionContext lcc,
                                             PreparedStatement ps,
                                             Object[] paramDefaults,
                                             SchemaDescriptor spsSchema,
                                             boolean internalSQL) throws StandardException{
        if(ps==null)
            ps=new GenericStorablePreparedStatement(this);
        else
            ((GenericStorablePreparedStatement)ps).statement=this;

        this.preparedStmt=(GenericStorablePreparedStatement)ps;
        return prepMinion(lcc,false,paramDefaults,spsSchema,internalSQL);
    }

    @Override
    public String getSource(){ return statementText; }

    @Override
    public String getSessionPropertyValues() {
        return sessionPropertyValues;
    }

    public String getCompilationSchema(){ return compilationSchema.getDescriptorName(); }

    /**
     * Return the {@link PreparedStatement} currently associated with this
     * statement.
     *
     * @return the prepared statement that is associated with this statement
     */
    public PreparedStatement getPreparedStatement(){ return preparedStmt; }

    public boolean equals(Object other){
        if(other instanceof GenericStatement){
            GenericStatement os=(GenericStatement)other;
            return statementTextTrimed.equals(os.statementTextTrimed) && sessionPropertyValues.equals(os.sessionPropertyValues) && isForReadOnly==os.isForReadOnly
                    && compilationSchema.equals(os.compilationSchema) &&
                    (prepareIsolationLevel==os.prepareIsolationLevel);
        }
        return false;
    }

    public int hashCode(){ return statementTextTrimed.hashCode(); }

    public String toString() {
        return statementText.trim().toUpperCase() + "[session properties: " + sessionPropertyValues + "]";
    }

    private static long getCurrentTimeMillis(LanguageConnectionContext lcc){
        return 0;
    }

    private PreparedStatement prepMinion(LanguageConnectionContext lcc,
                                         boolean cacheMe,
                                         Object[] paramDefaults,
                                         SchemaDescriptor spsSchema,
                                         boolean internalSQL) throws StandardException{
        return prepMinion(lcc, cacheMe, paramDefaults, spsSchema, internalSQL, null);
    }

    private PreparedStatement prepMinion(LanguageConnectionContext lcc,
                                         boolean cacheMe,
                                         Object[] paramDefaults,
                                         SchemaDescriptor spsSchema,
                                         boolean internalSQL,
                                         StatementNode boundAndOptimizedStatement) throws StandardException{

        /*
         * An array holding timestamps for various points in time. The order is
         *
         * 0:   beginTime
         * 1:   parseTime
         * 2:   bindTime
         * 3:   optimizeTime
         * 4:   generateTime
         */
        long[] timestamps = new long[5];
        StatementContext statementContext=null;

        // verify it isn't already prepared...
        // if it is, and is valid, simply return that tree.
        // if it is invalid, we will recompile now.
        if(preparedStmt!=null){
            if(preparedStmt.upToDate())
                return preparedStmt;
        }

        // Clear the optimizer trace from the last statement
        if(lcc.getOptimizerTrace())
            lcc.setOptimizerTraceOutput(getSource()+"\n");

        timestamps[0]=getCurrentTimeMillis(lcc);

        /** set the prepare Isolaton from the LanguageConnectionContext now as
         * we need to consider it in caching decisions
         */
        prepareIsolationLevel=lcc.getPrepareIsolationLevel();

        /* a note on statement caching:
         *
         * A GenericPreparedStatement (GPS) is only added it to the cache if the
         * parameter cacheMe is set to TRUE when the GPS is created.
         *
         * Earlier only CacheStatement (CS) looked in the statement cache for a
         * prepared statement when prepare was called. Now the functionality
         * of CS has been folded into GenericStatement (GS). So we search the
         * cache for an existing PreparedStatement only when cacheMe is TRUE.
         * i.e if the user calls prepare with cacheMe set to TRUE:
         * then we
         *         a) look for the prepared statement in the cache.
         *         b) add the prepared statement to the cache.
         *
         * In cases where the statement cache has been disabled (by setting the
         * relevant Derby property) then the value of cacheMe is irrelevant.
         */
        boolean foundInCache=false;
        sessionPropertyValues = lcc.getCurrentSessionPropertyDelimited();
        if (lcc.getIgnoreCommentOptEnabled()) {
            lcc.setOrigStmtTxt(statementText);
        }

        if(preparedStmt==null){
            if(cacheMe)
                preparedStmt=(GenericStorablePreparedStatement)((GenericLanguageConnectionContext)lcc).lookupStatement(this);

            if (preparedStmt==null || preparedStmt.referencesSessionSchema()) {
                // cannot use this state since it is private to a connection.
                // switch to a new statement.
                preparedStmt = new GenericStorablePreparedStatement(this);
            } else {
                foundInCache=true;
            }
        }

        // if anyone else also has this prepared statement,
        // we don't want them trying to compile with it while
        // we are.  So, we synchronize on it and re-check
        // its validity first.
        // this is a no-op if and until there is a central
        // cache of prepared statement objects...
        synchronized(preparedStmt){
            for(;;){
                // did it get updated while we waited for the lock on it?
                if(preparedStmt.upToDate()){
                    /*
                     * -sf- DB-1082 regression note:
                     *
                     * The Statement Cache and the DependencyManager are separated, which leads to the possibility
                     * that they become out of date with one another. Specifically, when a statement fails it
                     * may be removed from the DependencyManager but *not* removed from the StatementCache. This
                     * makes sense in some ways--you want to indicate that something bad happened, but you don't
                     * necessarily want to recompile a statement because of (say) a unique constraint violation.
                     * Unfortunately, if you do that, then dropping a table won't cause this statement to
                     * recompile, because it won't be in the dependency manager, but it WILL be in the statement
                     * cache. To protect against this case, we recompile it in the event that we are missing
                     * from the DependencyManager, but are still considered up to date. This does not happen in the
                     * event of an insert statement, only for imports(as far as I can tell, anyway).
                     */
                    Collection<Dependency> selfDep = lcc.getDataDictionary().getDependencyManager().find(preparedStmt.getObjectID());
                    if(selfDep!=null){
                        for(Dependency dep:selfDep){
                            if (dep.getDependent().equals(preparedStmt)) {
                                return preparedStmt;
                            }
                        }
                    }
                    //we actually aren't valid, because the dependency is missing.
                    preparedStmt.isValid = false;
                }

                if(!preparedStmt.compilingStatement){
                    break;
                }

                try{
                    preparedStmt.wait();
                }catch(InterruptedException ie){
                    InterruptStatus.setInterrupted();
                }
            }

            preparedStmt.compilingStatement = true;
            preparedStmt.setActivationClass(null);
        }
        CompilerContext cc = null;
        try{
            /*
            ** For stored prepared statements, we want all
            ** errors, etc in the context of the underlying
            ** EXECUTE STATEMENT statement, so don't push/pop
            ** another statement context unless we don't have
            ** one.  We won't have one if it is an internal
            ** SPS (e.g. jdbcmetadata).
            */
            if(!preparedStmt.isStorable() || lcc.getStatementDepth()==0){
                // since this is for compilation only, set atomic
                // param to true and timeout param to 0
                statementContext=lcc.pushStatementContext(true,isForReadOnly,getSource(), null,false,0L);
            }

            /*
            ** RESOLVE: we may ultimately wish to pass in
            ** whether we are a jdbc metadata query or not to
            ** get the CompilerContext to make the createDependency()
            ** call a noop.
            */
            if (boundAndOptimizedStatement != null) {
                cc = boundAndOptimizedStatement.getCompilerContext();
            } else {
                cc = pushCompilerContext(lcc, internalSQL, spsSchema);
            }
            if (internalSQL) {
                cc.setCompilingTrigger(true);
            }
            fourPhasePrepare(lcc,paramDefaults,timestamps,foundInCache,cc,boundAndOptimizedStatement, cacheMe, false);
        } catch (Throwable e) {
            if (foundInCache) {
                ((GenericLanguageConnectionContext) lcc).removeStatement(this);
            }
            throw StandardException.getOrWrap(e);
        }
        finally{
            synchronized(preparedStmt){
                preparedStmt.compilingStatement=false;
                preparedStmt.notifyAll();
            }
            TriggerReferencingStruct.fromTableTriggerDescriptor.remove();
            TriggerReferencingStruct.fromTableTriggerSPSDescriptor.remove();
            // Communicate to the immediate parent statement if its child
            // contains a FROM TABLE clause.
            if (boundAndOptimizedStatement != null)
                TriggerReferencingStruct.isFromTableStatement.get().setValue(true);
            else
                TriggerReferencingStruct.isFromTableStatement.get().setValue(false);
            if (cc != null)
                cc.setCompilingTrigger(false);
        }

        lcc.commitNestedTransaction();

        if (statementContext != null) {
            lcc.popStatementContext(statementContext, null);
        }

        return preparedStmt;
    }

    private CompilerContext pushCompilerContext(LanguageConnectionContext lcc,
                                                boolean internalSQL,
                                                SchemaDescriptor spsSchema) throws StandardException {
        CompilerContext cc = lcc.pushCompilerContext(compilationSchema);

        if (prepareIsolationLevel != ExecutionContext.UNSPECIFIED_ISOLATION_LEVEL) {
            cc.setScanIsolationLevel(prepareIsolationLevel);
        }

        // Look for stored statements that are in a system schema
        // and with a match compilation schema. If so, allow them
        // to compile using internal SQL constructs.
        if (internalSQL ||
                (spsSchema != null) && (spsSchema.isSystemSchema()) && (spsSchema.equals(compilationSchema))) {
            cc.setReliability(CompilerContext.INTERNAL_SQL_LEGAL);
        }

        setSelectivityEstimationIncludingSkewedDefault(lcc, cc);
        setProjectionPruningEnabled(lcc, cc);
        setMaxMulticolumnProbeValues(lcc, cc);
        setMaxDerivedCNFPredicates(lcc, cc);
        setMulticolumnInlistProbeOnSparkEnabled(lcc, cc);
        setConvertMultiColumnDNFPredicatesToInList(lcc, cc);
        setDisablePredicateSimplification(lcc, cc);
        setDisableConstantFolding(lcc, cc);
        setNativeSparkAggregationMode(lcc, cc);
        setAllowOverflowSensitiveNativeSparkExpressions(lcc, cc);
        setNewMergeJoin(lcc, cc);
        setDisableParallelTaskJoinCosting(lcc, cc);
        setDisablePrefixIteratorMode(lcc, cc);
        setDisableSubqueryFlattening(lcc, cc);
        setDisableUnionedIndexScans(lcc, cc);
        setFavorUnionedIndexScans(lcc, cc);
        setCurrentTimestampPrecision(lcc, cc);
        setTimestampFormat(lcc, cc);
        setSecondFunctionCompatibilityMode(lcc, cc);
        setFloatingPointNotation(lcc, cc);
        setCountReturnType(lcc, cc);
        setOuterJoinFlatteningDisabled(lcc, cc);
        setCursorUntypedExpressionType(lcc, cc);

        if (!cc.isSparkVersionInitialized()) {
            setSparkVersion(cc);
        }

        setSSQFlatteningForUpdateDisabled(lcc, cc);
        setAlterTableAutoViewRefreshing(lcc, cc);
        setVarcharDB2CompatibilityMode(lcc, cc);
        return cc;
    }

    private void setVarcharDB2CompatibilityMode(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        // why not getCachedDatabaseBoolean ?
        String varcharDB2CompatibilityModeString =
            PropertyUtil.getCached(lcc, GlobalDBProperties.SPLICE_DB2_VARCHAR_COMPATIBLE);
        boolean varcharDB2CompatibilityMode = CompilerContext.DEFAULT_SPLICE_DB2_VARCHAR_COMPATIBLE;
        try {
            if (varcharDB2CompatibilityModeString != null)
                varcharDB2CompatibilityMode =
                Boolean.parseBoolean(varcharDB2CompatibilityModeString);
            if (varcharDB2CompatibilityMode) {
                CharTypeCompiler charTC = getCurrentCharTypeCompiler(lcc);
                if (charTC != null) {
                    charTC.setDB2VarcharCompatibilityMode(varcharDB2CompatibilityMode);
                    lcc.setDB2VarcharCompatibilityModeNeedsReset(true, charTC);
                }
            }

        } catch (Exception e) {
            // If the property value failed to convert to a boolean, don't throw an error,
            // just use the default setting.
        }
        cc.setVarcharDB2CompatibilityMode(varcharDB2CompatibilityMode);
    }

    private void setAlterTableAutoViewRefreshing(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String valueStr = PropertyUtil.getCached(lcc, GlobalDBProperties.SPLICE_ALTER_TABLE_AUTO_VIEW_REFRESHING);
        boolean value = CompilerContext.DEFAULT_SPLICE_ALTER_TABLE_AUTO_VIEW_REFRESHING;
        if (valueStr != null) {
            value = Boolean.parseBoolean(valueStr);
        }
        cc.setAlterTableAutoViewRefreshing(value);
    }

    private void setSSQFlatteningForUpdateDisabled(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.SSQ_FLATTENING_FOR_UPDATE_DISABLED, CompilerContext.DEFAULT_SSQ_FLATTENING_FOR_UPDATE_DISABLED);
        cc.setSSQFlatteningForUpdateDisabled(param);
    }

    private void setSparkVersion(CompilerContext cc) {
        // If splice.spark.version is manually set, use it...
        String spliceSparkVersionString = System.getProperty(SPLICE_SPARK_VERSION);
        SparkVersion sparkVersion = new SimpleSparkVersion(spliceSparkVersionString);

        // ... otherwise pick up the splice compile-time version of spark.
        if (sparkVersion.isUnknown()) {
            spliceSparkVersionString = System.getProperty(SPLICE_SPARK_COMPILE_VERSION);
            sparkVersion = new SimpleSparkVersion(spliceSparkVersionString);
            if (sparkVersion.isUnknown())
                sparkVersion = CompilerContext.DEFAULT_SPLICE_SPARK_VERSION;
        }
        cc.setSparkVersion(sparkVersion);
    }

    private void setOuterJoinFlatteningDisabled(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.OUTERJOIN_FLATTENING_DISABLED, CompilerContext.DEFAULT_OUTERJOIN_FLATTENING_DISABLED);
        cc.setOuterJoinFlatteningDisabled(param);
    }

    private void setSelectivityEstimationIncludingSkewedDefault(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        /* get the selectivity estimation property so that we know what strategy to use to estimate selectivity */
        String selectivityEstimationString = PropertyUtil.getCachedDatabaseProperty(lcc,
                Property.SELECTIVITY_ESTIMATION_INCLUDING_SKEWED);
        Boolean selectivityEstimationIncludingSkewedDefault = Boolean.parseBoolean(selectivityEstimationString);

        cc.setSelectivityEstimationIncludingSkewedDefault(selectivityEstimationIncludingSkewedDefault);
    }

    private void setProjectionPruningEnabled(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        /* check if the optimization to do projection pruning is enabled or not */
        String projectionPruningOptimizationString = PropertyUtil.getCachedDatabaseProperty(lcc,
        Property.PROJECTION_PRUNING_DISABLED);
        // if database property is not set, treat it as false
        Boolean projectionPruningOptimizationDisabled = false;
        try {
            if (projectionPruningOptimizationString != null)
                projectionPruningOptimizationDisabled = Boolean.parseBoolean(projectionPruningOptimizationString);
        } catch (Exception e) {
            // If the property value failed to convert to a boolean, don't throw an error,
            // just use the default setting.
        }
        cc.setProjectionPruningEnabled(!projectionPruningOptimizationDisabled);
    }

    private void setMaxDerivedCNFPredicates(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        // User can specify the maximum number of CNF predicates to derive via the distributive
        // law.  If the calculated number of derived predicates exceeds this value, DNF to CNF
        // conversion is skipped.
        String maxDerivedCNFPredicatesString = PropertyUtil.getCachedDatabaseProperty(lcc, Property.MAX_DERIVED_CNF_PREDICATES);
        int maxDerivedCNFPredicates = CompilerContext.DEFAULT_MAX_DERIVED_CNF_PREDICATES;
        try {
            if (maxDerivedCNFPredicatesString != null)
                maxDerivedCNFPredicates = Integer.parseInt(maxDerivedCNFPredicatesString);
        } catch (Exception e) {
            // If the property value failed to convert to an int, don't throw an error,
            // just use the default setting.
        }
        if (maxDerivedCNFPredicates > MAX_DERIVED_CNF_PREDICATES_MAX_VALUE)
            maxDerivedCNFPredicates = MAX_DERIVED_CNF_PREDICATES_MAX_VALUE;
        cc.setMaxDerivedCNFPredicates(maxDerivedCNFPredicates);
    }

    private void setMaxMulticolumnProbeValues(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        // User can specify the max length of multicolumn IN list the optimizer may build for
        // use as a probe predicate.  Single-column IN lists can be combined up until the point
        // where adding in the next IN predicate would push us over the limit.
        String maxMulticolumnProbeValuesString = PropertyUtil.getCachedDatabaseProperty(lcc, Property.MAX_MULTICOLUMN_PROBE_VALUES);
        int maxMulticolumnProbeValues = CompilerContext.DEFAULT_MAX_MULTICOLUMN_PROBE_VALUES;
        try {
            if (maxMulticolumnProbeValuesString != null)
                maxMulticolumnProbeValues = Integer.parseInt(maxMulticolumnProbeValuesString);
        } catch (Exception e) {
            // If the property value failed to convert to an int, don't throw an error,
            // just use the default setting.
        }
        if (maxMulticolumnProbeValues > MAX_MULTICOLUMN_PROBE_VALUES_MAX_VALUE)
            maxMulticolumnProbeValues = MAX_MULTICOLUMN_PROBE_VALUES_MAX_VALUE;
        cc.setMaxMulticolumnProbeValues(maxMulticolumnProbeValues);
    }

    private void setMulticolumnInlistProbeOnSparkEnabled(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String property = Property.MULTICOLUMN_INLIST_PROBE_ON_SPARK_ENABLED;
        boolean defaultValue = CompilerContext.DEFAULT_MULTICOLUMN_INLIST_PROBE_ON_SPARK_ENABLED;
        boolean multicolumnInlistProbeOnSparkEnabled = getBooleanParam(lcc, property, defaultValue);
        cc.setMulticolumnInlistProbeOnSparkEnabled(multicolumnInlistProbeOnSparkEnabled);
    }

    private boolean getBooleanParam(LanguageConnectionContext lcc, GlobalDBProperties.PropertyType property,
                                    boolean defaultValue) throws StandardException {
        return getBooleanParam(lcc, property.getName(), defaultValue);

    }
    private boolean getBooleanParam(LanguageConnectionContext lcc, String property, boolean defaultValue) throws StandardException {
        String paramString = PropertyUtil.getCachedDatabaseProperty(lcc, property);
        boolean value = defaultValue;
        try {
            if (paramString != null)
                value = Boolean.parseBoolean(paramString);
        } catch (Exception e) {
            // If the property value failed to convert to a boolean, don't throw an error,
            // just use the default setting.
        }
        return value;
    }

    private void setConvertMultiColumnDNFPredicatesToInList(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.CONVERT_MULTICOLUMN_DNF_PREDICATES_TO_INLIST, CompilerContext.DEFAULT_CONVERT_MULTICOLUMN_DNF_PREDICATES_TO_INLIST);
        cc.setConvertMultiColumnDNFPredicatesToInList(param);
    }

    private void setDisablePredicateSimplification(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.DISABLE_PREDICATE_SIMPLIFICATION, CompilerContext.DEFAULT_DISABLE_PREDICATE_SIMPLIFICATION);
        cc.setDisablePredicateSimplification(param);
    }

    private void setDisableConstantFolding(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.DISABLE_CONSTANT_FOLDING, CompilerContext.DEFAULT_DISABLE_CONSTANT_FOLDING);
        cc.setDisablePredicateSimplification(param);
    }

    private void setNativeSparkAggregationMode(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String nativeSparkAggregationModeString = PropertyUtil.getCachedDatabaseProperty(lcc, Property.SPLICE_NATIVE_SPARK_AGGREGATION_MODE);
        CompilerContext.NativeSparkModeType nativeSparkAggregationMode = CompilerContext.DEFAULT_SPLICE_NATIVE_SPARK_AGGREGATION_MODE;
        try {
            if (nativeSparkAggregationModeString != null) {
                nativeSparkAggregationModeString = nativeSparkAggregationModeString.toLowerCase();
                switch (nativeSparkAggregationModeString) {
                    case "on":
                        nativeSparkAggregationMode = CompilerContext.NativeSparkModeType.ON;
                        break;
                    case "off":
                        nativeSparkAggregationMode = CompilerContext.NativeSparkModeType.OFF;
                        break;
                    case "forced":
                        nativeSparkAggregationMode = CompilerContext.NativeSparkModeType.FORCED;
                        break;
                    default:
                        // use default value
                        break;
                }
            }
        } catch (Exception e) {
            // If the property value failed to get decoded to a valid value, don't throw an error,
            // just use the default setting.
        }
        cc.setNativeSparkAggregationMode(nativeSparkAggregationMode);
    }

    private void setAllowOverflowSensitiveNativeSparkExpressions(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.SPLICE_ALLOW_OVERFLOW_SENSITIVE_NATIVE_SPARK_EXPRESSIONS, CompilerContext.DEFAULT_SPLICE_ALLOW_OVERFLOW_SENSITIVE_NATIVE_SPARK_EXPRESSIONS);
        cc.setAllowOverflowSensitiveNativeSparkExpressions(param);
    }

    private void setNewMergeJoin(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String newMergeJoinString =
        PropertyUtil.getCached(lcc, GlobalDBProperties.SPLICE_NEW_MERGE_JOIN);
        CompilerContext.NewMergeJoinExecutionType newMergeJoin =
        CompilerContext.DEFAULT_SPLICE_NEW_MERGE_JOIN;
        try {
            if (newMergeJoinString != null) {
                newMergeJoinString = newMergeJoinString.toLowerCase();
                if (newMergeJoinString.equalsIgnoreCase("on"))
                    newMergeJoin = CompilerContext.NewMergeJoinExecutionType.ON;
                else if (newMergeJoinString.equalsIgnoreCase("off"))
                    newMergeJoin = CompilerContext.NewMergeJoinExecutionType.OFF;
                else if (newMergeJoinString.equalsIgnoreCase("forced"))
                    newMergeJoin = CompilerContext.NewMergeJoinExecutionType.FORCED;
            }
        } catch (Exception e) {
            // If the property value failed to get decoded to a valid value, don't throw an error,
            // just use the default setting.
        }
        cc.setNewMergeJoin(newMergeJoin);
    }

    private void setDisablePrefixIteratorMode(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.DISABLE_INDEX_PREFIX_ITERATION, CompilerContext.DEFAULT_DISABLE_INDEX_PREFIX_ITERATION);
        cc.setDisablePrefixIteratorMode(param);
    }

    private void setDisableSubqueryFlattening(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.DISABLE_SUBQUERY_FLATTENING, CompilerContext.DEFAULT_DISABLE_SUBQUERY_FLATTENING);
        cc.setDisableSubqueryFlattening(param);
    }          

    private void setDisableParallelTaskJoinCosting(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, GlobalDBProperties.DISABLE_PARALLEL_TASKS_JOIN_COSTING,
                CompilerContext.DEFAULT_DISABLE_PARALLEL_TASKS_JOIN_COSTING);
        cc.setDisablePerParallelTaskJoinCosting(param);
    }

    private void setDisableUnionedIndexScans(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.DISABLE_UNIONED_INDEX_SCANS, CompilerContext.DEFAULT_DISABLE_UNIONED_INDEX_SCANS);
        cc.setDisableUnionedIndexScans(param);
    }

    private void setFavorUnionedIndexScans(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        boolean param = getBooleanParam(lcc, Property.FAVOR_UNIONED_INDEX_SCANS, CompilerContext.DEFAULT_FAVOR_UNIONED_INDEX_SCANS);
        cc.setFavorUnionedIndexScans(param);
    }

    private void setCurrentTimestampPrecision(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String currentTimestampPrecisionString =
                PropertyUtil.getCached(lcc, GlobalDBProperties.SPLICE_CURRENT_TIMESTAMP_PRECISION);
        int currentTimestampPrecision = CompilerContext.DEFAULT_SPLICE_CURRENT_TIMESTAMP_PRECISION;
        try {
            if (currentTimestampPrecisionString != null)
                currentTimestampPrecision = Integer.parseInt(currentTimestampPrecisionString);
            if (currentTimestampPrecision < 0)
                currentTimestampPrecision = 0;
            if (currentTimestampPrecision > 6)
                currentTimestampPrecision = 6;
        } catch (Exception e) {
            // If the property value failed to convert to a boolean, don't throw an error,
            // just use the default setting.
        }
        cc.setCurrentTimestampPrecision(currentTimestampPrecision);
    }

    private void setTimestampFormat(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String timestampFormatString = PropertyUtil.getCached(lcc, GlobalDBProperties.SPLICE_TIMESTAMP_FORMAT );
        if(timestampFormatString == null)
            cc.setTimestampFormat(CompilerContext.DEFAULT_TIMESTAMP_FORMAT);
        else {
            try {
                // the following code checks if the timestampFormatString is valid
                // DB-10968 we shouldn't even allow setting this to the wrong value
                SQLTimestamp.getFormatLength(timestampFormatString);
            } catch(Exception e)
            {
                timestampFormatString = CompilerContext.DEFAULT_TIMESTAMP_FORMAT;
            }
            cc.setTimestampFormat(timestampFormatString);
        }
    }

    private void setCursorUntypedExpressionType(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String type = PropertyUtil.getCachedDatabaseProperty(lcc, Property.CURSOR_UNTYPED_EXPRESSION_TYPE);
        if(type != null && "varchar".equals(type.toLowerCase())) {
            cc.setCursorUntypedExpressionType(DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR, true, 254));
        } else {
            cc.setCursorUntypedExpressionType(null);
        }
    }

    private void setSecondFunctionCompatibilityMode(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String mode =
                PropertyUtil.getCachedDatabaseProperty(lcc, Property.SPLICE_SECOND_FUNCTION_COMPATIBILITY_MODE);
        if (mode == null) {
            cc.setSecondFunctionCompatibilityMode(CompilerContext.DEFAULT_SECOND_FUNCTION_COMPATIBILITY_MODE);
        } else {
            switch (mode.toLowerCase()) {
                case "db2":
                    cc.setSecondFunctionCompatibilityMode(mode);
                    break;
                case "splice":
                default:
                    cc.setSecondFunctionCompatibilityMode(CompilerContext.DEFAULT_SECOND_FUNCTION_COMPATIBILITY_MODE);
                    break;
            }
        }

    }

    private void setFloatingPointNotation(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String floatingPointNotationString =
                PropertyUtil.getCached(lcc, GlobalDBProperties.FLOATING_POINT_NOTATION);
        if(floatingPointNotationString == null) {
            cc.setFloatingPointNotation(CompilerContext.DEFAULT_FLOATING_POINT_NOTATION);
        } else {
            switch (floatingPointNotationString.toLowerCase()) {
                case "plain":
                    cc.setFloatingPointNotation(FloatingPointDataType.PLAIN);
                    break;
                case "normalized":
                    cc.setFloatingPointNotation(FloatingPointDataType.NORMALIZED);
                    break;
                default:
                    cc.setFloatingPointNotation(CompilerContext.DEFAULT_FLOATING_POINT_NOTATION);
            }
        }
    }

    private void setCountReturnType(LanguageConnectionContext lcc, CompilerContext cc) throws StandardException {
        String countReturnTypeString = PropertyUtil.getCachedDatabaseProperty(lcc, Property.COUNT_RETURN_TYPE);
        if(countReturnTypeString == null) {
            cc.setCountReturnType(CompilerContext.DEFAULT_COUNT_RETURN_TYPE);
        } else {
            if (countReturnTypeString.toLowerCase().startsWith("int")) {
                cc.setCountReturnType(Types.INTEGER);
            } else {
                cc.setCountReturnType(Types.BIGINT);
            }
        }
    }

    /*
     * Performs the 4-phase preparation of the statement. The four
     * phases are:
     *
     * 1. parse: Convert the Sql text into an abstract syntax tree (AST)
     * 2. bind: Bind tables and variables. Also performs some error detection (missing tables/columns,etc)
     * 3. optimize: Perform cost-based optimization
     * 4. generate: Generate the actual byte code to be executed
     */
    private StatementNode fourPhasePrepare(LanguageConnectionContext lcc,
                                           Object[] paramDefaults,
                                           long[] timestamps,
                                           boolean foundInCache,
                                           CompilerContext cc,
                                           StatementNode boundAndOptimizedStatement,
                                           boolean cacheMe,
                                           boolean forExplain) throws StandardException{
       lcc.logStartCompiling(getSource());
        long startTime = System.nanoTime();
        try {

            StatementNode qt;
            if (boundAndOptimizedStatement == null) {
                qt = parse(lcc, paramDefaults, timestamps, cc);

                /*
                 ** Tell the data dictionary that we are about to do
                 ** a bunch of "get" operations that must be consistent with
                 ** each other.
                 */

                DataDictionary dataDictionary = lcc.getDataDictionary();
                bindAndOptimize(lcc, timestamps, foundInCache, qt, dataDictionary, cc, cacheMe);
            }
            else {
                lcc.beginNestedTransaction(true);
                qt = boundAndOptimizedStatement;
            }
            /* we need to move the commit of nested sub-transaction
             * after we mark PS valid, during compilation, we might need
             * to get some lock to synchronize with another thread's DDL
             * execution, in particular, the compilation of insert/update/
             * delete vs. create index/constraint (see Beetle 3976).  We
             * can't release such lock until after we mark the PS valid.
             * Otherwise we would just erase the DDL's invalidation when
             * we mark it valid.
             */
            if(!forExplain) {
                generate(lcc, timestamps, cc, qt, boundAndOptimizedStatement != null);
            }

            saveTree(qt, CompilationPhase.AFTER_GENERATE);

            lcc.logEndCompiling(getSource(), System.nanoTime() - startTime);
            return qt;
        } catch (Throwable e) {
            StandardException se = StandardException.getOrWrap(e);
            lcc.logErrorCompiling(getSource(), se, System.nanoTime() - startTime);
            throw se;
        }
        finally{ // for block introduced by pushCompilerContext()
            lcc.resetDB2VarcharCompatibilityMode();
            lcc.setHasJoinStrategyHint(false);
            if (boundAndOptimizedStatement == null)
                lcc.popCompilerContext(cc);
        }
    }

    private void handleExplainNode(LanguageConnectionContext lcc,
                                   CompilerContext cc,
                                   StatementNode statementNode,
                                   boolean cacheMe) throws StandardException {
        if (!(statementNode instanceof ExplainNode) || ((ExplainNode)statementNode).isSparkExplain()) {
            return;
        }

        ExplainNode explainNode = (ExplainNode)statementNode;

        if(explainNode.getExplainedStatementStart() == -1) {
            return;
        }

        String explained = statementText.substring(explainNode.getExplainedStatementStart() + 1).trim();

        GenericStatement queryNode = new GenericStatement(compilationSchema,explained, isForReadOnly /*this could be incorrect with updatableCursors*/, lcc);
        queryNode.sessionPropertyValues = lcc.getCurrentSessionPropertyDelimited();

        if(cacheMe) {
            // similar to ANALYSE statement, EXPLAIN statement will
            // force invalidate the currently cached statement if it exists
            // the assumption here is when running EXPLAIN, the user expects
            // the plan to correspond to what the system currently thinks is
            // the best plan, retrieving it from the cache instead could be
            // misleading to say the least.
            lcc.removeStatement(queryNode);

            // add the statement to the statement cache
            lcc.lookupStatement(queryNode);
        }

        // proceed to optimize and generate code for it
        StatementNode optimizedPlan = queryNode.fourPhasePrepare(lcc, null, new long[5], false, cc, null, cacheMe, true);
        // mark the CC as in use so we use a new CC for the explain plan code generation.
        cc.setCurrentDependent(preparedStmt);
        if(!cc.getInUse()) {
            cc.setInUse(true);
        }
        // plug back the statement in the EXPLAIN plan, so we can proceed
        // with optimizing the EXPLAIN plan. The optimization of EXPLAIN
        // will bypass the underlying node since it is already optimized
        // and the workflow continues as usual with code generation and
        // execution returning the expected output of EXPLAIN.
        // by doing this we achieve two goals:
        // 1. we cache exactly the same execution plan that EXPLAIN
        //    reports back to the user.
        // 2. we only optimize the underlying plan once, guaranteeing
        //    a what-you-see-is-what-you-get next time we attempt to
        //    run the same statement (as long as we don't run something
        //    that invalidates the cache inbetween such as ANALYSE or
        //    SYSCS_EMPTY_STATEMENT_CACHE.
        explainNode.setOptimizedPlanRoot(optimizedPlan);

        // calling bind will create new nested transaction.
        lcc.commitNestedTransaction();
    }

    private StatementNode parse(LanguageConnectionContext lcc,
                                Object[] paramDefaults,
                                long[] timestamps,
                                CompilerContext cc) throws StandardException{
        Parser p=cc.getParser();

        if(preparedStmt == null) {
            preparedStmt = (GenericStorablePreparedStatement)lcc.lookupStatement(this);
        }

        cc.setCurrentDependent(preparedStmt);

        //Only top level statements go through here, nested statement
        //will invoke this method from other places
        StatementNode qt=(StatementNode)p.parseStatement(statementText,paramDefaults);

        timestamps[1]=getCurrentTimeMillis(lcc);

        // Call user-written tree-printer if it exists
        walkAST(lcc,qt, CompilationPhase.AFTER_PARSE);
        saveTree(qt, CompilationPhase.AFTER_PARSE);

        dumpParseTree(lcc,qt,true);
        return qt;
    }

    private void bindAndOptimize(LanguageConnectionContext lcc,
                                 long[] timestamps,
                                 boolean foundInCache,
                                 StatementNode qt,
                                 DataDictionary dataDictionary,
                                 CompilerContext cc,
                                 boolean cacheMe) throws StandardException{
        // start a nested transaction -- all locks acquired by bind
        // and optimize will be released when we end the nested
        // transaction.
        lcc.beginNestedTransaction(true);
        try{
            dumpParseTree(lcc,qt,false);

            handleExplainNode(lcc, cc, qt, cacheMe);

            qt.bindStatement();
            timestamps[2]=getCurrentTimeMillis(lcc);

            // Call user-written tree-printer if it exists
            walkAST(lcc,qt, CompilationPhase.AFTER_BIND);
            saveTree(qt, CompilationPhase.AFTER_BIND);

            dumpBoundTree(lcc,qt);

            maintainCacheEntry(lcc, qt, foundInCache);

            qt.optimizeStatement();
            dumpOptimizedTree(lcc,qt,false);
            timestamps[3]=getCurrentTimeMillis(lcc);

            // Call user-written tree-printer if it exists
            walkAST(lcc,qt, CompilationPhase.AFTER_OPTIMIZE);
            saveTree(qt, CompilationPhase.AFTER_OPTIMIZE);
        }catch(Throwable t){
            lcc.commitNestedTransaction();
            throw StandardException.getOrWrap(t);
        }
    }

    public void maintainCacheEntry(LanguageConnectionContext lcc,
                                   StatementNode statementNode,
                                   boolean foundInCache) throws StandardException {
        //Derby424 - In order to avoid caching select statements referencing
        // any SESSION schema objects (including statements referencing views
        // in SESSION schema), we need to do the SESSION schema object check
        // here.
        //a specific eg for statement referencing a view in SESSION schema
        //CREATE TABLE t28A (c28 int)
        //INSERT INTO t28A VALUES (280),(281)
        //CREATE VIEW SESSION.t28v1 as select * from t28A
        //SELECT * from SESSION.t28v1 should show contents of view and we
        // should not cache this statement because a user can later define
        // a global temporary table with the same name as the view name.
        //Following demonstrates that
        //DECLARE GLOBAL TEMPORARY TABLE SESSION.t28v1(c21 int, c22 int) not
        //     logged
        //INSERT INTO SESSION.t28v1 VALUES (280,1),(281,2)
        //SELECT * from SESSION.t28v1 should show contents of global temporary
        //table and not the view.  Since this select statement was not cached
        // earlier, it will be compiled again and will go to global temporary
        // table to fetch data. This plan will not be cached either because
        // select statement is using SESSION schema object.
        //
        //Following if statement makes sure that if the statement is
        // referencing SESSION schema objects, then we do not want to cache it.
        // We will remove the entry that was made into the cache for
        //this statement at the beginning of the compile phase.
        //The reason we do this check here rather than later in the compile
        // phase is because for a view, later on, we loose the information that
        // it was referencing SESSION schema because the reference
        //view gets replaced with the actual view definition. Right after
        // binding, we still have the information on the view and that is why
        // we do the check here.
        if (preparedStmt.referencesSessionSchema(statementNode) || statementNode.referencesTemporaryTable()) {
            if (foundInCache)
                lcc.removeStatement(this);
        }
        /*
         * If we have EXPLAIN statement, then we should remove it from the
         * cache to prevent future readers from fetching it
         */
        if (foundInCache && statementNode instanceof ExplainNode) {
            lcc.removeStatement(this);
        }
    }

    /**
     * Performs code generation for `qt`.
     * @return the time code generation took in milliseconds.
     */
    private Timestamp generate(LanguageConnectionContext lcc,
                               long[] timestamps,
                               CompilerContext cc,
                               StatementNode qt,
                               boolean outerStatementSharesCompilerContext) throws StandardException{
        Timestamp endTimestamp = null;
        try{        // put in try block, commit sub-transaction if bad
            dumpOptimizedTree(lcc,qt,true);

            ByteArray array=preparedStmt.getByteCodeSaver();
            GeneratedClass ac=qt.generate(array);

            timestamps[4]=getCurrentTimeMillis(lcc);
            /* endTimestamp only meaningful if generateTime is meaningful.
             * generateTime is meaningful if STATISTICS TIMING is ON.
             */
            if(timestamps[4]!=0){
                endTimestamp=new Timestamp(timestamps[4]);
            }

            if(SanityManager.DEBUG){
                if(SanityManager.DEBUG_ON("StopAfterGenerating")){
                    throw StandardException.newException(SQLState.LANG_STOP_AFTER_GENERATING);
                }
            }

            /*
                copy over the compile-time created objects
                to the prepared statement.  This always happens
                at the end of a compile, so there is no need
                to erase the previous entries on a re-compile --
                this erases as it replaces.  Set the activation
                class in case it came from a StorablePreparedStatement
            */
            preparedStmt.setConstantAction(qt.makeConstantAction());
            Object[] savedObjects = cc.getSavedObjects();
            preparedStmt.setSavedObjects(savedObjects);
            if (outerStatementSharesCompilerContext)
                cc.setSavedObjects(savedObjects);
            preparedStmt.setRequiredPermissionsList(cc.getRequiredPermissionsList());
            preparedStmt.incrementVersionCounter();
            preparedStmt.setActivationClass(ac);
            preparedStmt.setNeedsSavepoint(qt.needsSavepoint() ||
                                           TriggerReferencingStruct.isFromTableStatement.get().booleanValue());
            preparedStmt.setCursorInfo((CursorInfo)cc.getCursorInfo());
            preparedStmt.setIsAtomic(qt.isAtomic());
            preparedStmt.setExecuteStatementNameAndSchema(qt.executeStatementName(), qt.executeSchemaName());
            preparedStmt.setSPSName(qt.getSPSName());
            preparedStmt.completeCompile(qt);
            preparedStmt.setCompileTimeWarnings(cc.getWarnings());

        }catch(StandardException e){
            lcc.commitNestedTransaction();
            throw e;
        }
        return endTimestamp;
    }

    private void dumpParseTree(LanguageConnectionContext lcc,StatementNode qt,boolean stopAfter) throws StandardException{
        if(SanityManager.DEBUG){
            if(SanityManager.DEBUG_ON("DumpParseTree")){
                SanityManager.GET_DEBUG_STREAM().print("\n\n============PARSE===========\n\n");
                qt.treePrint();
                lcc.getPrintedObjectsMap().clear();
                SanityManager.GET_DEBUG_STREAM().print("\n\n============END PARSE===========\n\n");
            }

            if(stopAfter && SanityManager.DEBUG_ON("StopAfterParsing")){
                lcc.setLastQueryTree(qt);

                throw StandardException.newException(SQLState.LANG_STOP_AFTER_PARSING);
            }
        }
    }

    private void dumpBoundTree(LanguageConnectionContext lcc,StatementNode qt) throws StandardException{
        if(SanityManager.DEBUG){
            if(SanityManager.DEBUG_ON("DumpBindTree")){
                SanityManager.GET_DEBUG_STREAM().print(
                        "\n\n============BIND===========\n\n");
                qt.treePrint();
                SanityManager.GET_DEBUG_STREAM().print(
                        "\n\n============END BIND===========\n\n");
                lcc.getPrintedObjectsMap().clear();
            }

            if(SanityManager.DEBUG_ON("StopAfterBinding")){
                throw StandardException.newException(SQLState.LANG_STOP_AFTER_BINDING);
            }
        }
    }

    private void dumpOptimizedTree(LanguageConnectionContext lcc,StatementNode qt,boolean stopAfter) throws StandardException{
        if(SanityManager.DEBUG){
            if(SanityManager.DEBUG_ON("DumpOptimizedTree")){
                SanityManager.GET_DEBUG_STREAM().print("\n\n============OPTIMIZED===========\n\n");
                qt.treePrint();
                lcc.getPrintedObjectsMap().clear();
                SanityManager.GET_DEBUG_STREAM().print("\n\n============END OPTIMIZED===========\n\n");
            }

            if(stopAfter && SanityManager.DEBUG_ON("StopAfterOptimizing")){
                throw StandardException.newException(SQLState.LANG_STOP_AFTER_OPTIMIZING);
            }
        }
    }

    /**
     * Walk the AST, using a (user-supplied) Visitor, write tree as JSON file if enabled.
     */
    private void walkAST(LanguageConnectionContext lcc, Visitable queryTree, CompilationPhase phase) throws StandardException {
        ASTVisitor visitor = lcc.getASTVisitor();
        if (visitor != null) {
            try {
                visitor.begin(statementText, phase);
                queryTree.accept(visitor);
            } finally {
                visitor.end(phase);
            }
        }
    }

    /**
     * Saves AST tree as JSON in files (in working directory for now) for each phase.  This is of course intended to be
     * used only by splice developers.
     *
     * AFTER_PARSE.json
     * AFTER_BIND.json
     * AFTER_OPTIMIZE.json
     * AFTER_GENERATE.json
     *
     * View using ast-visualization.html
     */
    @SuppressFBWarnings(value = "LI_LAZY_INIT_STATIC", justification = "used only for development")
    private void saveTree(Visitable queryTree, CompilationPhase phase) throws StandardException {
        if (JSON_TREE_LOG.isTraceEnabled()) {
            JSON_TREE_LOG.warn("JSON AST logging is enabled");
            try {
                if (actualJsonIncrement==-1)
                    actualJsonIncrement = jsonIncrement.getAndIncrement();
                JsonTreeBuilderVisitor jsonVisitor = new JsonTreeBuilderVisitor();
                queryTree.accept(jsonVisitor);
                writeJSON(jsonVisitor,phase.toString() + actualJsonIncrement+".json");
                if (phase == CompilationPhase.AFTER_PARSE)
                    Files.write(getTargePath("sql"+actualJsonIncrement+".txt"),statementText.getBytes("UTF-8"));
            } catch (IOException e) {
                /* Don't let the exception propagate.  If we are trying to use this tool on a server where we can't
                   write to the destination, for example, then warn but let the query run. */
                JSON_TREE_LOG.warn("unable to save AST JSON file", e);
            }
        }
    }

    /**
     *
     * Write Results to target directory
     *
     * @param jsonVisitor
     * @param destinationFileName
     * @throws IOException
     */
    private void writeJSON(JsonTreeBuilderVisitor jsonVisitor,String destinationFileName) throws IOException {
        Path target = getTargePath(destinationFileName);
        Files.write(target, jsonVisitor.toJson().getBytes("UTF-8"));
    }

    /**
     *
     * Get target directory path for debugging
     *
     * @param destinationFileName
     * @return
     * @throws IOException
     */
    private Path getTargePath(String destinationFileName) throws IOException {
        Path target = Paths.get(destinationFileName);
        // Attempt to write to target director, if exists under CWD
        Path subDir = Paths.get("./target");
        if (Files.isDirectory(subDir)) {
            target = subDir.resolve(target);
        }
        return target;
    }

    private String filterComment(String stmtText, LanguageConnectionContext lcc) throws StandardException {
        if (stmtText == null)
            return null;
        CommentStripper commentStripper = lcc.getCommentStripper();
        return commentStripper.stripStatement(stmtText);
    }

    public void setPreparedStmt(GenericStorablePreparedStatement stmt) { preparedStmt = stmt; }
}
