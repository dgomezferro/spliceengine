package com.splicemachine.db.impl.sql.catalog;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.splicemachine.db.catalog.AliasInfo;
import com.splicemachine.db.catalog.TypeDescriptor;
import com.splicemachine.db.catalog.UUID;
import com.splicemachine.db.catalog.types.RoutineAliasInfo;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.Limits;
import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.services.monitor.ModuleControl;
import com.splicemachine.db.iapi.services.monitor.Monitor;
import com.splicemachine.db.iapi.sql.dictionary.AliasDescriptor;
import com.splicemachine.db.iapi.sql.dictionary.DataDictionary;
import com.splicemachine.db.iapi.sql.dictionary.SchemaDescriptor;
import com.splicemachine.db.iapi.store.access.TransactionController;
import com.splicemachine.db.iapi.types.DataTypeDescriptor;

/**
 * @author Scott Fines
 *         Created on: 2/22/13
 */
public class DefaultSystemProcedureGenerator implements SystemProcedureGenerator,ModuleControl {
    private static final String SYSTEM_PROCEDURES = "com.splicemachine.db.catalog.SystemProcedures";
    private static final String LOB_STORED_PROCEDURE = "com.splicemachine.db.impl.jdbc.LOBStoredProcedure";

    private static final DataTypeDescriptor TYPE_SYSTEM_IDENTIFIER =
            DataTypeDescriptor.getBuiltInDataTypeDescriptor(
                    Types.VARCHAR, false, 128);

    private static final TypeDescriptor CATALOG_TYPE_SYSTEM_IDENTIFIER =
            TYPE_SYSTEM_IDENTIFIER.getCatalogType();

    private DataDictionary dictionary;

    public DefaultSystemProcedureGenerator(DataDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public void boot(boolean create, Properties properties) throws StandardException{
        dictionary = (DataDictionary) Monitor.findSystemModule(DataDictionary.MODULE);
    }

    public void stop() {
        //no-op
    }


    protected static final int SYSIBM_PROCEDURES = 1;
    protected static final int SYSCS_PROCEDURES = 2;
    protected static final int SQLJ_PROCEDURES = 3;

//    @Override
    public final void createProcedures(TransactionController tc, HashSet newlyCreatedRoutines) throws StandardException {
        //get system procedures
        Map/*<UUID,Procedure>*/ procedureMap = getProcedures(dictionary,tc);
        Iterator uuidIterator = procedureMap.keySet().iterator();
        while(uuidIterator.hasNext()){
            UUID uuid = (UUID)uuidIterator.next();
            Iterator/*<Procedure>*/ procedures = ((List) procedureMap.get(uuid)).iterator();
            while(procedures.hasNext()){
                Object n = procedures.next();
                //free null check plus cast protection
                if(n instanceof Procedure){
                    Procedure procedure = (Procedure)n;
                    newlyCreatedRoutines.add(procedure.createSystemProcedure(uuid,dictionary,tc));
                }
            }
        }
    }

    /**
     * Create a system stored procedure.
     * PLEASE NOTE:
     * This method is currently not used, but will be used when Splice Machine has a SYS_DEBUG schema available
     * with tools to debug and repair databases and data dictionaries.
     *
     * @param schemaName	name of the system schema
     * @param procName		name of the system stored procedure
     * @param tc			TransactionController to use
     * @param newlyCreatedRoutines	set of newly created routines
     * @throws StandardException
     */
    public final void createProcedure(
    	String schemaName,
    	String procName,
    	TransactionController tc,
    	HashSet newlyCreatedRoutines) throws StandardException {

    	if (schemaName == null || procName == null) {
    		throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "PROCEDURE", (schemaName + "." + procName));
    	}

    	// Upper the case since Derby is case sensitive by default.
    	schemaName = schemaName.toUpperCase();
    	procName = procName.toUpperCase();

    	SchemaDescriptor sd = dictionary.getSchemaDescriptor(schemaName, tc, true);  // Throws an exception if the schema does not exist.
    	UUID schemaId = sd.getUUID();

    	// Do not check for an existing procedure.  We want stuff to blow up.

    	// Find the definition of the procedure and create it.
    	Procedure procedure = findProcedure(schemaId, procName, tc);
    	if (procedure == null) {
    		throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "PROCEDURE", (schemaName + "." + procName));
    	} else {
    		newlyCreatedRoutines.add(procedure.createSystemProcedure(schemaId, dictionary, tc));
    	}
    }

    /**
     * Drop a system stored procedure.
     * PLEASE NOTE:
     * This method is currently not used, but will be used when Splice Machine has a SYS_DEBUG schema available
     * with tools to debug and repair databases and data dictionaries.
     *
     * @param schemaName	name of the system schema
     * @param procName		name of the system stored procedure
     * @param tc			TransactionController to use
     * @param newlyCreatedRoutines	set of newly created routines
     * @throws StandardException
     */
    public final void dropProcedure(
    	String schemaName,
    	String procName,
    	TransactionController tc,
    	HashSet newlyCreatedRoutines) throws StandardException {

    	if (schemaName == null || procName == null) {
    		throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "PROCEDURE", (schemaName + "." + procName));
    	}

    	// Upper the case since Derby is case sensitive by default.
    	schemaName = schemaName.toUpperCase();
    	procName = procName.toUpperCase();

    	SchemaDescriptor sd = dictionary.getSchemaDescriptor(schemaName, tc, true);  // Throws an exception if the schema does not exist.
    	UUID schemaId = sd.getUUID();

    	// Check for existing procedure
    	String schemaIdStr = schemaId.toString();
    	AliasDescriptor ad = dictionary.getAliasDescriptor(schemaIdStr, procName, AliasInfo.ALIAS_NAME_SPACE_PROCEDURE_AS_CHAR);
    	if (ad != null) {  // Drop the procedure if it already exists.
    		dictionary.dropAliasDescriptor(ad, tc);
    	}
    }

    /**
	 * Creates or updates a system stored procedure. If the system stored procedure
	 * already exists in the data dictionary, the stored procedure will be dropped
	 * and then created again. This includes functions implemented as
	 * stored procedures.
	 * 
	 * @param schemaName           the schema where the procedure does and/or will reside
	 * @param procName             the procedure to create or update
	 * @param tc                   the xact
	 * @param newlyCreatedRoutines set of newly created procedures
	 * @throws StandardException
	 */
    public final void createOrUpdateProcedure(
    		String schemaName,
    		String procName,
    		TransactionController tc,
    		HashSet newlyCreatedRoutines) throws StandardException {

    	if (schemaName == null || procName == null) {
    		throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "PROCEDURE", (schemaName + "." + procName));
    	}

    	// Upper the case since Derby is case sensitive by default.
    	schemaName = schemaName.toUpperCase();
    	procName = procName.toUpperCase();

    	// Delete the procedure from SYSALIASES if it already exists.
    	SchemaDescriptor sd = dictionary.getSchemaDescriptor(schemaName, tc, true);  // Throws an exception if the schema does not exist.
    	UUID schemaId = sd.getUUID();

       	// Check for existing procedure
    	String schemaIdStr = schemaId.toString();
    	AliasDescriptor ad = dictionary.getAliasDescriptor(schemaIdStr, procName, AliasInfo.ALIAS_NAME_SPACE_PROCEDURE_AS_CHAR);
    	if (ad == null) {
    		// If not found check for existing function
        	ad = dictionary.getAliasDescriptor(schemaIdStr, procName, AliasInfo.ALIAS_NAME_SPACE_FUNCTION_AS_CHAR);
    	}
    	if (ad != null) {  // Drop the procedure if it already exists.
    		dictionary.dropAliasDescriptor(ad, tc);
    	}

    	// Find the definition of the procedure and create it.
    	Procedure procedure = findProcedure(schemaId, procName, tc);
    	if (procedure == null) {
    		throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "PROCEDURE", (schemaName + "." + procName));
    	} else {
    		newlyCreatedRoutines.add(procedure.createSystemProcedure(schemaId, dictionary, tc));
    	}
    }

    /**
	 * Create or update all system stored procedures.  If the system stored procedure already exists in the data dictionary,
	 * the stored procedure will be dropped and then created again.
	 * 
	 * @param schemaName           the schema where the procedures do and/or will reside
	 * @param tc                   the xact
	 * @param newlyCreatedRoutines set of newly created procedures
	 * @throws StandardException
	 */
    public final void createOrUpdateAllProcedures(
    		String schemaName,
    		TransactionController tc,
    		HashSet newlyCreatedRoutines) throws StandardException {

    	if (schemaName == null) {
    		throw StandardException.newException(SQLState.LANG_OBJECT_NOT_FOUND_DURING_EXECUTION, "SCHEMA", (schemaName));
    	}

    	List procedureList = findProceduresForSchema(schemaName, tc);
    	if (procedureList == null) {
    		throw StandardException.newException(SQLState.PROPERTY_INVALID_VALUE, "SCHEMA", (schemaName));
    	}
    	Iterator/*<Procedure>*/ procedures = procedureList.iterator();
    	while (procedures.hasNext()) {
    		Object n = procedures.next();
    		if (n instanceof Procedure) {
    			Procedure procedure = (Procedure)n;
    			createOrUpdateProcedure(schemaName, procedure.getName(), tc, newlyCreatedRoutines);
    		}
    	}
    }

    /**
     * Find a list of system procedures for the specified schema that has been defined in this class.
     *
     * @param schemaName  name of the schema
     * @param tc          the transaction
     * @return            the system stored procedure if found, otherwise, null is returned
     * @throws StandardException
     */
    protected List findProceduresForSchema(String schemaName, TransactionController tc) throws StandardException {

    	if (schemaName == null || tc == null) {
    		return null;
    	}

    	SchemaDescriptor sd = dictionary.getSchemaDescriptor(schemaName, tc, true);  // Throws an exception if the schema does not exist.
    	UUID schemaId = sd.getUUID();
    	Map/*<UUID, List<Procedure>>*/ procedureMap = getProcedures(dictionary, tc);
    	if (procedureMap == null) {
    		return null;
    	}
    	return ((List) procedureMap.get(schemaId));
    }

    /**
     * Find a system procedure that has been defined in this class.
     *
     * @param schemaId  ID of the schema
     * @param procName  name of the system stored procedure
     * @param tc        the transaction
     * @return  the system stored procedure if found, otherwise, null is returned
     * @throws StandardException
     */
    protected Procedure findProcedure(UUID schemaId, String procName, TransactionController tc) throws StandardException {

    	if (schemaId == null || procName == null || tc == null) {
    		return null;
    	}
    	Map/*<UUID, List<Procedure>>*/ procedureMap = getProcedures(dictionary, tc);
    	if (procedureMap == null) {
    		return null;
    	}
    	List procedureList = (List) procedureMap.get(schemaId);
    	if (procedureList == null) {
    		return null;
    	}

    	Iterator/*<Procedure>*/ procedures = procedureList.iterator();
    	while (procedures.hasNext()) {
    		Object n = procedures.next();
    		if (n instanceof Procedure) {
    			Procedure procedure = (Procedure)n;
    			if (procName.equalsIgnoreCase(procedure.getName())) {
    				return procedure;
    			}
    		}
    	}
    	return null;
    }

    protected Map/*<UUID,List<Procedure>>*/ getProcedures(DataDictionary dictionary,TransactionController tc) throws StandardException {
        Map/*<UUID,Procedure>*/ procedures = new HashMap/*<UUID,Procedure>*/();

        // SYSIBM schema
        UUID sysIbmUUID = dictionary.getSysIBMSchemaDescriptor().getUUID();
        procedures.put(sysIbmUUID,sysIbmProcedures);

        // SQL schema
        UUID sqlJUUID = dictionary.getSchemaDescriptor(
                SchemaDescriptor.STD_SQLJ_SCHEMA_NAME,tc,true).getUUID();
        procedures.put(sqlJUUID,sqlJProcedures);

        // SYSCS schema
        UUID sysUUID = dictionary.getSystemUtilSchemaDescriptor().getUUID();
        procedures.put(sysUUID,sysCsProcedures);

        // Splice fork: hook added for need to add SYSFUN to the set of schemas
        // expected to have stored procedures. It would be better if this Derby
        // method didn't create a HashMap like this every time, but we can
        // refactor to address this later.
        augmentProcedureMap(dictionary, procedures);
        
        return procedures;
    }

    /**
     * Hook to be invoked only from {@link #getProcedures(DataDictionary, TransactionController)}
     * Do not call directly.
     */
    protected void augmentProcedureMap(DataDictionary dictionary, Map procedures) throws StandardException {
        // No op at Derby layer. See Splice override.
    }
    
    private static final List/*<Procedure>*/ sysCsProcedures = new ArrayList/*<Procedure>*/(Arrays.asList(new Procedure[]{
            Procedure.newBuilder().name("SYSCS_SET_DATABASE_PROPERTY").numOutputParams(0).numResultSets(0)
                    .sqlControl(RoutineAliasInfo.MODIFIES_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("KEY")
                    .varchar("VALUE", Limits.DB2_VARCHAR_MAXWIDTH).build()
            ,
            Procedure.newBuilder().name("SYSCS_GET_DATABASE_PROPERTY").numOutputParams(0).numResultSets(0)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA)
                    .returnType(DataTypeDescriptor.getCatalogType(Types.VARCHAR,Limits.DB2_VARCHAR_MAXWIDTH))
                    .isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("KEY").build()
            ,
            Procedure.newBuilder().name("SYSCS_EMPTY_STATEMENT_CACHE")
                .numOutputParams(0).numResultSets(0).sqlControl(RoutineAliasInfo.NO_SQL)
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_CREATE_USER")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("userName")
                .varchar("password",32672)
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_RESET_PASSWORD")
                .numResultSets(0).numOutputParams(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("userName")
                .varchar("password",32672)
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_MODIFY_PASSWORD")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .varchar("password",32672)
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_DROP_USER")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("userName")
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_PEEK_AT_SEQUENCE")
                .numOutputParams(0).numResultSets(0).readsSqlData()
                .returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .isDeterministic(false).ownerClass(SYSTEM_PROCEDURES)
                .catalog("schemaName")
                .catalog("sequenceName")
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_RECOMPILE_INVALID_STORED_STATEMENTS")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .build()
                ,
            Procedure.newBuilder().name("SYSCS_INVALIDATE_STORED_STATEMENTS")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .build()
                ,
            Procedure.newBuilder().name("SYSCS_UPDATE_METADATA_STORED_STATEMENTS")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .build()
                ,
            Procedure.newBuilder().name("SYSCS_UPDATE_SYSTEM_PROCEDURE")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("schemaName")
                .catalog("procName")
                .build()
                ,
                /*
                 * PLEASE NOTE:
                 * This method is currently not used, but will be used and moved when Splice Machine has a SYS_DEBUG schema available
                 * with tools to debug and repair databases and data dictionaries.
                 */
//                Procedure.newBuilder().name("SYSCS_CREATE_SYSTEM_PROCEDURE")
//                .numOutputParams(0).numResultSets(0).modifiesSql()
//                .returnType(null).isDeterministic(false)
//                .ownerClass(SYSTEM_PROCEDURES)
//                .catalog("schemaName")
//                .catalog("procName")
//                .build()
//                ,
                /*
                 * PLEASE NOTE:
                 * This method is currently not used, but will be used and moved when Splice Machine has a SYS_DEBUG schema available
                 * with tools to debug and repair databases and data dictionaries.
                 */
//                Procedure.newBuilder().name("SYSCS_DROP_SYSTEM_PROCEDURE")
//                .numOutputParams(0).numResultSets(0).modifiesSql()
//                .returnType(null).isDeterministic(false)
//                .ownerClass(SYSTEM_PROCEDURES)
//                .catalog("schemaName")
//                .catalog("procName")
//                .build()
//                ,
            Procedure.newBuilder().name("SYSCS_UPDATE_ALL_SYSTEM_PROCEDURES")
                .numOutputParams(0).numResultSets(0).modifiesSql()
                .returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .build()
            ,
            Procedure.newBuilder().name("SYSCS_BACKUP_DATABASE")
                    .numOutputParams(0).numResultSets(0).modifiesSql()
                    .returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .varchar("directory",32672)
                    .build()
            ,
            Procedure.newBuilder().name("SYSCS_RESTORE_DATABASE")
                    .numOutputParams(0).numResultSets(0).modifiesSql()
                    .returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .varchar("directory",32672)
                    .build()
    }));

    private static final List/*<Procedure>*/ sqlJProcedures = Arrays.asList(new Procedure[]{
    		Procedure.newBuilder().name("INSTALL_JAR").numOutputParams(0).numResultSets(0)
            	.sqlControl(RoutineAliasInfo.MODIFIES_SQL_DATA).returnType(null).isDeterministic(false)
            	.ownerClass(SYSTEM_PROCEDURES)
            	.varchar("URL",256)
            	.catalog("JAR")
            	.integer("DEPLOY").build()
            ,
            Procedure.newBuilder().name("REPLACE_JAR").numOutputParams(0).numResultSets(0)
                .sqlControl(RoutineAliasInfo.MODIFIES_SQL_DATA).returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .varchar("URL",256)
                .catalog("JAR").build()
            ,
            Procedure.newBuilder().name("REMOVE_JAR").numOutputParams(0).numResultSets(0)
                .sqlControl(RoutineAliasInfo.MODIFIES_SQL_DATA).returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("JAR")
                .integer("UNDEPLOY").build()
    });

    private static final List/*<Procedure>*/ sysIbmProcedures = Arrays.asList(new Procedure[]{
            Procedure.newBuilder().name("SQLCAMESSAGE").numOutputParams(2).numResultSets(0)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .integer("SQLCODE")
                    .smallint("ERRMCLEN")
                    .varchar("SQLERRMC", Limits.DB2_JCC_MAX_EXCEPTION_PARAM_LENGTH)
                    .charType("SQLERRP", 8)
                    .integer("SQLERRD0")
                    .integer("SQLERRD1")
                    .integer("SQLERRD2")
                    .integer("SQLERRD3")
                    .integer("SQLERRD4")
                    .integer("SQLERRD5")
                    .charType("SQLWARN",11)
                    .charType("SQLSTATE",5)
                    .varchar("FILE",50)
                    .charType("LOCALE",5)
                    .varchar("MESSAGE",2400)
                    .integer("RETURNCODE").build()
            ,
            Procedure.newBuilder().name("SQLPROCEDURES").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("PROCNAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLTABLEPRIVILEGES").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLPRIMARYKEYS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLTABLES").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME")
                    .varchar("TABLETYPE",4000)
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLPROCEDURECOLS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("PROCNAME")
                    .catalog("PARAMNAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLCOLUMNS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME")
                    .catalog("COLUMNNAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLCOLPRIVILEGES").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME")
                    .catalog("COLUMNNAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLUDTS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TYPENAMEPATTERN")
                    .catalog("UDTTYPES")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLFOREIGNKEYS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("PKCATALOGNAME")
                    .catalog("PKSCHEMANAME")
                    .catalog("PKTABLENAME")
                    .catalog("FKCATALOGNAME")
                    .catalog("FKSCHEMANAME")
                    .catalog("FKTABLENAME")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLSPECIALCOLUMNS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .smallint("COLTYPE")
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME" )
                    .smallint("SCOPE")
                    .smallint("NULLABLE")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLGETTYPEINFO").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .smallint("DATATYPE")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLSTATISTICS").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES)
                    .catalog("CATALOGNAME")
                    .catalog("SCHEMANAME")
                    .catalog("TABLENAME")
                    .smallint("UNIQUE")
                    .smallint("APPROXIMATE")
                    .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("METADATA").numOutputParams(0).numResultSets(1)
                    .sqlControl(RoutineAliasInfo.READS_SQL_DATA).returnType(null).isDeterministic(false)
                    .ownerClass(SYSTEM_PROCEDURES).build()
            ,
            Procedure.newBuilder().name("SQLFUNCTIONS").numOutputParams(0).numResultSets(1)
                .readsSqlData().returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("CATALOGNAME")
                .catalog("SCHEMANAME")
                .catalog("FUNCNAME")
                .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("SQLFUNCTIONPARAMS").numOutputParams(0).numResultSets(1)
                .readsSqlData().returnType(null).isDeterministic(false)
                .ownerClass(SYSTEM_PROCEDURES)
                .catalog("CATALOGNAME")
                .catalog("SCHEMANAME")
                .catalog("FUNCNAME")
                .catalog("PARAMNAME")
                .varchar("OPTIONS",4000).build()
            ,
            Procedure.newBuilder().name("CLOBCREATELOCATOR").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(TypeDescriptor.INTEGER).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE).build()
            ,
            Procedure.newBuilder().name("CLOBRELEASELOCATOR").numResultSets(0).numOutputParams(0)
                .containsSql().returnType(null).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR").build()
            ,
            Procedure.newBuilder().name("CLOBGETPROSITIONFROMSTRING").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .varchar("SEARCHSTR",Limits.DB2_VARCHAR_MAXWIDTH)
                .bigint("POS").build()
            ,
            Procedure.newBuilder().name("CLOBGETPOSITIONFROMLOCATOR").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .integer("SEARCHLOCATOR")
                .integer("POS").build()
            ,
            Procedure.newBuilder().name("CLOBGETLENGTH").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR").build()
            ,
            Procedure.newBuilder().name("CLOBGETSUBSTRING").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.VARCHAR,Limits.MAX_CLOB_RETURN_LEN))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .bigint("POS")
                .integer("LEN").build()
            ,
            Procedure.newBuilder().name("CLOBSETSTRING").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(null).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .bigint("POS")
                .integer("LEN")
                .varchar("REPLACESTR",Limits.DB2_VARCHAR_MAXWIDTH).build()
            ,
            Procedure.newBuilder().name("CLOBTRUNCATE").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(null).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .bigint("LEN").build()
            ,
            Procedure.newBuilder().name("BLOBCREATELOCATOR").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(TypeDescriptor.INTEGER).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .build()
            ,
            Procedure.newBuilder().name("BLOBRELEASELOCATOR").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(null).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .build()
            ,
            Procedure.newBuilder().name("BLOBGETPOSITIONFROMBYTES").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
            .ownerClass(LOB_STORED_PROCEDURE).isDeterministic(false)
                .integer("LOCATOR")
                .arg("SEARCHBYTES",DataTypeDescriptor.getCatalogType(Types.VARBINARY))
                .bigint("POS")
                .build()
            ,
            Procedure.newBuilder().name("BLOBGETPOSITIONFROMLOCATOR").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .integer("SEARCHLOCATOR")
                .bigint("POS")
                .build()
            ,
            Procedure.newBuilder().name("BLOBGETLENGTH").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.BIGINT))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .build()
            ,
            Procedure.newBuilder().name("BLOBGETBYTES").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(DataTypeDescriptor.getCatalogType(Types.VARBINARY,Limits.MAX_BLOB_RETURN_LEN))
                .isDeterministic(false).ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .bigint("POS")
                .integer("LEN")
                .build()
            ,
            Procedure.newBuilder().name("BLOBSETBYTES").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(null).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .bigint("POS")
                .integer("LEN")
                .arg("REPLACEBYTES",DataTypeDescriptor.getCatalogType(Types.VARBINARY))
                .build()
            ,
            Procedure.newBuilder().name("BLOBTRUNCATE").numOutputParams(0).numResultSets(0)
                .containsSql().returnType(null).isDeterministic(false)
                .ownerClass(LOB_STORED_PROCEDURE)
                .integer("LOCATOR")
                .bigint("POS")
                .build()

    });
}