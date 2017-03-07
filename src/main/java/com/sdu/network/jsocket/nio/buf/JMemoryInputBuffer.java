package com.sdu.network.jsocket.nio.buf;

/**
 *
 * @author hanhan.zhang
 * */
public class JMemoryInputBuffer {

    private byte[] _buf;
    private int _pos;
    private int _endPos;

    public JMemoryInputBuffer() {

    }

    public JMemoryInputBuffer(byte[] bytes) {
        reset(bytes);
    }

    public void reset(byte[] buf) {
        reset(buf, 0, buf.length);
    }

    public void reset(byte[] buf, int offset, int length) {
        _buf = buf;
        _pos = offset;
        _endPos = offset + length;
    }

    public void clear() {
        _buf = null;
    }

    public int getBufferLength() {
        return _buf.length;
    }

    public byte[] getBuffer() {
        return _buf;
    }

    public int getBufferPosition() {
        return _pos;
    }

    public int getBytesRemainingInBuffer() {
        return _endPos - _pos;
    }
}
