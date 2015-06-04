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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;

public class HandlerHelper {

    private static class WaitHandler extends Handler {

        private final Object lock;

        public Object reply;

        public boolean releaseLock;

        private WaitHandler(Looper looper, Object lock) {
            super(looper);
            this.lock = lock;
        }

        @Override
        public void handleMessage(Message msg) {
            reply = msg.obj;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    private final ArrayList<WaitHandler> mMessageList = new ArrayList<>();

    private final Object mListLock = new Object();

    public Object sendMessageAndAwaitResponse(Message msg) {
        HandlerThread waitThread = new HandlerThread("sendMessageAndAwaitResponse");
        final Object lock = new Object();
        waitThread.start();

        WaitHandler handler = new WaitHandler(waitThread.getLooper(), lock);
        synchronized (mListLock) {
            mMessageList.add(handler);
        }

        msg.obj = handler;
        msg.sendToTarget();

        Object reply = null;

        synchronized (lock) {
            while (reply == null && !handler.releaseLock) {
                try {
                    lock.wait(10);
                    reply = handler.reply;
                } catch (InterruptedException e) {
                }
            }
        }

        waitThread.quit();

        synchronized (mListLock) {
            mMessageList.remove(handler);
        }

        return reply;
    }

    public void releaseAllLocks() {
        synchronized (mListLock) {
            for (WaitHandler handler : mMessageList) {
                synchronized (handler.lock) {
                    handler.releaseLock = true;
                    handler.lock.notifyAll();
                }
            }
        }
    }
}
