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

package com.splicemachine.stream;

/**
 * @author Scott Fines
 *         Date: 8/13/14
 */
class TransformingStream<T,V> extends AbstractStream<V> {
    private final Stream<T> stream;
    private final Transformer<T, V> transformer;

    TransformingStream(Stream<T> stream, Transformer<T, V> transformer) {
        this.stream = stream;
        this.transformer = transformer;
    }

    @Override
    public V next() throws StreamException {
        T n = stream.next();
        if(n==null) return null;
        return transformer.transform(n);
    }

    @Override
    public void close() throws StreamException  {stream.close(); }
}
