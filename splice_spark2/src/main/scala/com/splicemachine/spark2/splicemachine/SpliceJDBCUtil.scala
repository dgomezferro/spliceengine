/*
 * Copyright (c) 2012 - 2021 Splice Machine, Inc.
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
package com.splicemachine.spark2.splicemachine

import java.sql.{Connection, JDBCType, ResultSet, SQLException}

import org.apache.spark.sql.execution.datasources.jdbc.JDBCRDD
import org.apache.spark.sql.jdbc.JdbcDialects
import org.apache.spark.sql.sources.Filter
import org.apache.spark.sql.types._

import scala.collection.mutable.ArrayBuffer

/**
  * Created by jleach on 4/10/17.
  */
object SpliceJDBCUtil {

  /**
    * `columns`, but as a String suitable for injection into a SQL query.
    */
  def listColumns(columns: Array[String]): String = {
    val sb = new StringBuilder()
    columns.foreach(x => sb.append(",").append(quoteIdentifier(x)) )
    if (sb.isEmpty) "*" else sb.substring(1)
  }

  /**
    * Prune all but the specified columns from the specified Catalyst schema.
    *
    * @param schema - The Catalyst schema of the master table
    * @param columns - The list of desired columns
    * @return A Catalyst schema corresponding to columns in the given order.
    */
  def pruneSchema(schema: StructType, columns: Array[String]): StructType = {
    val fieldMap = Map(schema.fields.map(x => x.metadata.getString("name") -> x): _*)
    new StructType(columns.map(name => fieldMap(name)))
  }

  /**
    * Create Where Clause Filter
    */
  def filterWhereClause(url: String, filters: Array[Filter]): String = {
    filters
      .flatMap(JDBCRDD.compileFilter(_, JdbcDialects.get(url)))
      .map(p => s"($p)").mkString(" AND ")
  }


  /**
    * Compute the schema string for this RDD.
    */
  def schemaWithoutNullableString(schema: StructType, url: String): String = {
    val sb = new StringBuilder()
    val dialect = JdbcDialects.get(url)
    schema.fields foreach { field =>
      val name =
        if (field.metadata.contains("name"))
          quoteIdentifier(field.metadata.getString("name"))
      else
          quoteIdentifier(field.name)
      val typ: String = getJdbcType(field.dataType, dialect).databaseTypeDefinition
      sb.append(s", $name $typ")
    }
    if (sb.length < 2) "" else sb.substring(2)
  }

  def retrievePrimaryKeys(tableName: String, con: Connection): Array[String] =
    retrieveMetaData(
      tableName, con,
      (conn,schema,tablename) => conn.getMetaData.getPrimaryKeys(null, schema, tablename),
      (conn,tablename) => conn.getMetaData.getPrimaryKeys(null, null, tablename),
      rs => Seq(rs.getString("COLUMN_NAME"))
    ).map(_(0))

  def retrieveColumnInfo(tableName: String, con: Connection): Array[Seq[String]] =
    retrieveMetaData(
      tableName, con,
      (conn,schema,tablename) => conn.getMetaData.getColumns(null, schema.toUpperCase, tablename.toUpperCase, null),
      (conn,tablename) => conn.getMetaData.getColumns(null, null, tablename.toUpperCase, null),
      rs => Seq(
        rs.getString("COLUMN_NAME"),
        rs.getString("TYPE_NAME"),
        rs.getString("COLUMN_SIZE"),
        rs.getString("DECIMAL_DIGITS")
      )
    )

  def retrieveTableInfo(tableName: String, con: Connection): Array[Seq[String]] =
    retrieveMetaData(
      tableName, con,
      (conn,schema,tablename) => conn.getMetaData.getTables(null, schema.toUpperCase, tablename.toUpperCase, null),
      (conn,tablename) => conn.getMetaData.getTables(null, null, tablename.toUpperCase, null),
      rs => Seq(
        rs.getString("TABLE_SCHEM"),
        rs.getString("TABLE_NAME"),
        rs.getString("TABLE_TYPE")
      )
    )

  private def retrieveMetaData(
    tableName: String, con: Connection,
    getWithSchemaTablename: (Connection,String,String) => ResultSet,
    getWithTablename: (Connection,String) => ResultSet,
    getData: ResultSet => Seq[String]
  ): Array[Seq[String]] = {
    val rs: ResultSet =
      if (tableName.contains(".")) {
        val meta = tableName.split("\\.")
        getWithSchemaTablename(con, meta(0), meta(1))
      }
      else {
        getWithTablename(con, tableName)
      }
    val buffer = ArrayBuffer[Seq[String]]()
    while (rs.next()) {
      buffer += getData(rs)
    }
    buffer.toArray
  }

  /**
   * Maps a JDBC type to a Catalyst type.  This function can be called when
   * the JdbcDialect class corresponding to your database driver returns null.
   *
   * @param sqlType - A field of java.sql.Types
   * @return The Catalyst type corresponding to sqlType.
   */
   def getCatalystType(
       sqlType: Int,
       precision: Int,
       scale: Int,
       signed: Boolean): DataType = {
    val answer = sqlType match {
      // scalastyle:off
      case java.sql.Types.ARRAY         => null
      case java.sql.Types.BIGINT        => if (signed) { LongType } else { DecimalType(20,0) }
      case java.sql.Types.BINARY        => BinaryType
      case java.sql.Types.BIT           => BooleanType // @see JdbcDialect for quirks
      case java.sql.Types.BLOB          => BinaryType
      case java.sql.Types.BOOLEAN       => BooleanType
      case java.sql.Types.CHAR          => StringType
      case java.sql.Types.CLOB          => StringType
      case java.sql.Types.DATALINK      => null
      case java.sql.Types.DATE          => DateType
      case java.sql.Types.DECIMAL
        if precision != 0 || scale != 0 => DecimalType(precision, scale)
      case java.sql.Types.DECIMAL       => DecimalType.SYSTEM_DEFAULT
      case java.sql.Types.DISTINCT      => null
      case java.sql.Types.DOUBLE        => DoubleType
      case java.sql.Types.FLOAT         => FloatType
      case java.sql.Types.INTEGER       => if (signed) { IntegerType } else { LongType }
      case java.sql.Types.JAVA_OBJECT   => null
      case java.sql.Types.LONGNVARCHAR  => StringType
      case java.sql.Types.LONGVARBINARY => BinaryType
      case java.sql.Types.LONGVARCHAR   => StringType
      case java.sql.Types.NCHAR         => StringType
      case java.sql.Types.NCLOB         => StringType
      case java.sql.Types.NULL          => null
      case java.sql.Types.NUMERIC
        if precision != 0 || scale != 0 => DecimalType(precision, scale)
      case java.sql.Types.NUMERIC       => DecimalType.SYSTEM_DEFAULT
      case java.sql.Types.NVARCHAR      => StringType
      case java.sql.Types.OTHER         => null
      case java.sql.Types.REAL          => DoubleType
      case java.sql.Types.REF           => StringType
      case java.sql.Types.REF_CURSOR    => null
      case java.sql.Types.ROWID         => LongType
      case java.sql.Types.SMALLINT      => IntegerType
      case java.sql.Types.SQLXML        => StringType
      case java.sql.Types.STRUCT        => StringType
      case java.sql.Types.TIME          => TimestampType
      case java.sql.Types.TIME_WITH_TIMEZONE
      => null
      case java.sql.Types.TIMESTAMP     => TimestampType
      case java.sql.Types.TIMESTAMP_WITH_TIMEZONE
      => null
      case java.sql.Types.TINYINT       => IntegerType
      case java.sql.Types.VARBINARY     => BinaryType
      case java.sql.Types.VARCHAR       => StringType
      case _                            =>
        throw new SQLException("Unrecognized SQL type " + sqlType)
      // scalastyle:on
    }

    if (answer == null) {
      throw new SQLException("Unsupported type " + JDBCType.valueOf(sqlType).getName)
    }
    answer
  }
}
