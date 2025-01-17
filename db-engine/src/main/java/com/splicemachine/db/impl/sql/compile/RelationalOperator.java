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

package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.catalog.IndexDescriptor;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.compiler.MethodBuilder;
import com.splicemachine.db.iapi.sql.compile.Optimizable;
import com.splicemachine.db.iapi.sql.dictionary.ConglomerateDescriptor;
import com.splicemachine.db.iapi.store.access.TransactionController;
import com.splicemachine.db.iapi.types.DataValueDescriptor;

/**
 * This interface is an abstraction of a relational operator.  It was created
 * for optimization, to allow binary comparison operators and IS NULL to
 * be treated the same.
 */
public interface RelationalOperator
{
	int EQUALS_RELOP = 1;
	int NOT_EQUALS_RELOP = 2;
	int GREATER_THAN_RELOP = 3;
	int GREATER_EQUALS_RELOP = 4;
	int LESS_THAN_RELOP = 5;
	int LESS_EQUALS_RELOP = 6;
	int IS_NULL_RELOP = 7;
	int IS_NOT_NULL_RELOP = 8;
	int ILLEGAL_UNDEFINED_RELOP = 9;

	/**
	 * Check whether this RelationalOperator is a comparison of the given
	 * column with an expression.  If so, return the ColumnReference that
	 * corresponds to the given column, and that is on one side of this
	 * RelationalOperator or the other (this method copes with the
	 * column being on either side of the operator).  If the given column
	 * does not appear by itself on one side of the comparison, the
	 * method returns null.
	 *
	 * @param optTable	An Optimizable for the base table the column is in
	 * @param columnPosition	The ordinal position of the column (one-based)
	 *
	 * @return	The ColumnReference on one side of this RelationalOperator
	 *			that represents the given columnPosition.  Returns null
	 *			if no such ColumnReference exists by itself on one side of
	 *			this RelationalOperator.
	 */
	ColumnReference	getColumnOperand(Optimizable optTable, int columnPosition);

	/**
	 * Get the ColumnReference for the given table on one side of this
	 * RelationalOperator.  This presumes it will be found only on one
	 * side.  If not found, it will return null.
	 */
	ColumnReference getColumnOperand(Optimizable optTable);

	/**
	 * Find the operand (left or right) that points to the same table
	 * as the received ColumnReference, and then return either that
	 * operand or the "other" operand, depending on the value of
	 * otherSide. This presumes it will be found only on one
	 * side.  If not found, it will return null.
	 *
	 * @param cRef The ColumnReference for which we're searching.
	 * @param refSetSize Size of the referenced map for the predicate
	 *  represented by this RelationalOperator node.  This is used
	 *  for storing base table numbers when searching for cRef.
	 * @param otherSide Assuming we find an operand that points to
	 *  the same table as cRef, then we will return the *other*
	 *  operand if otherSide is true; else we'll return the operand
	 *  that matches cRef.
	 */
	ValueNode getOperand(ColumnReference cRef, int refSetSize,
		boolean otherSide);

	ValueNode getOperand(int tabNum, int colNum, int refSetSize,
						 boolean otherSide);
	/**
	 * Check whether this RelationalOperator is a comparison of the given
	 * column with an expression.  If so, return the expression
	 * the column is being compared to.
	 *
	 * @param tableNumber	The table number of the base table the column is in
	 * @param columnPosition	The ordinal position of the column (one-based)
	 * @param ft	We'll look for the column in all tables at and beneath ft.
	 *   This is useful if ft is, say, a ProjectRestrictNode over a subquery--
	 *   then we want to look at all of the FROM tables in the subquery to try
	 *   to find the right column.
	 *
	 * @return	The ValueNode for the expression the column is being compared
	 *			to - null if the column is not being compared to anything.
	 */
	ValueNode getExpressionOperand(int tableNumber,
								   int columnPosition,
								   FromTable ft,
								   boolean forIndexExpression) throws StandardException;

	/**
	 * Check whether this RelationalOperator is a comparison of the given
	 * column with an expression.  If so, generate the Expression for
	 * the ValueNode that the column is being compared to.
	 *
	 * @param optTable	An Optimizable for the base table the column is in
	 * @param columnPosition	The ordinal position of the column (one-based)
	 * @param acb		The ExpressionClassBuilder for the class we're building
	 * @param mb		The method the expression will go into
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */
	void generateExpressionOperand(Optimizable optTable,
										int columnPosition,
										boolean forIndexExpression,
										ExpressionClassBuilder acb,
										MethodBuilder mb)
						throws StandardException;

	/**
	 * Get the index column position if there is one operand matches an index
	 * expression defined on optTable.
	 * @param tableNumber Table number of the base table on which the index
	 *                    expression is defined.
	 * @return The matching index column position if there is a match, otherwise -1.
	 */
	int getMatchingExprIndexColumnPosition(int tableNumber);

	/**
	 * Check whether this RelationalOperator compares the given ColumnReference
	 * to any columns in the same table as the ColumnReference.
	 *
	 * @param cr	The ColumnReference that is being compared to some
	 *				expression.
	 *
	 * @return	true if the given ColumnReference is being compared to any
	 *			columns from the same table
	 *
	 * @exception StandardException		Thrown on error
	 */
	boolean selfComparison(ColumnReference cr, boolean forIndexExpression)
		throws StandardException;

	/**
	 * Tell whether this relop is a useful start key for the given table.
	 * It has already been determined that the relop has a column from
	 * the given table on one side or the other.
	 *
	 * @param optTable	The Optimizable table for which we want to know
	 *					whether this is a useful start key.
	 *
	 * @return	true if this is a useful start key
	 */
	boolean	usefulStartKey(Optimizable optTable, IndexDescriptor id);

	/**
	 * Tell whether this relop is a useful stop key for the given table.
	 * It has already been determined that the relop has a column from
	 * the given table on one side or the other.
	 *
	 * @param optTable	The Optimizable table for which we want to know
	 *					whether this is a useful stop key.
	 *
	 * @return	true if this is a useful stop key
	 */
	boolean	usefulStopKey(Optimizable optTable, IndexDescriptor id);

	/**
	 * Get the start operator for a scan (at the store level) for this
	 * RelationalOperator.
	 *
	 * @param optTable	The optimizable table we're doing the scan on.
	 *					This parameter is so we can tell which side of
	 *					the operator the table's column is on.
	 *
	 * @return	Either ScanController.GT or ScanController.GE
	 *
	 * @see TransactionController#openScan
	 */
	int getStartOperator(Optimizable optTable);

	/**
	 * Get the stop operator for a scan (at the store level) for this
	 * RelationalOperator.
	 *
	 * @param optTable	The optimizable table we're doing the scan on.
	 *					This parameter is so we can tell which side of
	 *					the operator the table's column is on.
	 *
	 * @return	Either ScanController.GT or ScanController.GE
	 *
	 * @see TransactionController#openScan
	 */
	int getStopOperator(Optimizable optTable);

	/**
	 * Generate the absolute column id for the ColumnReference that appears on one
	 * side of this RelationalOperator or the other, and that refers to
	 * the given table. (Absolute column id means column id within the 
	 * row stored on disk.)
	 *
	 * @param mb	The method the generated code is to go into
	 * @param optTable	The optimizable table we're doing the scan on.
	 *
	 */
	void generateAbsoluteColumnId(MethodBuilder mb,
								  Optimizable optTable) throws StandardException;

	/**
	 * Generate the relative column id for the ColumnReference that appears on one
	 * side of this RelationalOperator or the other, and that refers to
	 * the given table. (Relative column id means column id within the 
	 * partial row returned by the store.)
	 *
	 * @param mb	The method the generated code is to go into
	 * @param optTable	The optimizable table we're doing the scan on.
	 *
	 */
	void generateRelativeColumnId(MethodBuilder mb,
								  Optimizable optTable) throws StandardException;

	/**
	 * Generate the comparison operator for this RelationalOperator.
	 * The operator can depend on which side of this operator the
	 * optimizable column is.
	 * 
	 * @param mb	The method the generated code is to go into
	 * @param optTable	The optimizable table we're doing the scan on.
	 *
	 */
	void generateOperator(MethodBuilder mb,
								Optimizable optTable);

	/**
	 * Generate the method to evaluate a Qualifier.  The factory method for
	 * a Qualifier takes a GeneratedMethod that returns the Orderable
	 * that Qualifier.getOrderable() returns.
	 *
	 * @param acb	The ExpressionClassBuilder for the class we're building
	 * @param mb	The method the generated code is to go into
	 * @param optTable	The Optimizable table the Qualifier will qualify
	 *
	 * @exception StandardException		Thrown on error.
	 */
	void generateQualMethod(ExpressionClassBuilder acb,
							MethodBuilder mb,
							Optimizable optTable,
							boolean forIndexExpression)
						throws StandardException;

	/**
	 * Generate an expression that evaluates to true if this RelationalOperator
	 * uses ordered null semantics, false if it doesn't.
	 *
	 * @param mb	The method the generated code is to go into
	 *
	 */
	void generateOrderedNulls(MethodBuilder mb);

	/**
	 * Generate an expression that evaluates to true if the result of the
	 * comparison should be negated.  For example, col > 1 generates
	 * a comparison operator of <= and a negation of true, while col < 1
	 * generates a comparison operator of < and a negation of false.
	 *
	 * @param mb	The method the generated code is to go into
	 * @param optTable	The Optimizable table the Qualifier will qualify
	 * @param forIndexExpression If we are generating this for utilizing
	 *                              an index expression.
	 */
	void generateNegate(MethodBuilder mb,
						Optimizable optTable,
						boolean forIndexExpression) throws StandardException;

	/** Return true if this operator uses ordered null semantics */
	boolean orderedNulls();

	/**
	 * Return true if this operator can be compiled into a Qualifier for
	 * the given Optimizable table.  This means that there is a column
	 * from that table on one side of this relop, and an expression that
	 * does not refer to the table on the other side of the relop.
	 *
	 * Note that this method has two uses: 1) see if this operator (or
	 * more specifically, the predicate to which this operator belongs)
	 * can be used as a join predicate (esp. for a hash join), and 2)
	 * see if this operator can be pushed to the target optTable.  We
	 * use the parameter "forPush" to distinguish between the two uses
	 * because in some cases (esp. situations where we have subqueries)
	 * the answer to "is this a qualifier?" can differ depending on
	 * whether or not we're pushing.  In particular, for binary ops
	 * that are join predicates, if we're just trying to find an
	 * equijoin predicate then this op qualifies if it references either
	 * the target table OR any of the base tables in the table's subtree.
	 * But if we're planning to push the predicate down to the target
	 * table, this op only qualifies if it references the target table
	 * directly.  This difference in behavior is required because in
	 * case 1 (searching for join predicates), the operator remains at
	 * its current level in the tree even if its operands reference
	 * nodes further down; in case 2, though, we'll end up pushing
	 * the operator down the tree to child node(s) and that requires
	 * additional logic, such as "scoping" consideration.  Until
	 * that logic is in place, we don't search a subtree if the intent
	 * is to push the predicate to which this operator belongs further
	 * down that subtree.  See BinaryRelationalOperatorNode for an
	 * example of where this comes into play.
	 *
	 * @param optTable  The Optimizable table in question.
	 * @param cd        The conglomerate descriptor of optTable currently
	 *                  in consideration. It's only used in case of
	 *                  forPush == true.
	 * @param forPush   Are we asking because we're trying to push?
	 *
	 * @return	true if this operator can be compiled into a Qualifier
	 *			for the given Optimizable table.
	 *
	 * @exception StandardException		Thrown on error
	 */
	boolean isQualifier(Optimizable optTable, ConglomerateDescriptor cd, boolean forPush)
		throws StandardException;

	/**
	 * Return the operator (as an int) for this RelationalOperator.
	 *
	 * @return int		The operator for this RelationalOperator.
	 */
	int getOperator();

	/**
	 * Return the variant type for the Qualifier's Orderable.
	 * (Is the Orderable invariant within a scan or within a query?)
	 *
	 * @param optTable	The Optimizable table the Qualifier will qualify
	 *
	 * @return int		The variant type for the Qualifier's Orderable.
	 * @exception StandardException	thrown on error
	 */
	int getOrderableVariantType(Optimizable optTable)
		throws StandardException;

	/**
	 * Return whether this operator compares the given Optimizable with
	 * a constant whose value is known at compile time.
	 */
	boolean compareWithKnownConstant(Optimizable optTable, boolean considerParameters);

	/**
	 * Return an Object representing the known value that this relational
	 * operator is comparing to a column in the given Optimizable.
	 *
	 * @exception StandardException		Thrown on error
	 */
	DataValueDescriptor getCompareValue(Optimizable optTable)
					throws StandardException;

	/**
	 * Return whether this operator is an equality comparison of the given
	 * optimizable with a constant expression.
	 */
	boolean equalsComparisonWithConstantExpression(Optimizable optTable);

	/**
	 * Return a relational operator which matches the current one
	 * but with the passed in ColumnReference as the (left) operand.
	 *
	 * @param otherCR	The ColumnReference for the new (left) operand.
	 *
	 * @return	A relational operator which matches the current one
	 *			but with the passed in ColumnReference as the (left) operand.
	 *
	 * @exception StandardException		Thrown on error
	 */
	RelationalOperator getTransitiveSearchClause(ColumnReference otherCR)
		throws StandardException;
}
