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

package com.splicemachine.db.iapi.types;

import com.splicemachine.db.iapi.error.StandardException;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import java.text.RuleBasedCollator;

/**
 * This interface is how we get data values of different types.
 *
 * For any method that takes a 'previous' argument it is required
 * that the caller pass in an object of the same class that would
 * be returned by the call if null was passed for previous.
 */
public interface DataValueFactory
{
        /**
         * Get a SQL int with the given value.  A null argument means get
         * a SQL null value.  Uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        NumberDataValue         getDataValue(Integer value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL int with a char value.  A null argument means get
         * a SQL null value.  Uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        NumberDataValue getDataValue(char value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL smallint with the given value.  A null argument means get
         * a SQL null value.  The second arg  uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        NumberDataValue         getDataValue(Short value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL TINYINT with the given value.  A null argument means get
         * a SQL null value.  The second arg  uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        NumberDataValue         getDataValue(Byte value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL bigint with the given value.  A null argument means get
         * a SQL null value.  The second arg  uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        NumberDataValue         getDataValue(Long value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL real with the given value.  A null argument means get
         * a SQL null value.  The second arg  uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        NumberDataValue         getDataValue(Float value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL double precision with the given value.  A null argument means
         * a SQL null value.  The second arg  uses the previous value (if non-null)
         * to hold the return value.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDataValue(Double value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL boolean with the given value.  A null argument means get
         * a SQL null value.  The second arg  uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        BooleanDataValue        getDataValue(Boolean value, BooleanDataValue previous)
                                                        throws StandardException;

        // ------ LONGVARBIT

        /**
         * Get a SQL Long Bit Varying with the given value.  A null argument means
         * get a SQL null value.  Uses the previous value (if
         * non-null) to hold the return value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getLongVarbitDataValue(byte[] value,
                                                                                                BitDataValue previous)
                                                        throws StandardException;

        // ------ BLOB

        /**
         * Get a SQL Blob with the given value.  A null argument means
         * get a SQL null value.  Uses the previous value (if
         * non-null) to hold the return value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getBlobDataValue(byte[] value,
                                                                                                BitDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL Blob with the given value.  A null argument means
         * get a SQL null value.  Uses the previous value (if
         * non-null) to hold the return value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getBlobDataValue(Blob value,
                                                                                                BitDataValue previous)
                                                        throws StandardException;
        /**
         * Get a SQL varchar with the given value.  A null argument means get
         * a SQL null value.
         *
         */
        StringDataValue         getVarcharDataValue(String value);

        /**
         * Get a SQLVarhar object to represent a SQL VARCHAR  (UCS_BASIC)
         * with the given value. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new SQLVarchar
         * will be created and set to the value.
         *
         */
        StringDataValue getVarcharDataValue(String value, StringDataValue previous)
            throws StandardException;

        /**
         * Get a StringDataValue to represent a SQL VARCHAR with the
         * passed in collationType. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new StringDataValue
         * will be created and set to the value.
         * If collationType is equal to StringDataValue.COLLATION_TYPE_UCS_BASIC
         * then the call is the equivalent of the overload without collationType.
         */
        StringDataValue getVarcharDataValue(String value, StringDataValue previous,
                int collationType) throws StandardException;

        StringDataValue getVarcharDB2CompatibleDataValue(String value);
        StringDataValue getVarcharDB2CompatibleDataValue(String value, StringDataValue previous)
            throws StandardException;
        StringDataValue getVarcharDB2CompatibleDataValue(String value, StringDataValue previous,
                int collationType) throws StandardException;

        /**
         * Get a SQL long varchar with the given value.  A null argument means
         * get a SQL null value.
         *
         */
        StringDataValue         getLongvarcharDataValue(String value);

        /**
         * Get a SQLLongvarchar object to represent a SQL LONG VARCHAR  (UCS_BASIC)
         * with the given value. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new SQLLongvarchar
         * will be created and set to the value.
         *
         */
        StringDataValue getLongvarcharDataValue(String value, StringDataValue previous) throws StandardException;

        /**
         * Get a StringDataValue to represent a SQL LONG VARCHAR with the
         * passed in collationType. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new StringDataValue
         * will be created and set to the value.
         * If collationType is equal to StringDataValue.COLLATION_TYPE_UCS_BASIC
         * then the call is the equivalent of the overload without collationType.

         */
        StringDataValue getLongvarcharDataValue(String value, StringDataValue previous,
                int collationType) throws StandardException;


        /**
         * Get a SQLClob object to represent a SQL CLOB  (UCS_BASIC)
         * with the given value. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new SQLLongvarchar
         * will be created and set to the value.
         *
         */
        StringDataValue getClobDataValue(String value, StringDataValue previous) throws StandardException;

        /**
         * Get a SQLClob object to represent a SQL CLOB  (UCS_BASIC)
         * with the given value. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new SQLLongvarchar
         * will be created and set to the value.
         *
         */
        StringDataValue getClobDataValue(Clob value, StringDataValue previous) throws StandardException;

        /**
         * Get a StringDataValue to represent a SQL LONG VARCHAR with the
         * passed in collationType. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new StringDataValue
         * will be created and set to the value.
         * If collationType is equal to StringDataValue.COLLATION_TYPE_UCS_BASIC
         * then the call is the equivalent of the overload without collationType.
         */
        StringDataValue getClobDataValue(String value, StringDataValue previous,
                int collationType) throws StandardException;

        /**
         * Get a StringDataValue to represent a SQL CLOB with the
         * passed in collationType. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new StringDataValue
         * will be created and set to the value.
         * If collationType is equal to StringDataValue.COLLATION_TYPE_UCS_BASIC
         * then the call is the equivalent of the overload without collationType.
         */
        StringDataValue getClobDataValue(Clob value, StringDataValue previous,
                int collationType) throws StandardException;

        /**
         * Get a User-defined data value with the given value and type name.
         * A null argument means get a SQL null value.  The second arg uses
         * the previous value (if non-null) hold the return value.
         *
         */
        UserDataValue           getDataValue(Object value,
                                                                                UserDataValue previous);

        /**
         * Get a RefDataValue with the given value.  A null argument means get
         * a SQL null value.  Uses the previous value (if non-null)
         * to hold the return value.
         *
         */
        RefDataValue            getDataValue(RowLocation value, RefDataValue previous);

        /**
         * Get a SQL int with the given value.  The second arg re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         */
        NumberDataValue         getDataValue(int value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL bigint with the given value.  The second arg re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         */
        NumberDataValue         getDataValue(long value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL real with the given value.  Uses the previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDataValue(float value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL double precision with the given value.  Uses the previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDataValue(double value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL SMALLINT with the given value.  Uses the
         * previous value, if non-null, as the data holder to return.
         *
         */
         NumberDataValue         getDataValue(short value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL TINYINT with the given value. Uses the
         * previous value, if non-null, as the data holder to return.
         *
         */
        NumberDataValue         getDataValue(byte value, NumberDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL DECIMAL with the given value.  The second form re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDecimalDataValue(Number value) throws StandardException;
        NumberDataValue         getDecimalDataValue(Number value, NumberDataValue previous)
                                                        throws StandardException;


        /**
         * Get a SQL DECIMAL with the given value.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDecimalDataValue(Long value, NumberDataValue previous) throws StandardException;


        /**
         * Get a SQL DECIMAL with the given value.  The second form re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDecimalDataValue(String value) throws StandardException;
        NumberDataValue         getDecimalDataValue(String value, NumberDataValue previous) throws StandardException;

        /**
         * Get a SQL DECFLOAT with the given value.  The second form re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue getDecfloatDataValue(Number value) throws StandardException;
        NumberDataValue getDecfloatDataValue(Number value, NumberDataValue previous) throws StandardException;

        /**
         * Get a SQL DECFLOAT with the given value.
         * Re-uses the * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue getDecfloatDataValue(Long value, NumberDataValue previous) throws StandardException;

        /**
         * Get a SQL DECFLOAT with the given value. The second form re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        NumberDataValue         getDecfloatDataValue(String value) throws StandardException;
        NumberDataValue         getDecfloatDataValue(String value, NumberDataValue previous) throws StandardException;
        /**
         * Get a SQL boolean with the given value.  The second arg re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         */
        BooleanDataValue        getDataValue(boolean value, BooleanDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL bit with the given value.  The second form re-uses the
         * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getBitDataValue(byte[] value) throws StandardException;
        BitDataValue            getBitDataValue(byte[] value, BitDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL bit with the given value.  Uses the
         * previous value, if non-null, as the data holder to return.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getVarbitDataValue(byte[] value, BitDataValue previous)
                                                        throws StandardException;

        /**
         * Get a new SQLChar object to represent a SQL CHAR (UCS_BASIC)
         * with the given value. A null argument means get a SQL NULL value.
         *
         */
        StringDataValue         getCharDataValue(String value);

        /**
         * Get a SQLChar object to represent a SQL CHAR  (UCS_BASIC
         * with the given value. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new SQLChar
         * will be created and set to the value.
         *
         */
        StringDataValue  getCharDataValue(String value, StringDataValue previous)
                                                        throws StandardException;

        /**
         * Get a StringDataValue to represent a SQL CHAR with the
         * passed in collationType. A null argument means get a SQL NULL value.
         * If previous is not null (Java reference) then it will be set
         * to the value passed in and returned, otherwise a new StringDataValue
         * will be created and set to the value.
         * If collationType is equal to StringDataValue.COLLATION_TYPE_UCS_BASIC
         * then the call is the equivalent of the overload without collationType.
         */
        StringDataValue getCharDataValue(String value, StringDataValue previous,
                int collationType) throws StandardException;

        /**
         * Get a SQL date with the given value.  A null argument means get
         * a SQL null value.  The second arg re-uses the previous value,
         * if non-null, as the data holder to return.
         *
         */
        DateTimeDataValue       getDataValue(Date value, DateTimeDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL time with the given value.  A null argument means get
         * a SQL null value.  The second arg re-uses the previous value,
         * if non-null, as the data holder to return.
         *
         */
        DateTimeDataValue       getDataValue(Time value, DateTimeDataValue previous)
                                                        throws StandardException;

        /**
         * Get a SQL timestamp with the given value.  A null argument means get
         * a SQL null value.  The second arg re-uses the previous value,
         * if non-null, as the data holder to return.
         *
         */
        DateTimeDataValue       getDataValue(Timestamp value,
                                                                                DateTimeDataValue previous)
                                                        throws StandardException;

        /**
         * Implement the timestamp SQL function: construct a SQL timestamp from a string, or timestamp.
         *
         * @param operand Must be a timestamp or a string convertible to a timestamp.
         */
        DateTimeDataValue getTimestamp( DataValueDescriptor operand) throws StandardException;

        /**
         * Construct a SQL timestamp from a date and time.
         *
         * @param date Must be convertible to a date.
         * @param time Must be convertible to a time.
         */
        DateTimeDataValue getTimestamp( DataValueDescriptor date, DataValueDescriptor time) throws StandardException;

        /**
         * Truncate the given timestamp to the given truncValue.
         *
         * @param timestamp Must be convertible to a timestamp.
         * @param truncValue Must be a valid truncation value for date/timestamp.
         */
        DateTimeDataValue truncTimestamp(DataValueDescriptor timestamp, DataValueDescriptor truncValue) throws StandardException;

        /**
         * Truncate the given date to the given truncValue.
         *
         * @param date Must be convertible to a date.
         * @param truncValue Must be a valid truncation value for date/timestamp.
         */
        DateTimeDataValue truncDate(DataValueDescriptor date, DataValueDescriptor truncValue) throws StandardException;

    /**
     * Truncate the given decimal using the given trunc value.
     *
     * @param decimal
     * @param truncValue
     * @throws StandardException
     */
    NumberDataValue truncDecimal(DataValueDescriptor decimal, DataValueDescriptor truncValue) throws StandardException;

        /**
         * Implements the SQL date function
         *
         * @param operand A date, timestamp, string or integer.
         *
         * @return the corresponding date value
         *
         * @exception StandardException if the syntax is invalid or the date is out of range.
         */
        DateTimeDataValue getDate(DataValueDescriptor operand) throws StandardException;

        /**
         * @param dateStr A date in one of the DB2 standard date formats or the local format.
         * @param isJdbcEscape If true then the timestamp must be in the JDBC timestamp escape format, otherwise it must
         *                     be in the DB2 timestamp format.
         * @return A DateTimeDataValue
         *
         * @exception StandardException if the syntax is invalid or the date is out of range.
         */
        DateTimeDataValue getDateValue(String dateStr, boolean isJdbcEscape) throws StandardException;

        /**
         * @param timeStr A date in one of the DB2 standard time formats or the local format.
         * @param isJdbcEscape If true then the timestamp must be in the JDBC time escape format, otherwise it must
         *                     be in the DB2 time format.
         * @return A DateTimeDataValue
         *
         * @exception StandardException if the syntax is invalid or the time is out of range.
         */
        DateTimeDataValue getTimeValue(String timeStr, boolean isJdbcEscape) throws StandardException;

        /**
         * @param timestampStr A time in string format.
         * @param isJdbcEscape If true then the time must be in the JDBC time escape format, otherwise it must
         *                     be in the DB2 time format.
         * @return An internal timestamp
         *
         * @exception StandardException if the syntax is invalid or the timestamp is out of range.
         */
        DateTimeDataValue getTimestampValue(String timestampStr, boolean isJdbcEscape) throws StandardException;

        /**
         * Get a null XML value. Uses the previous value,
         * if non-null, as the data holder to return.
         */
        XMLDataValue getXMLDataValue(XMLDataValue previous)
			throws StandardException;

        /**
         * Get a SQL int with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullInteger(NumberDataValue dataValue);

        /**
         * Get a SQL smallint with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         */
        NumberDataValue getNullShort(NumberDataValue dataValue);

        /**
         * Get a SQL tinyint with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullByte(NumberDataValue dataValue);

        /**
         * Get a SQL bigint with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullLong(NumberDataValue dataValue);

        /**
         * Get a SQL float with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullFloat(NumberDataValue dataValue);

        /**
         * Get a SQL double with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullDouble(NumberDataValue dataValue);

        /**
         * Get a SQL Decimal/Numeric with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullDecimal(NumberDataValue dataValue);


        /**
         * Get a SQL Decimal/Numeric with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullDecimal(NumberDataValue dataValue, int precision, int scale);

        /**
         * Get a SQL Decfloat with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        NumberDataValue getNullDecfloat(NumberDataValue dataValue);

        /**
         * Get a SQL boolean with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         */
        BooleanDataValue getNullBoolean(BooleanDataValue dataValue);

        /**
         * Get a SQL array with  a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         */
        ArrayDataValue getNullArray(ArrayDataValue dataValue, DataValueDescriptor typeDataValue) throws StandardException;

    /**
         * Get a SQL Bit with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getNullBit(BitDataValue dataValue) throws StandardException;

        /**
         * Get a SQL Bit Varying with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getNullVarbit(BitDataValue dataValue) throws StandardException;

        // --- LONGVARBIT
        /**
         * Get a SQL Long Bit Varying with a SQL null value. If the supplied
         * value is null then get a new value, otherwise set it to null
         * and return that value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getNullLongVarbit(
                                                                                                        BitDataValue dataValue)
                                                                throws StandardException;
        // --- BLOB
        /**
         * Get a SQL Blob with a SQL null value. If the supplied
         * value is null then get a new value, otherwise set it to null
         * and return that value.
         *
         * @exception StandardException         Thrown on error
         */
        BitDataValue            getNullBlob(BitDataValue dataValue)
                throws StandardException;

    // ------ CHAR
        /**
         * Get a SQL CHAR (UCS_BASIC) with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         */
        StringDataValue         getNullChar(StringDataValue dataValue);

        /**
         * Get a SQL CHAR set to NULL with collation set to collationType.
         * If the supplied value is null then get a new value,
         * otherwise set it to null and return that value.
         */
        StringDataValue         getNullChar(StringDataValue dataValue,
                int collationType)
        throws StandardException;


        StringDataValue getNullChar(StringDataValue dataValue, int collationType, int maxSize)
        throws StandardException;

        /**
         * Get a SQL VARCHAR (UCS_BASIC) with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        StringDataValue getNullVarchar(StringDataValue dataValue);

        /**
         * Get a SQL VARCHAR set to NULL with collation set to collationType.
         * If the supplied value is null then get a new value,
         * otherwise set it to null and return that value.
         */
        StringDataValue getNullVarchar(StringDataValue dataValue,
                                       int collationType)
                        throws StandardException;

        StringDataValue getNullVarchar(StringDataValue previous, int collationType, int maxSize)
                        throws StandardException;

        StringDataValue getNullVarcharDB2Compatible(StringDataValue dataValue);

        StringDataValue getNullVarcharDB2Compatible(StringDataValue dataValue,
                                                    int collationType)
                        throws StandardException;

        StringDataValue getNullVarcharDB2Compatible(StringDataValue previous, int collationType, int maxSize)
                        throws StandardException;

        /**
         * Get a SQL LONG VARCHAR (UCS_BASIC) with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        StringDataValue         getNullLongvarchar(StringDataValue dataValue);

        /**
         * Get a SQL LONG VARCHAR set to NULL with collation set to collationType.
         * If the supplied value is null then get a new value,
         * otherwise set it to null and return that value.
         */
        StringDataValue         getNullLongvarchar(StringDataValue dataValue,
                int collationType)
        throws StandardException;

        /**
         * Get a SQL CLOB (UCS_BASIC) with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        StringDataValue         getNullClob(StringDataValue dataValue);

        /**
         * Get a SQL CLOB set to NULL with collation set to collationType.
         * If the supplied value is null then get a new value,
         * otherwise set it to null and return that value.
         */
        StringDataValue         getNullClob(StringDataValue dataValue,
                int collationType)
        throws StandardException;

        /**
         * Get a User-defined data value with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        UserDataValue           getNullObject(UserDataValue dataValue);

        UserDataValue           getNullObject(Object o);
        /**
         * Get a RefDataValue with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        RefDataValue            getNullRef(RefDataValue dataValue);

        /**
         * Get a SQL date with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        DateTimeDataValue       getNullDate(DateTimeDataValue dataValue);

        /**
         * Get a SQL time with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         *
         */
        DateTimeDataValue       getNullTime(DateTimeDataValue dataValue);

        /**
         * Get a SQL timestamp with a SQL null value. If the supplied value
         * is null then get a new value, otherwise set it to null and return
         * that value.
         */
        DateTimeDataValue       getNullTimestamp(DateTimeDataValue dataValue);

        /**
         * Get an XML with a SQL null value. If the supplied value is
         * null then get a new value, otherwise set it to null and return
         * that value.
         */
        XMLDataValue            getNullXML(XMLDataValue dataValue);

        /**
         * Return the RuleBasedCollator depending on the collation type.
         * If the collation type is UCS_BASIC, then this method will return
         * null. If the collation type is TERRITORY_BASED then the return
         * value will be the Collator derived from the database's locale.
         * If this is the first time Collator is being requested for a
         * database with collation type of TERRITORY_BASED, then we will check
         * to make sure that JVM supports the Collator for the database's
         * locale. If not, we will throw an exception
         *
         * This method will be used when Store code is trying to create a DVD
         * template row using the format ids and the collation types. First a
         * DVD will be constructed just using format id. Then if the DVD is of
         * type StringDataValue, then it will call this method to get the
         * Collator object. If the Collator object returned from this method is
         * null then we will continue to use the default DVDs for the character
         * types, ie the DVDs which just use the JVM's default collation. (This
         * is why, we want this method to return null if we are dealing with
         * UCS_BASIC.) If the Collator object returned is not null, then we
         * will construct collation sensitive DVD for the character types. So,
         * the return value of this method determines if we are going to create
         * a character DVD with default collation or with custom collation.
         *
         * @param collationType This will be UCS_BASIC or TERRITORY_BASED
         *
         * @return Collator null if the collation type is UCS_BASIC.
         *  Collator based on territory if the collation type is TERRITORY_BASED
         */
        RuleBasedCollator getCharacterCollator(int collationType)
        throws StandardException;

        /**
         * Return an object based on the format id and collation type. For
         * format ids which do not correspond to character types, a format id
         * is sufficient to get the right DVD. But for character types, Derby
         * uses same format id for collation sensitive character types and for
         * character types that use the default JVM collation. To get the
         * correct DVD for character types, we need to know the collation type.
         * Using collation type, we will determine if we need to construct
         * collation sensitive DVD and associate the correct RuleBasedCollator
         * with such DVDs.
         *
         * @param formatId Format id for the DVD
         * @param collationType this is meaningful only for character types.
         *
         * @return DataValueDescriptor which will be constructed using the
         * passed parameters
         */
        DataValueDescriptor getNull(int formatId, int collationType)
        throws StandardException;
}
