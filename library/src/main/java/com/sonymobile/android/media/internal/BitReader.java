/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.sonymobile.android.media.internal;

public class BitReader {

    private final byte[] mBytes;

    private int mPos = 0;

    public BitReader(byte[] byteData) {
        mBytes = byteData.clone();
        mPos = 0;
    }

    public void skipBits(int nbrBits) {
        mPos += nbrBits;
    }

    private int getBit(int pos) {
        int currentByte = 0xFF & mBytes[pos / 8];

        int bitIndex = pos % 8;
        return 0x01 & (currentByte >> (7 - bitIndex));
    }

    public int getBits(int nbrBits) {
        int bitValue = 0;

        for (int i = 0; i < nbrBits; ++i) {
            bitValue = bitValue << 1;
            bitValue |= getBit(mPos);
            mPos++;
        }

        return bitValue;
    }
}
