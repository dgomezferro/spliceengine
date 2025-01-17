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

package com.splicemachine.db.iapi.store.access;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.services.io.Storable;
import com.splicemachine.db.iapi.services.locks.CompatibilitySpace;
import com.splicemachine.db.iapi.services.property.PersistentSet;
import com.splicemachine.db.iapi.sql.dictionary.ConglomerateDescriptor;
import com.splicemachine.db.iapi.sql.dictionary.TableDescriptor;
import com.splicemachine.db.iapi.store.access.conglomerate.Conglomerate;
import com.splicemachine.db.iapi.types.DataValueDescriptor;

import java.sql.Timestamp;
import java.util.Properties;
import java.util.Set;

/**

 The TransactionController interface provides methods that an access client
 can use to control a transaction, which include the methods for
 gaining access to resources (conglomerates, scans, etc.) in the transaction
 controller's storage manager.  TransactionControllers are obtained
 from an AccessFactory via the getTransaction method.
 <P>
 Each transaction controller is associated with a transaction context which
 provides error cleanup when standard exceptions are thrown anywhere in the
 system.  The transaction context performs the following actions in response
 to cleanupOnError:
 <UL>
 <LI>
 If the error is an instance of StandardException that has a severity less
 than ExceptionSeverity.TRANSACTION_SEVERITY all resources remain unaffected.
 <LI>
 If the error is an instance of StandardException that has a severity equal
 to ExceptionSeverity.TRANSACTION_SEVERITY, then all resources are released.  An attempt
 to use any resource obtained from this transaction controller after
 such an error will result in an error.  The transaction controller itself remains
 valid, however.
 <LI>
 If the error is an instance of StandardException that has a severity greater
 than ExceptionSeverity.TRANSACTION_SEVERITY, then all resources are released and the
 context is popped from the stack.  Attempting to use this controller or any
 resources obtained from it will result in an error.
 </UL>
 Transactions are obtained from an AccessFactory.
 @see AccessFactory#getTransaction
 @see com.splicemachine.db.iapi.error.StandardException
 @see PersistentSet


 **/

public interface TransactionController
		extends PersistentSet
{

	/**
	 * Constant used for the lock_level argument to openConglomerate() and
	 * openScan() calls.  Pass in MODE_RECORD if you want the conglomerate
	 * to be opened with record level locking (but the system may override
	 * this choice and provide table level locking instead).
	 **/
	int MODE_RECORD    = 6;
	/**
	 * Constant used for the lock_level argument to openConglomerate() and
	 * openScan() calls.  Pass in MODE_TABLE if you want the conglomerate
	 * to be opened with table level locking - if this mode is passed in the
	 * system will never use record level locking for the open scan or
	 * controller.
	 **/
	int MODE_TABLE     = 7;

	/**
	 * Constants used for the isolation_level argument to openConglomerate() and
	 * openScan() calls.
	 **/

	/**
	 *
	 * No locks are requested for data that is read only.  Uncommitted data
	 * may be returned.  Writes only visible previous to commit.
	 * Exclusive transaction length locks are set on data that is written, no
	 * lock is set on data that is read.  No table level intent lock is held
	 * so it is up to caller to insure that table is not dropped while being
	 * accessed (RESOLVE - this issue may need to be resolved differently if
	 * we can't figure out a non-locked based way to prevent ddl during
	 * read uncommitted access).
	 *
	 * ONLY USED INTERNALLY BY ACCESS, NOT VALID FOR EXTERNAL USERS.
	 **/
	int ISOLATION_NOLOCK = 0;

	/**
	 * No locks are requested for data that is read only.  Uncommitted data
	 * may be returned.  Writes only visible previous to commit.
	 * Exclusive transaction length locks are set on data that is written, no
	 * lock is set on data that is read.  No table level intent lock is held
	 * so it is up to caller to insure that table is not dropped while being
	 * accessed (RESOLVE - this issue may need to be resolved differently if
	 * we can't figure out a non-locked based way to prevent ddl during
	 * read uncommitted access).
	 *
	 * Note that this is currently only supported in heap scans.
	 *
	 * TODO - work in progress to support this locking mode in the 5.1
	 * storage system.
	 **/
	int ISOLATION_READ_UNCOMMITTED = 1;

	/**
	 * No lost updates, no dirty reads, only committed data is returned.
	 * Writes only visible when committed.  Exclusive transaction
	 * length locks are set on data that is written, short term locks (
	 * possibly instantaneous duration locks) are set
	 * on data that is read.
	 **/
	int ISOLATION_READ_COMMITTED = 2;

	/**
	 * No lost updates, no dirty reads, only committed data is returned.
	 * Writes only visible when committed.  Exclusive transaction
	 * length locks are set on data that is written, short term locks (
	 * possibly instantaneous duration locks) are set
	 * on data that is read.  Read locks are requested for "zero" duration,
	 * thus upon return from access no read row lock is held.
	 **/
	int ISOLATION_READ_COMMITTED_NOHOLDLOCK = 3;

	/**
	 * Read and write locks are held until end of transaction, but no
	 * phantom protection is performed (ie no previous key locking).
	 * Writes only visible when committed.
	 *
	 * Note this constant is currently mapped to ISOLATION_SERIALIZABLE.
	 * The constant is provided so that code which only requires repeatable
	 * read can be coded with the right isolation level, and will just work when
	 * store provided real repeatable read isolation.
	 **/
	int ISOLATION_REPEATABLE_READ = 4;

	/**
	 * Gray's isolation degree 3, "Serializable, Repeatable Read".	Note that
	 * some conglomerate implementations may only be able to provide
	 * phantom protection under MODE_TABLE, while others can support this
	 * under MODE_RECORD.
	 **/
	int ISOLATION_SERIALIZABLE = 5;

	/**
	 * Constants used for the flag argument to openConglomerate() and
	 * openScan() calls.
	 *
	 * NOTE - The values of these constants must correspond to their associated
	 * constants in
	 * protocol.Database.Storage.RawStore.Interface.ContainerHandle, do not
	 * add constants to this file without first adding there.
	 **/

	/**
	 * Use this mode to the openScan() call to indicate the scan should get
	 * update locks during scan, and either promote the update locks to
	 * exclusive locks if the row is changed or demote the lock if the row
	 * is not updated.  The lock demotion depends on the isolation level of
	 * the scan.  If isolation level is ISOLATION_SERIALIZABLE or
	 * ISOLATION_REPEATABLE_READ
	 * then the lock will be converted to a read lock.  If the isolation level
	 * ISOLATION_READ_COMMITTED then the lock is released when the scan moves
	 * off the row.
	 * <p>
	 * Note that one must still set OPENMODE_FORUPDATE to be able to change
	 * rows in the scan.  So to enable update locks for an updating scan one
	 * provides (OPENMODE_FORUPDATE | OPENMODE_USE_UPDATE_LOCKS)
	 **/
	int OPENMODE_USE_UPDATE_LOCKS      = 0x00001000;

	/**
	 * Use this mode to the openConglomerate() call which opens the base
	 * table to be used in a index to base row probe.  This will cause
	 * the openConglomerate() call to not get any row locks as part of
	 * it's fetches.
	 * It is important when using this mode that the secondary index table be
	 * successfully opened before opening the base table so that
	 * proper locking protocol is followed.
	 **/
	int OPENMODE_SECONDARY_LOCKED      = 0x00002000;

	/**
	 * Use this mode to the openConglomerate() call used to open the
	 * secondary indices of a table for inserting new rows in the table.
	 * This will let the secondaryindex know that the base row being inserted
	 * has already been locked and only previous key locks need be obtained.
	 *
	 * It is important when using this mode that the base table be
	 * successfully opened before opening the secondaryindex so that
	 * proper locking protocol is followed.
	 **/
	int OPENMODE_BASEROW_INSERT_LOCKED = 0x00004000;

	/**
	 * open table for update, if not specified table will be opened for read.
	 **/
	int OPENMODE_FORUPDATE             = 0x00000004;

	/**
	 * Use this mode to the openConglomerate() call used to just get the
	 * table lock on the conglomerate without actually doing anything else.
	 * Any operations other than close() performed on the "opened" container
	 * will fail.
	 **/
	int OPENMODE_FOR_LOCK_ONLY         = 0x00000040;

	/**
	 * The table lock request will not wait.
	 * <p>
	 * The request to get the table lock (any table lock including intent or
	 * "real" table level lock), will not wait if it can't be granted.   A
	 * lock timeout will be returned.  Note that subsequent row locks will
	 * wait if the application has not set a 0 timeout and if the call does
	 * not have a wait parameter (like OpenConglomerate.fetch().
	 **/
	int OPENMODE_LOCK_NOWAIT           = 0x00000080;

	/**
	 * Constants used for the countOpen() call.
	 **/
	int OPEN_CONGLOMERATE   = 0x01;
	int OPEN_SCAN           = 0x02;
	int OPEN_CREATED_SORTS  = 0x03;
	int OPEN_SORT           = 0x04;
	int OPEN_TOTAL          = 0x05;


	byte IS_DEFAULT	=	(byte) 0x00; // initialize the flag
	byte IS_TEMPORARY	=	(byte) 0x01; // conglom is temporary
	byte IS_KEPT		=	(byte) 0x02; // no auto remove

	int TIME_TRAVEL_OLDEST = -1;
	int TIME_TRAVEL_UNSET  = -2;

	/**************************************************************************
	 * Interfaces previously defined in TcAccessIface:
	 **************************************************************************
	 */

	/**
	 * Get reference to access factory which started this transaction.
	 * <p>
	 *
	 * @return The AccessFactory which started this transaction.
	 **/
	AccessFactory getAccessManager();

	/**
	 Check whether a conglomerate exists.

	 @param  conglomId  The identifier of the conglomerate to check for.

	 @return  true if the conglomerate exists, false otherwise.

	 @exception StandardException   only thrown if something goes
	 wrong in the lower levels.
	 **/
	boolean conglomerateExists(long conglomId)
			throws StandardException;

	/**
	 Create a conglomerate.
	 <p>
	 Currently, only "heap"'s and ""btree secondary index"'s are supported,
	 and all the features are not completely implemented.
	 For now, create conglomerates like this:
	 <p>
	 <blockquote><pre>
	 TransactionController tc;
	 long conglomId = tc.createConglomerate(
	 "heap", // we're requesting a heap conglomerate
	 template, // a populated template is required for heap and btree.
	 null, // no column order
	 null, // default collation order for all columns
	 null, // default properties
	 0); // not temporary
	 </blockquote></pre>

	 Each implementation of a conglomerate takes a possibly different set
	 of properties.  The "heap" implementation currently takes no properties.

	 The "btree secondary index" requires the following set of properties:
	 <UL>
	 <LI> "baseConglomerateId" (integer).  The conglomerate id of the base
	 conglomerate is never actually accessed by the b-tree secondary
	 index implementation, it only serves as a namespace for row locks.
	 This property is required.
	 <LI> "rowLocationColumn" (integer).  The zero-based index into the row which
	 the b-tree secondary index will assume holds a @see RowLocation of
	 the base row in the base conglomerate.  This value will be used
	 for acquiring locks.  In this implementation RowLocationColumn must be
	 the last key column.
	 This property is required.
	 <LI>"allowDuplicates" (boolean).  If set to true the table will allow
	 rows which are duplicate in key column's 0 through (nUniqueColumns - 1).
	 Currently only supports "false".
	 This property is optional, defaults to false.
	 <LI>"nKeyFields"  (integer) Columns 0 through (nKeyFields - 1) will be
	 included in key of the conglomerate.
	 This implementation requires that "nKeyFields" must be the same as the
	 number of fields in the conglomerate, including the rowLocationColumn.
	 Other implementations may relax this restriction to allow non-key fields
	 in the index.
	 This property is required.
	 <LI>"nUniqueColumns" (integer) Columns 0 through "nUniqueColumns" will be
	 used to check for uniqueness.  So for a standard SQL non-unique index
	 implementation set "nUniqueColumns" to the same value as "nKeyFields"; and
	 for a unique index set "nUniqueColumns" to "nKeyFields - 1 (ie. don't
	 include the rowLocationColumn in the uniqueness check).
	 This property is required.
	 <LI>"maintainParentLinks" (boolean)
	 Whether the b-tree pages maintain the page number of their parent.  Only
	 used for consistency checking.  It takes a certain amount more effort to
	 maintain these links, but they're really handy for ensuring that the index
	 is consistent.
	 This property is optional, defaults to true.
	 </UL>

	 A secondary index i (a, b) on table t (a, b, c) would have rows
	 which looked like (a, b, row_location).  baseConglomerateId is set to the
	 conglomerate id of t.  rowLocationColumns is set to 2.  allowsDuplicates
	 would be set to false.  To create a unique
	 secondary index set uniquenessColumns to 2, this means that the btree
	 code will compare the key values but not the row id when determing
	 uniqueness.  To create a nonunique secondary index set uniquenessColumns
	 to 3, this would mean that the uniqueness test would include the row
	 location and since all row locations will be unique  all rows inserted
	 into the index will be differentiated (at least) by row location.

	 @return The identifier to be used to open the conglomerate later.

	 @exception  StandardException  if the conglomerate could
	 not be created for some reason.
	  *
	  * @param implementation Specifies what kind of conglomerate to create.
	THE WAY THAT THE IMPLEMENTATION IS CHOSEN STILL NEEDS SOME WORK.
	For now, use "BTREE" or "heap" for a local access manager.
	 * @param template A row which describes the prototypical
	row that the conglomerate will be holding.
	Typically this row gives the conglomerate
	information about the number and type of
	columns it will be holding.  The implementation
	may require a specific subclass of row type.
	Note that the createConglomerate call reads the template and makes a copy
	of any necessary information from the template, no reference to the
	template is kept (and thus this template can be re-used in subsequent
	calls - such as openScan()).  This field is required when creating either
	a heap or btree conglomerate.
	 * @param columnOrder Specifies the colummns sort order.
	Useful only when the conglomerate is of type BTREE, default
	value is 'null', which means all columns needs to be sorted in
	Ascending order.
	 * @param collationIds Specifies the collation id of each of the columns
	in the new conglomerate.  Collation id along with format id may be used
	to create DataValueDescriptor's which may subsequently be used for
	comparisons.  For instance the correct collation specific order and
	searching is maintained by correctly specifying the collation id of
	the columns in the index when the index is created.
	 * @param properties Implementation-specific properties of the
	conglomerate.
	 * @param  temporaryFlag
	Where temporaryFlag can have the following values:
	IS_DEFAULT		- no bit is set.
	IS_TEMPORARY	- if set, the conglomerate is temporary
	IS_KEPT			- only looked at if IS_TEMPORARY,
	if set, the temporary container is not
	removed automatically by store when
	transaction terminates.

	If IS_TEMPORARY is set, the conglomerate is temporary.
	Temporary conglomerates are only visible through the transaction
	controller that created them.  Otherwise, they are opened,
	scanned, and dropped in the same way as permanent conglomerates.
	Changes to temporary conglomerates persist across commits, but
	temporary conglomerates are truncated on abort (or rollback
	to savepoint).  Updates to temporary conglomerates are not
	locked or logged.

	A temporary conglomerate is only visible to the	transaction
	controller that created it, even if the conglomerate IS_KEPT
	when the transaction termination.

	All temporary conglomerate is removed by store when the
	conglomerate controller is destroyed, or if it is dropped by an explicit
	dropConglomerate.  If Derby reboots, all temporary
	conglomerates are removed.
	 * @param priority*/
	long createConglomerate(
			boolean                 isExternal,
			String                  implementation,
			DataValueDescriptor[]   template,
			ColumnOrdering[]        columnOrder,
			int[]                   keyFormatIds,
			int[]                   collationIds,
			Properties              properties,
			int                     temporaryFlag,
			Conglomerate.Priority priority)
			throws StandardException;

	/**
	 * Creates a conglomerate asynchronously, the caller can use the returned object but they must call {@link Conglomerate#awaitCreation()}
	 * before they can use the newly created, referenced conglomerate
	 */
	Conglomerate createConglomerateAsync(
			boolean                 isExternal,
			String                  implementation,
			DataValueDescriptor[]   template,
			ColumnOrdering[]        columnOrder,
			int[]                   keyFormatIds,
			int[]                   collationIds,
			Properties              properties,
			int                     temporaryFlag,
			byte[][]                splitKeys,
			Conglomerate.Priority priority)
			throws StandardException;

	/** Tags this conglomerate with the transaction Id that dropped it, in order
	 * to resolve whether or not we can VACUUM it later on
	 * @param conglomerateId
	 * @throws StandardException
	 */
	void markConglomerateDropped(
			long conglomerateId)
			throws StandardException;

	/**
	 Create a conglomerate and load (filled) it with rows that comes from the
	 row source without loggging.

	 <p>Individual rows that are loaded into the conglomerate are not
	 logged. After this operation, the underlying database must be backed up
	 with a database backup rather than an transaction log backup (when we have
	 them). This warning is put here for the benefit of future generation.

	 <p>
	 This function behaves the same as @see createConglomerate except it also
	 populates the conglomerate with rows from the row source and the rows that
	 are inserted are not logged.

	 @param implementation Specifies what kind of conglomerate to create.
	 THE WAY THAT THE IMPLEMENTATION IS CHOSEN STILL NEEDS SOME WORK.
	 For now, use "BTREE" or "heap" for a local access manager.

	 @param template A row which describes the prototypical
	 row that the conglomerate will be holding.
	 Typically this row gives the conglomerate
	 information about the number and type of
	 columns it will be holding.  The implementation
	 may require a specific subclass of row type.
	 Note that the createConglomerate call reads the template and makes a copy
	 of any necessary information from the template, no reference to the
	 template is kept (and thus this template can be re-used in subsequent
	 calls - such as openScan()).  This field is required when creating either
	 a heap or btree conglomerate.

	 @param columnOrder Specifies the colummns sort order.
	 Useful only when the conglomerate is of type BTREE, default
	 value is 'null', which means all columns needs to be sorted in
	 Ascending order.

	 @param collationIds Specifies the collation id of each of the columns
	 in the new conglomerate.  Collation id along with format id may be used
	 to create DataValueDescriptor's which may subsequently be used for
	 comparisons.  For instance the correct collation specific order and
	 searching is maintained by correctly specifying the collation id of
	 the columns in the index when the index is created.

	 @param properties Implementation-specific properties of the
	 conglomerate.

	 @param rowSource the interface to recieve rows to load into the
	 conglomerate.

	 @param rowCount - if not null the number of rows loaded into the table
	 will be returned as the first element of the array.

	 @exception StandardException if the conglomerate could not be created or
	 loaded for some reason.  Throws
	 SQLState.STORE_CONGLOMERATE_DUPLICATE_KEY_EXCEPTION if
	 the conglomerate supports uniqueness checks and has been created to
	 disallow duplicates, and one of the rows being loaded had key columns which
	 were duplicate of a row already in the conglomerate.
	 **/
	long createAndLoadConglomerate(
			boolean 				isExternal,
			String                  implementation,
			DataValueDescriptor[]   template,
			ColumnOrdering[]		columnOrder,
			int[]                   keyFormatIds,
			int[]                   collationIds,
			Properties              properties,
			int                     temporaryFlag,
			RowLocationRetRowSource rowSource,
			long[]                  rowCount)
			throws StandardException;

	/**
	 Recreate a conglomerate and possibly load it with new rows that come from
	 the new row source.

	 <p>
	 This function behaves the same as @see createConglomerate except it also
	 populates the conglomerate with rows from the row source and the rows that
	 are inserted are not logged.

	 <p>Individual rows that are loaded into the conglomerate are not
	 logged. After this operation, the underlying database must be backed up
	 with a database backup rather than an transaction log backup (when we have
	 them). This warning is put here for the benefit of future generation.

	 @param implementation Specifies what kind of conglomerate to create.
	 THE WAY THAT THE IMPLEMENTATION IS CHOSEN STILL NEEDS SOME WORK.
	 For now, use "BTREE" or "heap" for a local access manager.

	 @param recreate_ifempty If false, and the rowsource used to load the new
	 conglomerate returns no rows, then the original
	 conglomid will be returned.  To the client it will
	 be as if no call was made.  Underlying
	 implementations may actually create and drop a
	 container.
	 If true, then a new empty container will be
	 created and it's conglomid will be returned.

	 @param template A row which describes the prototypical
	 row that the conglomerate will be holding.
	 Typically this row gives the conglomerate
	 information about the number and type of
	 columns it will be holding.  The implementation
	 may require a specific subclass of row type.
	 Note that the createConglomerate call reads the template and makes a copy
	 of any necessary information from the template, no reference to the
	 template is kept (and thus this template can be re-used in subsequent
	 calls - such as openScan()).  This field is required when creating either
	 a heap or btree conglomerate.

	 @param columnOrder  Specifies the colummns sort order.
	 Useful only when the conglomerate is of type BTREE, default
	 value is 'null', which means all columns needs to be sorted in
	 Ascending order.

	 @param collationIds Specifies the collation id of each of the columns
	 in the new conglomerate.  Collation id along with format id may be used
	 to create DataValueDescriptor's which may subsequently be used for
	 comparisons.  For instance the correct collation specific order and
	 searching is maintained by correctly specifying the collation id of
	 the columns in the index when the index is created.

	 @param properties Implementation-specific properties of the conglomerate.

	 @param  temporaryFlag  If true, the conglomerate is temporary.
	 Temporary conglomerates are only visible through the transaction
	 controller that created them.  Otherwise, they are opened,
	 scanned, and dropped in the same way as permanent conglomerates.
	 Changes to temporary conglomerates persist across commits, but
	 temporary conglomerates are truncated on abort (or rollback
	 to savepoint).  Updates to temporary conglomerates are not
	 locked or logged.

	 @param orig_conglomId The conglomid of the original conglomerate.

	 @param rowSource interface to receive rows to load into the conglomerate.

	 @param rowCount - if not null the number of rows loaded into the table
	 will be returned as the first element of the array.

	 @exception StandardException if the conglomerate could not be created or
	 loaded for some reason.  Throws
	 SQLState.STORE_CONGLOMERATE_DUPLICATE_KEY_EXCEPTION if
	 the conglomerate supports uniqueness checks and has been created to
	 disallow duplicates, and one of the rows being loaded had key columns which
	 were duplicate of a row already in the conglomerate.
	 **/
	long recreateAndLoadConglomerate(
			boolean					isExternal,
			String                  implementation,
			boolean                 recreate_ifempty,
			DataValueDescriptor[]   template,
			ColumnOrdering[]		columnOrder,
			int[]                   keyFormatIds,
			int[]                   collationIds,
			Properties              properties,
			int                     temporaryFlag,
			long                    orig_conglomId,
			RowLocationRetRowSource rowSource,
			long[] rowCount
	)
			throws StandardException;

	/**
	 Add a column to a conglomerate.

	 The Storage system will block this action until it can get an exclusive
	 container level lock on the conglomerate.  The conglomerate must not be
	 open in the current transaction, this means that within the current
	 transaction there must be no open ConglomerateController's or
	 ScanControllers.  It may not be possible in some implementations of the
	 system to catch this error in the store, so it is up to the caller to
	 insure this.

	 The column can only be added at the spot just after the current set of
	 columns.

	 The template_column must be nullable.

	 After this call has been made, all fetches of this column from rows that
	 existed in the table prior to this call will return "null".

	 @param conglomId        The identifier of the conglomerate to alter.
	 @param column_id        The column number to add this column at.
	 @param template_column  An instance of the column to be added to table.
	 @param collation_id     Collation id of the added column.

	 @exception StandardException Only some types of conglomerates can support
	 adding a column, for instance "heap" conglomerates support adding a
	 column while "btree" conglomerates do not.  If the column can not be
	 added an exception will be thrown.
	 **/
	void addColumnToConglomerate(
			long conglomId,
			int column_id,
			Storable template_column,
			int collation_id)
			throws StandardException;



	/**
	 Remove a column from the conglomerate.

	 @param conglomId        The identifier of the conglomerate to alter.
	 @param storagePosition  The storage number to drop this column from.
	 @param position         The column number to drop this column from.
	 @exception StandardException Only base conglomerates support.  If the column can not be
	 dropped an exception will be thrown.
	 **/
	void dropColumnFromConglomerate(
			long conglomId,
			int storagePosition,
			int position)
			throws StandardException;


	/**
	 Drop a conglomerate.  The conglomerate must not be open in
	 the current transaction.  This also means that there must
	 not be any active scans on it.

	 @param conglomId The identifier of the conglomerate to drop.

	 @exception StandardException if the conglomerate could not be
	 dropped for some reason.
	 **/
	void dropConglomerate(long conglomId)
			throws StandardException;

	/**
	 * For debugging, find the conglomid given the containerid.
	 * <p>
	 *
	 * @return the conglomid, which contains the container with containerid.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	long findConglomid(long containerid)
			throws StandardException;

	/**
	 * For debugging, find the containerid given the conglomid.
	 * <p>
	 * Will have to change if we ever have more than one container in
	 * a conglomerate.
	 *
	 * @return the containerid of container implementing conglomerate with
	 *             "conglomid."
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	long findContainerid(long conglomid)
			throws StandardException;

	/**
	 * Get an nested user transaction.
	 * <p>
	 * A nested user transaction can be used exactly as any other
	 * TransactionController, except as follows.  For this discussion let the
	 * parent transaction be the transaction used to make the
	 * startNestedUserTransaction() call, and let the child transaction be the
	 * transaction returned by the startNestedUserTransaction() call.
	 * <p>
	 * A parent transaction can nest a single readonly transaction
	 * and a single separate read/write transaction.
	 * If a subsequent nested transaction creation is attempted
	 * against the parent prior to destroying an existing
	 * nested user transaction of the same type, an exception will be thrown.
	 * <p>
	 * The nesting is limited to one level deep.  An exception will be thrown
	 * if a subsequent getNestedUserTransaction() is called on the child
	 * transaction.
	 * <p>
	 * The locks in the child transaction of a readOnly nested user transaction
	 * will be compatible with the locks of the parent transaction.  The
	 * locks in the child transaction of a non-readOnly nested user transaction
	 * will NOT be compatible with those of the parent transaction - this is
	 * necessary for correct recovery behavior.
	 * <p>
	 * A commit in the child transaction will release locks associated with
	 * the child transaction only, work can continue in the parent transaction
	 * at this point.
	 * <p>
	 * Any abort of the child transaction will result in an abort of both
	 * the child transaction and parent transaction, either initiated by
	 * an explict abort() call or by an exception that results in an abort.
	 * <p>
	 * A TransactionController.destroy() call should be made on the child
	 * transaction once all child work is done, and the caller wishes to
	 * continue work in the parent transaction.
	 * <p>
	 * AccessFactory.getTransaction() will always return the "parent"
	 * transaction, never the child transaction.  Thus clients using
	 * nested user transactions must keep track of the transaction, as there
	 * is no interface to query the storage system to get the current
	 * child transaction.  The idea is that a nested user transaction should
	 * be used to for a limited amount of work, committed, and then work
	 * continues in the parent transaction.
	 * <p>
	 * Nested User transactions are meant to be used to implement
	 * system work necessary to commit as part of implementing a user's
	 * request, but where holding the lock for the duration of the user
	 * transaction is not acceptable.  2 examples of this are system catalog
	 * read locks accumulated while compiling a plan, and auto-increment.
	 * <p>
	 * Once the first write of a non-readOnly nested transaction is done,
	 * then the nested user transaction must be committed or aborted before
	 * any write operation is attempted in the parent transaction.
	 * (p>
	 * fix for DERBY-5493 introduced a behavior change for commits executed
	 * against an updatable nested user transaction.  Prior to this change
	 * commits would execute a "lazy" commit where commit log record would only
	 * be written to the stream, not guaranteed to disk.  After this change
	 * commits on these transactions will always be forced to disk.  To get
	 * the previous behavior one must call commitNoSync() instead.
	 * <p>
	 * examples of current usage of nested updatable transactions in Derby
	 * include:
	 * o recompile and saving of stored prepared statements, changed with
	 *   DERBY-5493 to do synchronous commit.  Code in SPSDescriptor.java.
	 * o sequence updater reserves new "range" of values in sequence
	 *   catalog, changed with DERBY-5493 to do synchronous commit.  Without
	 *   this change crash of system might lose the updat of the range and
	 *   then return same value on reboot.  Code in SequenceUpdater.java
	 * o in place compress defragment phase committing units of work in
	 *   moving tuples around in heap and indexes.  changed with DERBY-5493
	 *   to do synchronous commit. code in AlterTableConstantAction.java.
	 * o used for creation of users initial default schema in SYSSCHEMAS.
	 *   moving tuples around in heap and indexes.  changed with DERBY-5493
	 *   to do synchronous commit. code in DDLConstantAction.java.
	 * o autoincrement/generated key case.  Kept behavior previous to
	 *   DERBY-5493 by changing to use commitNoSync, and defaulting
	 *   flush_log_on_xact_end to false.  Changing every
	 *   key allocation to be a synchronous commit would be a huge performance
	 *   problem for existing applications depending on current performance.
	 *   code in InsertResultSet.java
	 *
	 * @param readOnly                 Is transaction readonly?  Only 1 non-read
	 *                                 only nested transaction is allowed per
	 *                                 transaction.
	 *
	 * @param flush_log_on_xact_end    By default should the transaction commit
	 *                                 and abort be synced to the log.  Normal
	 *                                 usage should pick true, unless there is
	 *                                 specific performance need and usage
	 *                                 works correctly if a commit can be lost
	 *                                 on system crash.
	 *
	 * @return The new nested user transaction.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	TransactionController startNestedUserTransaction(
			boolean readOnly,
			boolean flush_log_on_xact_end)
			throws StandardException;

	/**
	 * Get a nested internal transaction.
	 * <p>
	 * A nested internal transaction is used in place of a user transaction
	 * when a DML statement is executing its operation tree and a new writable
	 * child transaction needs to be created with the possibility that within
	 * that statement another substatement may be run, requiring a nested transaction.
	 *
	 * @param readOnly True to begin a readOnly transaction, otherwise false.
	 * @param destinationTable The byte representation of the conglomerate
	 *                         number of the target table as a String.
	 * @param inMemoryTxn If true, attempt to begin the child transaction
	 *                    as an in-memory transaction.  This is only
	 *                    applicable to non-Spark queries.
	 * @return SpliceInternalTransactionManager object for tracking the latest child transaction.
	 *
	 * @notes The purpose of this method is to produce a new SpliceInternalTransactionManager
	 * in the current context which will return the most recent child transaction upon
	 * call to LanguageConnectionContext.getTransactionExecute().  This is the main method
	 * which is used to determine which parent transaction to use when beginning a new
	 * child transaction.  Previously, triggers nested inside triggers did not create the
	 * inside trigger's transaction as a child of the outer trigger's transaction, but instead
	 * as a child of the top-level DMLWriteOperation.  Having triggers use sibling transactions
	 * adds the need to do early committing of transactions so that one trigger's written rows
	 * are visible to the other trigger, and causes problems if we need to roll back.
	 * With fully nested triggers, a child trigger can see a parent trigger's written rows
	 * while the parent transaction is still active, so we never have to get in a situation
	 * where we need to roll back a committed transaction.
	 * Once this TransactionController is created, it should be pushed to the context via
	 * LanguageConnectionContext.pushNestedTransaction, and popped via popNestedTransaction
	 * once the executing operation is finished.
	 * Once the initial TransactionController is pushed, subsequent child transactions can
	 * be pushed to the transaction stack via:
	 *   lcc.getTransactionExecute().getRawStoreXact().pushInternalTransaction(childTxn);
	 *
	 * SpliceInternalTransactionManager cannot be used to set or release savepoints or commit
	 * transactions.  It is merely used for providing a means to pick the proper parent
	 * transaction for any new internally-created child transactions.
	 *
	 */
	TransactionController startNestedInternalTransaction(
			boolean readOnly,
			byte[] destinationTable,
			boolean inMemoryTxn)
			throws StandardException;

	TransactionController startIndependentInternalTransaction(boolean readOnly) throws StandardException;

	/**
	 * A superset of properties that "users" can specify.
	 * <p>
	 * A superset of properties that "users" (ie. from sql) can specify.  Store
	 * may implement other properties which should not be specified by users.
	 * Layers above access may implement properties which are not known at
	 * all to Access.
	 * <p>
	 * This list is a superset, as some properties may not be implemented by
	 * certain types of conglomerates.  For instant an in-memory store may not
	 * implement a pageSize property.  Or some conglomerates may not support
	 * pre-allocation.
	 * <p>
	 * This interface is meant to be used by the SQL parser to do validation
	 * of properties passsed to the create table statement, and also by the
	 * various user interfaces which present table information back to the
	 * user.
	 * <p>
	 * Currently this routine returns the following list:
	 *      db.storage.initialPages
	 *      db.storage.minimumRecordSize
	 *      db.storage.pageReservedSpace
	 *      db.storage.pageSize
	 *
	 * @return The superset of properties that "users" can specify.
	 *
	 **/
	Properties getUserCreateConglomPropList();

	/**
	 * Open a conglomerate for use.
	 * <p>
	 * The lock level indicates the minimum lock level to get locks at, the
	 * underlying conglomerate implementation may actually lock at a higher
	 * level (ie. caller may request MODE_RECORD, but the table may be locked
	 * at MODE_TABLE instead).
	 * <p>
	 * The close method is on the ConglomerateController interface.
	 *
	 * @return a ConglomerateController to manipulate the conglomerate.
	 *
	 * @param conglomId         The identifier of the conglomerate to open.
	 *
	 * @param hold              If true, will be maintained open over commits.
	 *
	 * @param open_mode         Specifiy flags to control opening of table.
	 *                          OPENMODE_FORUPDATE - if set open the table for
	 *                          update otherwise open table shared.
	 *
	 * @param lock_level        One of (MODE_TABLE, MODE_RECORD).
	 *
	 * @param isolation_level   The isolation level to lock the conglomerate at.
	 *                          One of (ISOLATION_READ_COMMITTED,
	 *                          ISOLATION_REPEATABLE_READ or
	 *                          ISOLATION_SERIALIZABLE).
	 *
	 * @exception  StandardException  if the conglomerate could not be opened
	 *                                for some reason.  Throws
	 *                                SQLState.STORE_CONGLOMERATE_DOES_NOT_EXIST
	 *                                if the conglomId being requested does not
	 *                                exist for some reason (ie. someone has
	 *                                dropped it).
	 **/
	ConglomerateController openConglomerate(
			long                            conglomId,
			boolean                         hold,
			int                             open_mode,
			int                             lock_level,
			int                             isolation_level)
			throws StandardException;

	/**
	 * Open a conglomerate for use, optionally include "compiled" info.
	 * <p>
	 * Same as openConglomerate(), except that one can optionally provide
	 * "compiled" static_info and/or dynamic_info.  This compiled information
	 * must have be gotten from getDynamicCompiledConglomInfo() and/or
	 * getStaticCompiledConglomInfo() calls on the same conglomid being opened.
	 * It is up to caller that "compiled" information is still valid and
	 * is appropriately multi-threaded protected.
	 * <p>
	 *
	 * @see TransactionController#openConglomerate
	 * @see TransactionController#getDynamicCompiledConglomInfo
	 * @see TransactionController#getStaticCompiledConglomInfo
	 * @see DynamicCompiledOpenConglomInfo
	 * @see StaticCompiledOpenConglomInfo
	 *
	 * @return The identifier to be used to open the conglomerate later.
	 *
	 * @param hold              If true, will be maintained open over commits.
	 * @param open_mode         Specifiy flags to control opening of table.
	 * @param lock_level        One of (MODE_TABLE, MODE_RECORD).
	 * @param isolation_level   The isolation level to lock the conglomerate at.
	 *                          One of (ISOLATION_READ_COMMITTED,
	 *                          ISOLATION_REPEATABLE_READ or
	 *                          ISOLATION_SERIALIZABLE).
	 * @param static_info       object returned from
	 *                          getStaticCompiledConglomInfo() call on this id.
	 * @param dynamic_info      object returned from
	 *                          getDynamicCompiledConglomInfo() call on this id.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	ConglomerateController openCompiledConglomerate(
			boolean                         hold,
			int                             open_mode,
			int                             lock_level,
			int                             isolation_level,
			StaticCompiledOpenConglomInfo   static_info,
			DynamicCompiledOpenConglomInfo  dynamic_info)
			throws StandardException;


	/**
	 Open a scan on a conglomerate.  The scan will return all
	 rows in the conglomerate which are between the
	 positions defined by {startKeyValue, startSearchOperator} and
	 {stopKeyValue, stopSearchOperator}, which also match the qualifier.
	 <P>
	 The way that starting and stopping keys and operators are used
	 may best be described by example. Say there's an ordered conglomerate
	 with two columns, where the 0-th column is named 'x', and the 1st
	 column is named 'y'.  The values of the columns are as follows:
	 <blockquote><pre>
	 x: 1 3 4 4 4 5 5 5 6 7 9
	 y: 1 1 2 4 6 2 4 6 1 1 1
	 </blockquote></pre>
	 <P>
	 A {start key, search op} pair of {{5.2}, GE} would position on
	 {x=5, y=2}, whereas the pair {{5}, GT} would position on {x=6, y=1}.
	 <P>
	 Partial keys are used to implement partial key scans in SQL.
	 For example, the SQL "select * from t where x = 5" would
	 open a scan on the conglomerate (or a useful index) of t
	 using a starting position partial key of {{5}, GE} and
	 a stopping position partial key of {{5}, GT}.
	 <P>
	 Some more examples:
	 <p>
	 <blockquote><pre>
	 +-------------------+------------+-----------+--------------+--------------+
	 | predicate         | start key  | stop key  | rows         | rows locked  |
	 |                   | value | op | value |op | returned     |serialization |
	 +-------------------+-------+----+-------+---+--------------+--------------+
	 | x = 5             | {5}   | GE | {5}   |GT |{5,2} .. {5,6}|{4,6} .. {5,6}|
	 | x > 5             | {5}   | GT | null  |   |{6,1} .. {9,1}|{5,6} .. {9,1}|
	 | x >= 5            | {5}   | GE | null  |   |{5,2} .. {9,1}|{4,6} .. {9,1}|
	 | x <= 5            | null  |    | {5}   |GT |{1,1} .. {5,6}|first .. {5,6}|
	 | x < 5             | null  |    | {5}   |GE |{1,1} .. {4,6}|first .. {4,6}|
	 | x >= 5 and x <= 7 | {5},  | GE | {7}   |GT |{5,2} .. {7,1}|{4,6} .. {7,1}|
	 | x = 5  and y > 2  | {5,2} | GT | {5}   |GT |{5,4} .. {5,6}|{5,2} .. {5,6}|
	 | x = 5  and y >= 2 | {5,2} | GE | {5}   |GT |{5,2} .. {5,6}|{4,6} .. {5,6}|
	 | x = 5  and y < 5  | {5}   | GE | {5,5} |GE |{5,2} .. {5,4}|{4,6} .. {5,4}|
	 | x = 2             | {2}   | GE | {2}   |GT | none         |{1,1} .. {1,1}|
	 +-------------------+-------+----+-------+---+--------------+--------------+
	 </blockquote></pre>
	 <P>
	 As the above table implies, the underlying scan may lock
	 more rows than it returns in order to guarantee serialization.
	 <P>
	 For each row which meets the start and stop position, as described above
	 the row is "qualified" to see whether it should be returned.  The
	 qualification is a 2 dimensional array of @see Qualifiers, which represents
	 the qualification in conjunctive normal form (CNF).  Conjunctive normal
	 form is an "and'd" set of "or'd" Qualifiers.
	 <P>
	 For example x = 5 would be represented is pseudo code as:

	 qualifier_cnf[][] = new Qualifier[1];
	 qualifier_cnf[0]  = new Qualifier[1];

	 qualifier_cnr[0][0] = new Qualifer(x = 5)

	 <P>
	 For example (x = 5) or (y = 6) would be represented is pseudo code as:

	 qualifier_cnf[][] = new Qualifier[1];
	 qualifier_cnf[0]  = new Qualifier[2];

	 qualifier_cnr[0][0] = new Qualifer(x = 5)
	 qualifier_cnr[0][1] = new Qualifer(y = 6)

	 <P>
	 For example ((x = 5) or (x = 6)) and ((y = 1) or (y = 2)) would be
	 represented is pseudo code as:

	 qualifier_cnf[][] = new Qualifier[2];
	 qualifier_cnf[0]  = new Qualifier[2];

	 qualifier_cnr[0][0] = new Qualifer(x = 5)
	 qualifier_cnr[0][1] = new Qualifer(x = 6)

	 qualifier_cnr[0][0] = new Qualifer(y = 5)
	 qualifier_cnr[0][1] = new Qualifer(y = 6)

	 <P>
	 For each row the CNF qualfier is processed and it is determined whether
	 or not the row should be returned to the caller.

	 The following pseudo-code describes how this is done:

	 <blockquote><pre>
	 if (qualifier != null)
	 {
	 <blockquote><pre>
	 for (int and_clause; and_clause < qualifier.length; and_clause++)
	 {
	 boolean or_qualifies = false;

	 for (int or_clause; or_clause < qualifier[and_clause].length; or_clause++)
	 {
	 <blockquote><pre>
	 DataValueDescriptor key     =
	 qualifier[and_clause][or_clause].getOrderable();

	 DataValueDescriptor row_col =
	 get row column[qualifier[and_clause][or_clause].getColumnId()];

	 boolean or_qualifies =
	 row_col.compare(qualifier[i].getOperatorString,
	 <blockquote><pre>
	 key,
	 qualifier[i].getOrderedNulls,
	 qualifier[i].getUnknownRV);
	 </blockquote></pre>

	 if (or_qualifies)
	 {
	 break;
	 }
	 }

	 if (!or_qualifies)
	 {
	 <blockquote><pre>
	 don't return this row to the client - proceed to next row;
	 </blockquote></pre>
	 }
	 </blockquote></pre>

	 }
	 </blockquote></pre>
	 }
	 </blockquote></pre>


	 @param conglomId The identifier of the conglomerate
	 to open the scan for.

	 @param hold If true, this scan will be maintained open over
	 commits.

	 @param open_mode         Specifiy flags to control opening of table.
	 OPENMODE_FORUPDATE - if set open the table for
	 update otherwise open table shared.

	 @param lock_level        One of (MODE_TABLE, MODE_RECORD).

	 @param isolation_level   The isolation level to lock the conglomerate at.
	 One of (ISOLATION_READ_COMMITTED,
	 ISOLATION_REPEATABLE_READ or
	 ISOLATION_SERIALIZABLE).

	 @param scanColumnList A description of which columns to return from
	 every fetch in the scan.  template, and scanColumnList
	 work together to describe the row to be returned by the scan - see RowUtil
	 for description of how these three parameters work together to describe
	 a "row".

	 @param startKeyValue  An indexable row which holds a
	 (partial) key value which, in combination with the
	 startSearchOperator, defines the starting position of
	 the scan.  If null, the starting position of the scan
	 is the first row of the conglomerate.
	 The startKeyValue must only reference columns included
	 in the scanColumnList.

	 @param startSearchOperator an operator which defines
	 how the startKeyValue is to be searched for.  If
	 startSearchOperation is ScanController.GE, the scan starts on
	 the first row which is greater than or equal to the
	 startKeyValue.  If startSearchOperation is ScanController.GT,
	 the scan starts on the first row whose key is greater than
	 startKeyValue.  The startSearchOperation parameter is
	 ignored if the startKeyValue parameter is null.

	 @param qualifier A 2 dimensional array encoding a conjunctive normal
	 form (CNF) datastructure of of qualifiers which, applied
	 to each key, restrict the rows returned by the scan.  Rows
	 for which the CNF expression returns false are not
	 returned by the scan. If null, all rows are returned.
	 Qualifiers can only reference columns which are included in the
	 scanColumnList.  The column id that a qualifier returns is the
	 column id the table, not the column id in the partial row being
	 returned.

	 For detailed description of 2-dimensional array passing @see Qualifier

	 @param stopKeyValue  An indexable row which holds a
	 (partial) key value which, in combination with the
	 stopSearchOperator, defines the ending position of
	 the scan.  If null, the ending position of the scan
	 is the last row of the conglomerate.
	 The stopKeyValue must only reference columns included
	 in the scanColumnList.

	 @param stopSearchOperator an operator which defines
	 how the stopKeyValue is used to determine the scan stopping
	 position. If stopSearchOperation is ScanController.GE, the scan
	 stops just before the first row which is greater than or
	 equal to the stopKeyValue.  If stopSearchOperation is
	 ScanController.GT, the scan stops just before the first row whose
	 key is greater than	startKeyValue.  The stopSearchOperation
	 parameter is ignored if the stopKeyValue parameter is null.

	 @exception StandardException if the scan could not be
	 opened for some reason.  Throws SQLState.STORE_CONGLOMERATE_DOES_NOT_EXIST
	 if the conglomId being requested does not exist for some reason (ie.
	 someone has dropped it).

	 @see RowUtil
	 @see ScanController
	 **/
	ScanController openScan(
			long                            conglomId,
			boolean                         hold,
			int                             open_mode,
			int                             lock_level,
			int                             isolation_level,
			FormatableBitSet                scanColumnList,
			DataValueDescriptor[]           startKeyValue,
			int                             startSearchOperator,
			Qualifier                       qualifier[][],
			DataValueDescriptor[]           stopKeyValue,
			int                             stopSearchOperator)
			throws StandardException;


	/**
	 * Open a scan on a conglomerate, optionally providing compiled info.
	 * <p>
	 * Same as openScan(), except that one can optionally provide
	 * "compiled" static_info and/or dynamic_info.  This compiled information
	 * must have be gotten from getDynamicCompiledConglomInfo() and/or
	 * getStaticCompiledConglomInfo() calls on the same conglomid being opened.
	 * It is up to caller that "compiled" information is still valid and
	 * is appropriately multi-threaded protected.
	 * <p>
	 *
	 * @see TransactionController#openScan
	 * @see TransactionController#getDynamicCompiledConglomInfo
	 * @see TransactionController#getStaticCompiledConglomInfo
	 * @see DynamicCompiledOpenConglomInfo
	 * @see StaticCompiledOpenConglomInfo
	 *
	 * @return The identifier to be used to open the conglomerate later.
	 *
	 * @param open_mode             see openScan()
	 * @param lock_level            see openScan()
	 * @param isolation_level       see openScan()
	 * @param scanColumnList        see openScan()
	 * @param startKeyValue         see openScan()
	 * @param startSearchOperator   see openScan()
	 * @param qualifier             see openScan()
	 * @param stopKeyValue          see openScan()
	 * @param stopSearchOperator    see openScan()
	 * @param static_info       object returned from
	 *                          getStaticCompiledConglomInfo() call on this id.
	 * @param dynamic_info      object returned from
	 *                          getDynamicCompiledConglomInfo() call on this id.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	ScanController openCompiledScan(
			boolean                         hold,
			int                             open_mode,
			int                             lock_level,
			int                             isolation_level,
			FormatableBitSet                scanColumnList,
			DataValueDescriptor[]           startKeyValue,
			int                             startSearchOperator,
			Qualifier                       qualifier[][],
			DataValueDescriptor[]           stopKeyValue,
			int                             stopSearchOperator,
			StaticCompiledOpenConglomInfo   static_info,
			DynamicCompiledOpenConglomInfo  dynamic_info)
			throws StandardException;


	/**
	 * Open a scan which gets copies of multiple rows at a time.
	 * <p>
	 * All inputs work exactly as in openScan().  The return is
	 * a GroupFetchScanController, which only allows fetches of groups
	 * of rows from the conglomerate.
	 * <p>
	 *
	 * @return The GroupFetchScanController to be used to fetch the rows.
	 *
	 * @param conglomId             see openScan()
	 * @param open_mode             see openScan()
	 * @param lock_level            see openScan()
	 * @param isolation_level       see openScan()
	 * @param scanColumnList        see openScan()
	 * @param startKeyValue         see openScan()
	 * @param startSearchOperator   see openScan()
	 * @param qualifier             see openScan()
	 * @param stopKeyValue          see openScan()
	 * @param stopSearchOperator    see openScan()
	 *
	 * @exception  StandardException  Standard exception policy.
	 *
	 * @see ScanController
	 * @see GroupFetchScanController
	 **/
	GroupFetchScanController openGroupFetchScan(
			long                            conglomId,
			boolean                         hold,
			int                             open_mode,
			int                             lock_level,
			int                             isolation_level,
			FormatableBitSet                         scanColumnList,
			DataValueDescriptor[]           startKeyValue,
			int                             startSearchOperator,
			Qualifier                       qualifier[][],
			DataValueDescriptor[]           stopKeyValue,
			int                             stopSearchOperator)
			throws StandardException;

	/**
	 * Return an open StoreCostController for the given conglomid.
	 * <p>
	 * Return an open StoreCostController which can be used to ask about
	 * the estimated row counts and costs of ScanController and
	 * ConglomerateController operations, on the given conglomerate.
	 * <p>
	 *
	 * @return The open StoreCostController.
	 *
	 * @param conglomerateDescriptor The identifier of the conglomerate to open.
	 *
	 * @param skipDictionaryStats Whether we should fetch real stats from dictionary or just fake it
	 *
	 * @param defaultRowcount only takes effect when skipDictionaryStats is true, fix the rowcount to be the specified value
	 *
	 * @param requestedSplits The number of input splits requested via the splits query hint, or 0 for no hint.
	 *
	 * @param useDb2CompatibleVarchars
	 * @exception  StandardException  Standard exception policy.
	 *
	 * @see StoreCostController
	 **/
	StoreCostController openStoreCost(TableDescriptor td, ConglomerateDescriptor conglomerateDescriptor, boolean skipDictionaryStats, long defaultRowcount, int requestedSplits, boolean useDb2CompatibleVarchars) throws StandardException;

	/**
	 * Return a string with debug information about opened congloms/scans/sorts.
	 * <p>
	 * Return a string with debugging information about current opened
	 * congloms/scans/sorts which have not been close()'d.
	 * Calls to this routine are only valid under code which is conditional
	 * on SanityManager.DEBUG.
	 * <p>
	 *
	 * @return String with debugging information.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	String debugOpened() throws StandardException;


	/**
	 Get an object to handle non-transactional files.
	 */
	FileResource getFileHandler();

	/**
	 * Return an object that when used as the compatibility space for a lock
	 * request, <strong>and</strong> the group object is the one returned by a
	 * call to <code>getOwner()</code> on that object, guarantees that the lock
	 * will be removed on a commit or an abort.
	 */
	CompatibilitySpace getLockSpace();

	/**
	 * Tell this transaction whether it should time out immediately if a lock
	 * cannot be granted without waiting. This mechanism can for instance be
	 * used if an operation is first attempted in a nested transaction to
	 * reduce the lifetime of locks held in the system tables (like when
	 * a stored prepared statement is compiled and stored). In such a case,
	 * the caller must catch timeout exceptions and retry the operation in the
	 * main transaction if a lock timeout occurs.
	 *
	 * @param noWait if {@code true} never wait for a lock in this transaction,
	 * but time out immediately
	 * @see com.splicemachine.db.iapi.services.locks.LockOwner#noWait()
	 * @see com.splicemachine.db.iapi.store.raw.Transaction#setNoLockWait(boolean)
	 */
	void setNoLockWait(boolean noWait);

	/**
	 * Return static information about the conglomerate to be included in a
	 * a compiled plan.
	 * <p>
	 * The static info would be valid until any ddl was executed on the
	 * conglomid, and would be up to the caller to throw away when that
	 * happened.  This ties in with what language already does for other
	 * invalidation of static info.  The type of info in this would be
	 * containerid and array of format id's from which templates can be created.
	 * The info in this object is read only and can be shared among as many
	 * threads as necessary.
	 * <p>
	 *
	 * @return The static compiled information.
	 *
	 * @param conglomId The identifier of the conglomerate to open.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	StaticCompiledOpenConglomInfo getStaticCompiledConglomInfo(
			long conglomId)
			throws StandardException;

	/**
	 * Return dynamic information about the conglomerate to be dynamically
	 * reused in repeated execution of a statement.
	 * <p>
	 * The dynamic info is a set of variables to be used in a given
	 * ScanController or ConglomerateController.  It can only be used in one
	 * controller at a time.  It is up to the caller to insure the correct
	 * thread access to this info.  The type of info in this is a scratch
	 * template for btree traversal, other scratch variables for qualifier
	 * evaluation, ...
	 * <p>
	 *
	 * @return The dynamic information.
	 *
	 * @param conglomId The identifier of the conglomerate to open.
	 *
	 * @exception  StandardException  Standard exception policy.
	 **/
	DynamicCompiledOpenConglomInfo getDynamicCompiledConglomInfo(
			long conglomId)
			throws StandardException;

	/**************************************************************************
	 * Interfaces previously defined in TcLogIface:
	 **************************************************************************
	 */

	/**************************************************************************
	 * Interfaces previously defined in TcSortIface:
	 **************************************************************************
	 */

	/**
	 * Return an open SortCostController.
	 * <p>
	 * Return an open SortCostController which can be used to ask about
	 * the estimated costs of SortController() operations.
	 * <p>
	 * @param implParameters  Properties which help in choosing
	 *                        implementation-specific sort options.  If null, a
	 *                        "generally useful" sort will be used.
	 *
	 * @return The open StoreCostController.
	 *
	 * @exception  StandardException  Standard exception policy.
	 *
	 * @see StoreCostController
	 **/
	SortCostController openSortCostController( Properties  implParameters) throws StandardException;

	/**************************************************************************
	 * Interfaces previously defined in TcTransactionIface:
	 **************************************************************************
	 */

	/**
	 Return true if any transaction is blocked (even if not by this one).

	 */
	boolean anyoneBlocked();

	/**
	 Abort all changes made by this transaction since the last commit, abort
	 or the point the transaction was started, whichever is the most recent.
	 All savepoints within this transaction are released, and all resources
	 are released (held or non-held).

	 @exception StandardException Only exceptions with severities greater than
	 ExceptionSeverity.TRANSACTION_SEVERITY will be thrown.
	 **/
	void abort()
			throws StandardException;

	/**
	 * "Elevate" the underlying transaction to one that allows writes. This
	 * is not used by db code proper, but it is needed for the Read/write
	 * transaction logic contained in Splice.
	 *
	 * @param tableName the name of the table to elevate
	 * @throws StandardException If something goes wrong during elevation
	 */
	void elevate(String tableName) throws StandardException;

	/**
	 Commit this transaction.  All savepoints within this transaction are
	 released.  All non-held conglomerates and scans are closed.

	 @exception StandardException Only exceptions with severities greater than
	 ExceptionSeverity.TRANSACTION_SEVERITY will be thrown.
	 If an exception is thrown, the transaction will not (necessarily) have
	 been aborted.  The standard error handling mechanism is expected to do the
	 appropriate cleanup.  In other words, if commit() encounters an error, the
	 exception is propagated up to the the standard exception handler, which
	 initiates cleanupOnError() processing, which will eventually abort the
	 transaction.
	 **/
	void commit()
			throws StandardException;

	/**
	 "Commit" this transaction without sync'ing the log.  Everything else is
	 identical to commit(), use this at your own risk.

	 <BR>bits in the commitflag can turn on to fine tuned the "commit":
	 KEEP_LOCKS                          - no locks will be released by the
	 commit and no post commit processing
	 will be initiated.  If, for some
	 reasons, the locks cannot be kept
	 even if this flag is set, then the
	 commit will sync the log, i.e., it
	 will revert to the normal commit.

	 READONLY_TRANSACTION_INITIALIZATION - Special case used for processing
	 while creating the transaction.
	 Should only be used by the system
	 while creating the transaction to
	 commit readonly work that may have
	 been done using the transaction
	 while getting it setup to be used
	 by the user.  In the future we should
	 instead use a separate tranaction to
	 do this initialization.  Will fail
	 if called on a transaction which
	 has done any updates.
	 @see TransactionController#commit

	 @exception StandardException Only exceptions with severities greater than
	 ExceptionSeverity.TRANSACTION_SEVERITY will be thrown.
	 If an exception is thrown, the transaction will not (necessarily) have
	 been aborted.  The standard error handling mechanism is expected to do the
	 appropriate cleanup.  In other words, if commit() encounters an error, the
	 exception is propagated up to the the standard exception handler, which
	 initiates cleanupOnError() processing, which will eventually abort the
	 transaction.
	 **/
	void commitNoSync(int commitflag)
			throws StandardException;

	int RELEASE_LOCKS                          = 0x1;
	int KEEP_LOCKS                             = 0x2;
	int READONLY_TRANSACTION_INITIALIZATION    = 0x4;

	/**
	 Abort the current transaction and pop the context.
	 **/
	void destroy();

	/**
	 * Get string id of the transaction.
	 * <p>
	 * This transaction "name" will be the same id which is returned in
	 * the TransactionInfo information, used by the lock and transaction
	 * vti's to identify transactions.
	 * <p>
	 * Although implementation specific, the transaction id is usually a number
	 * which is bumped every time a commit or abort is issued.
	 * <p>
	 *
	 * @return The a string which identifies the transaction.
	 **/
	String getTransactionIdString();

	/**
	 * Get string id of the transaction that would be when the Transaction
	 * is IN active state. This method increments the Tx id of  current Tx
	 * object if it is in idle state.
	 * Note: Use this method only  getTransactionIdString() is not suitable.
	 * @return The string which identifies the transaction.
	 **/
	String getActiveStateTxIdString();

	/**
	 * First step of 2-phase commit for a data dictionary change. Makes sure all
	 * servers are in sync for committing a change to the DataDictionary.
	 * It has to be called *before* the transaction that makes the change is committed.
	 */
	void prepareDataDictionaryChange(String currentDDLChangeId) throws StandardException;

	/**
	 * Final step of 2-phase commit for a data dictionary change. Makes sure all
	 * servers are in sync for committing a change to the DataDictionary.
	 * It has to be called *after* the transaction that makes the change is committed.
	 */
	void commitDataDictionaryChange() throws StandardException;

	/**
	 * Reveals whether the transaction has ever read or written data.
	 *
	 * @return true If the transaction has never read or written data.
	 **/
	boolean isIdle();

	/**
	 * Reveals whether the transaction is a global or local transaction.
	 *
	 * @return true If the transaction was either started by
	 *         AccessFactory.startXATransaction() or was morphed to a global
	 *         transaction by calling createXATransactionFromLocalTransaction().
	 *
	 * @see AccessFactory#startXATransaction
	 * @see TransactionController#createXATransactionFromLocalTransaction
	 *
	 **/
	boolean isGlobal();

	/**
	 * Reveals whether the transaction is read only.
	 *
	 * @return true If the transaction is read only to this point.
	 *
	 **/
	boolean isPristine();

	/**
	 Release the save point of the given name. Releasing a savepoint removes all
	 knowledge from this transaction of the named savepoint and any savepoints
	 set since the named savepoint was set.

	 @param name     The user provided name of the savepoint, set by the user
	 in the setSavePoint() call.
	 @param kindOfSavepoint     A NULL value means it is an internal savepoint (ie not a user defined savepoint)
	 Non NULL value means it is a user defined savepoint which can be a SQL savepoint or a JDBC savepoint
	 A String value for kindOfSavepoint would mean it is SQL savepoint
	 A JDBC Savepoint object value for kindOfSavepoint would mean it is JDBC savepoint
	 @return returns savepoint position in the stack.

	 @exception StandardException  Standard Derby exception policy.  A
	 statement level exception is thrown if
	 no savepoint exists with the given name.
	 **/
	int releaseSavePoint(String name, Object kindOfSavepoint) throws StandardException;

	/**
	 Rollback all changes made since the named savepoint was set. The named
	 savepoint is not released, it remains valid within this transaction, and
	 thus can be named it future rollbackToSavePoint() calls. Any savepoints
	 set since this named savepoint are released (and their changes rolled back).
	 <p>
	 if "close_controllers" is true then all conglomerates and scans are closed
	 (held or non-held).
	 <p>
	 If "close_controllers" is false then no cleanup is done by the
	 TransactionController.  It is then the responsibility of the caller to
	 close all resources that may have been affected by the statements
	 backed out by the call.  This option is meant to be used by the Language
	 implementation of statement level backout, where the system "knows" what
	 could be affected by the scope of the statements executed within the
	 statement.
	 <p>

	 @param name               The identifier of the SavePoint to roll back to.
	 @param close_controllers  boolean indicating whether or not the controller
	 should close open controllers.
	 @param kindOfSavepoint    A NULL value means it is an internal savepoint (ie not a user defined savepoint)
	 Non NULL value means it is a user defined savepoint which can be a SQL savepoint or a JDBC savepoint
	 A String value for kindOfSavepoint would mean it is SQL savepoint
	 A JDBC Savepoint object value for kindOfSavepoint would mean it is JDBC savepoint
	 @return returns savepoint position in the stack.

	 @exception StandardException  Standard Derby exception policy.  A
	 statement level exception is thrown if
	 no savepoint exists with the given name.
	 **/
	int rollbackToSavePoint(
			String name,
			boolean close_controllers, Object kindOfSavepoint)
			throws StandardException;


	/**
	 Set a save point in the current transaction. A save point defines a point in
	 time in the transaction that changes can be rolled back to. Savepoints
	 can be nested and they behave like a stack. Setting save points "one" and
	 "two" and the rolling back "one" will rollback all the changes made since
	 "one" (including those made since "two") and release savepoint "two".

	 @param name     The user provided name of the savepoint.
	 @param kindOfSavepoint     A NULL value means it is an internal savepoint (ie not a user defined savepoint)
	 Non NULL value means it is a user defined savepoint which can be a SQL savepoint or a JDBC savepoint
	 A String value for kindOfSavepoint would mean it is SQL savepoint
	 A JDBC Savepoint object value for kindOfSavepoint would mean it is JDBC savepoint
	 @return returns savepoint position in the stack.

	 @exception StandardException  Standard Derby exception policy.  A
	 statement level exception is thrown if
	 no savepoint exists with the given name.
	 **/
	int setSavePoint(String name, Object kindOfSavepoint) throws StandardException;

	/**
	 * Convert a local transaction to a global transaction.
	 * <p>
	 * Get a transaction controller with which to manipulate data within
	 * the access manager.  Tbis controller allows one to manipulate a
	 * global XA conforming transaction.
	 * <p>
	 * Must only be called a previous local transaction was created and exists
	 * in the context.  Can only be called if the current transaction is in
	 * the idle state.  Upon return from this call the old tc will be unusable,
	 * and all references to it should be dropped (it will have been implicitly
	 * destroy()'d by this call.
	 * <p>
	 * The (format_id, global_id, branch_id) triplet is meant to come exactly
	 * from a javax.transaction.xa.Xid.  We don't use Xid so that the system
	 * can be delivered on a non-1.2 vm system and not require the javax classes
	 * in the path.
	 *
	 * @param global_id the global transaction identifier part of XID - ie.
	 *                  Xid.getGlobalTransactionId().
	 * @param branch_id The branch qualifier of the Xid - ie.
	 *                  Xid.getBranchQaulifier()
	 *
	 * @exception StandardException Standard exception policy.
	 * @see TransactionController
	 **/
	/* XATransactionController */ Object createXATransactionFromLocalTransaction(
			int                     format_id,
			byte[]                  global_id,
			byte[]                  branch_id)
			throws StandardException;


	boolean isElevated();

	String getCatalogVersion(String conglomerateNumber) throws StandardException;

	void setCatalogVersion(String conglomerateNumber, String version) throws StandardException;

	void truncate(String conglomerateNumber) throws StandardException;

	void snapshot(String snapshotName, String tableName) throws StandardException;

	Set<String> listSnapshots() throws StandardException;

	void cloneSnapshot(String snapshotName, String tableName) throws StandardException;

	void deleteSnapshot(String snapshotName) throws StandardException;

	long getActiveStateTxId();

	/**
	 * The ScanController.close() method has been called on "scan".
	 * <p>
	 * Take whatever cleanup action is appropriate to a closed scan.  It is
	 * likely this routine will remove references to the scan object that it
	 * was maintaining for cleanup purposes.
	 *
	 **/
	void closeMe(ScanController scan);

	void rewritePropertyConglomerate() throws StandardException;

	void recoverPropertyConglomerateIfNecessary() throws StandardException;

	long getTxnAt(long ts) throws StandardException;

	boolean txnWithin(long period, long pastTx) throws StandardException;

	boolean txnWithin(long period, Timestamp pastTx) throws StandardException;
}
