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

package com.splicemachine.pipeline;

import com.splicemachine.db.iapi.error.StandardException;
/**
 * 
 * Serializes the key elements of a Derby Standard Exception
 * 
 * @see com.splicemachine.db.iapi.error.StandardException
 *
 */
public class SpliceStandardException extends Exception{
	private static final long serialVersionUID = -298352016321581086L;
	public SpliceStandardException() {
		
	}
	public SpliceStandardException (StandardException standardException) {
		this.severity = standardException.getSeverity();
		this.textMessage = standardException.getMessage();
		this.sqlState = standardException.getSqlState();
	}
	
	private int severity;
	private String textMessage;
	private String sqlState;
	public int getSeverity() {
		return severity;
	}
	public void setSeverity(int severity) {
		this.severity = severity;
	}
	public String getTextMessage() {
		return textMessage;
	}
	public void setTextMessage(String textMessage) {
		this.textMessage = textMessage;
	}
	public String getSqlState() {
		return sqlState;
	}
	public void setSqlState(String sqlState) {
		this.sqlState = sqlState;
	}
	
	public StandardException generateStandardException() {
		StandardException se = new StandardException();
		se.setSeverity(severity);
		se.setSqlState(sqlState);
		se.setTextMessage(textMessage);
		return se;
	}
}
