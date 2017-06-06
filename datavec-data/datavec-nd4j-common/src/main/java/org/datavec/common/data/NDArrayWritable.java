/*-
 *  * Copyright 2016 Skymind, Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 */
package org.datavec.common.data;

import java.io.*;
import java.nio.ByteBuffer;
import org.datavec.api.io.WritableComparable;
import org.datavec.api.io.WritableComparator;
import org.datavec.api.writable.ArrayWritable;
import org.datavec.api.writable.WritableFactory;
import org.datavec.api.writable.WritableType;
import org.datavec.common.util.DataInputWrapperStream;
import org.datavec.common.util.DataOutputWrapperStream;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * A Writable that basically wraps an INDArray.
 *
 * @author saudet
 */
public class NDArrayWritable extends ArrayWritable implements WritableComparable {
    public static final byte NDARRAY_SER_VERSION_HEADER_NULL = 0;
    public static final byte NDARRAY_SER_VERSION_HEADER = 1;

    static {
        WritableFactory.getInstance().registerWritableType(WritableType.NDArray.typeIdx(), NDArrayWritable.class);
    }
    private INDArray array = null;

    public NDArrayWritable() {}

    public NDArrayWritable(INDArray array) {
        set(array);
    }

    /** Deserialize into a row vector of default type. */
    public void readFields(DataInput in) throws IOException {
        DataInputStream dis = new DataInputStream(new DataInputWrapperStream(in));
        byte header = dis.readByte();
        if(header != NDARRAY_SER_VERSION_HEADER && header != NDARRAY_SER_VERSION_HEADER_NULL){
            throw new IllegalStateException("Unexpected NDArrayWritable version header - stream corrupt?");
        }

        if(header == NDARRAY_SER_VERSION_HEADER_NULL){
            array = null;
            return;
        }

        array = Nd4j.read(dis);
    }

    @Override
    public void writeType(DataOutput out) throws IOException {
        out.writeShort(WritableType.NDArray.typeIdx());
    }

    /** Serialize array data linearly. */
    public void write(DataOutput out) throws IOException {
        if (array == null) {
            out.write(NDARRAY_SER_VERSION_HEADER_NULL);
            return;
        }

        INDArray toWrite;
        if(array.isView()){
            toWrite = array.dup();
        } else {
            toWrite = array;
        }

        //Write version header: this allows us to maintain backward compatibility in the future,
        // with features such as compression, sparse arrays or changes on the DataVec side
        out.write(NDARRAY_SER_VERSION_HEADER);
        Nd4j.write(toWrite, new DataOutputStream(new DataOutputWrapperStream(out)));
    }

    public void set(INDArray array) {
        this.array = array;
    }

    public INDArray get() {
        return array;
    }

    /**
     * Returns true iff <code>o</code> is a ArrayWritable with the same value.
     */
    public boolean equals(Object o) {
        if (!(o instanceof NDArrayWritable)) {
            return false;
        }
        NDArrayWritable other = (NDArrayWritable) o;
        DataBuffer thisData = this.array.data();
        DataBuffer otherData = other.array.data();
        DataBuffer.Type thisType = thisData.dataType();
        DataBuffer.Type otherType = otherData.dataType();
        if (thisType != otherType) {
            throw new IllegalArgumentException("Data types must be the same.");
        }
        switch (thisType) {
            case DOUBLE:
                return thisData.asNioDouble().equals(otherData.asNioDouble());
            case FLOAT:
                return thisData.asNioFloat().equals(otherData.asNioFloat());
            case INT:
                return thisData.asNioInt().equals(otherData.asNioInt());
        }
        throw new UnsupportedOperationException("Unsupported data type: " + thisType);
    }

    public int hashCode() {
        DataBuffer data = array.data();
        DataBuffer.Type type = data.dataType();
        switch (type) {
            case DOUBLE:
                return data.asNioDouble().hashCode();
            case FLOAT:
                return data.asNioFloat().hashCode();
            case INT:
                return data.asNioInt().hashCode();
        }
        throw new UnsupportedOperationException("Unsupported data type: " + type);
    }

    public int compareTo(Object o) {
        NDArrayWritable other = (NDArrayWritable) o;
        DataBuffer thisData = this.array.data();
        DataBuffer otherData = other.array.data();
        DataBuffer.Type thisType = thisData.dataType();
        DataBuffer.Type otherType = otherData.dataType();
        if (thisType != otherType) {
            throw new IllegalArgumentException("Data types must be the same.");
        }
        switch (thisType) {
            case DOUBLE:
                return thisData.asNioDouble().compareTo(otherData.asNioDouble());
            case FLOAT:
                return thisData.asNioFloat().compareTo(otherData.asNioFloat());
            case INT:
                return thisData.asNioInt().compareTo(otherData.asNioInt());
        }
        throw new UnsupportedOperationException("Unsupported data type: " + thisType);
    }

    public String toString() {
        return array.toString();
    }

    /** A Comparator optimized for ArrayWritable. */
    public static class Comparator extends WritableComparator {
        public Comparator() {
            super(NDArrayWritable.class);
        }

        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
            ByteBuffer buffer1 = ByteBuffer.wrap(b1, s1, l1);
            ByteBuffer buffer2 = ByteBuffer.wrap(b2, s2, l2);
            long length1 = buffer1.getLong();
            long length2 = buffer2.getLong();
            if (length1 == 0 && length2 == 0) {
                return 0;
            } else if (length1 == 0) {
                return (int) Math.max(-length2, Integer.MIN_VALUE);
            } else if (length2 == 0) {
                return (int) Math.min(length1, Integer.MAX_VALUE);
            }
            int type1 = buffer1.getInt();
            int type2 = buffer2.getInt();
            if (type1 != type2) {
                throw new IllegalArgumentException("Data types must be the same.");
            }
            if (type1 == DataBuffer.Type.DOUBLE.ordinal()) {
                return buffer1.asDoubleBuffer().compareTo(buffer2.asDoubleBuffer());
            } else if (type1 == DataBuffer.Type.FLOAT.ordinal()) {
                return buffer1.asFloatBuffer().compareTo(buffer2.asFloatBuffer());
            } else if (type1 == DataBuffer.Type.INT.ordinal()) {
                return buffer1.asIntBuffer().compareTo(buffer2.asIntBuffer());
            } else {
                throw new UnsupportedOperationException("Unsupported data type: " + type1);
            }
        }
    }

    static { // register this comparator
        WritableComparator.define(NDArrayWritable.class, new Comparator());
    }

    @Override
    public long length() {
        return array.data().length();
    }

    @Override
    public double getDouble(long i) {
        return array.data().getDouble(i);
    }

    @Override
    public float getFloat(long i) {
        return array.data().getFloat(i);
    }

    @Override
    public int getInt(long i) {
        return array.data().getInt(i);
    }

    @Override
    public long getLong(long i) {
        return (long) array.data().getDouble(i);
    }
}
