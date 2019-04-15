package com.example.sound.devicesound;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class BitstreamIterator implements Iterable<Integer> {
    final InputStream stream;
    final int bits;

    public BitstreamIterator(InputStream stream, int bits) {
        this.stream = stream;
        this.bits = bits;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            final byte[] buffer = new byte[4096];
            int buffer_size = 0;

            int next_read_byte = 0;
            int next_read_bit = 0;

            @Override
            public boolean hasNext() {
                if (next_read_byte == buffer_size) {
                    next_read_byte = 0;
                    try {
                        buffer_size = stream.read(buffer, 0, buffer.length);
                    } catch (IOException e) {
                    //    Log.e("DEBUG", "IO fail", e);
                        buffer_size = 0;
                    }
                }

                return buffer_size > 0;
            }

            @Override
            public Integer next() {
                int out = 0;
                int bits_left = bits;

                while (bits_left > 0) {
                    if (!hasNext()) {
                        break;
                    }

                    int can_fill = Byte.SIZE - next_read_bit;
//                    Log.d("BIT",Integer.toString(can_fill));
//                    Log.d("Byte.SIZE",Integer.toString(Byte.SIZE));
//                    Log.d("next_read_bit",Integer.toString(next_read_bit));
//                    Log.d("BIT_LEFT",Integer.toString(bits_left));

                    int to_fill = Math.min(can_fill, bits_left);
//                    Log.d("to_fill",Integer.toString(to_fill));
                    int offset = Byte.SIZE - next_read_bit - to_fill;
 //                   Log.d("offset",Integer.toString(offset));
                    out <<= to_fill;
//                    Log.d("out",Integer.toString(out));
                    int shifted_bits =  buffer[next_read_byte] & (((1 << to_fill) - 1) << offset);
//                    Log.d("shifted_bits",Integer.toString(shifted_bits));
                    out |= shifted_bits >> offset;
                    bits_left -= to_fill;
                    next_read_bit += to_fill;
//                    Log.d("bits_left",Integer.toString(bits_left));
//                    Log.d("next_read_bit",Integer.toString(next_read_bit));
//                    Log.d("next_read_byte",Integer.toString(next_read_byte));
//                    Log.d("buffer",Integer.toString((buffer[next_read_byte])));

                    if (next_read_bit >= Byte.SIZE) {
                        ++next_read_byte;
                        next_read_bit -= Byte.SIZE;
                    }
                }

                //Log.i("BitStream", "yield -> " + out);
                return out;
            }

            @Override
            public void remove() {
            }
        };
    }
}
