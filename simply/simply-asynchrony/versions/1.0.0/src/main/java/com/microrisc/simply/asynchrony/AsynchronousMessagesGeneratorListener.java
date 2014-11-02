/* 
 * Copyright 2014 MICRORISC s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microrisc.simply.asynchrony;

/**
 * Listener of data comming from generator of asynchronous messages. 
 * 
 * @param <T> type of asynchronous messages
 * 
 * @author Michal Konopa
 */
public interface AsynchronousMessagesGeneratorListener
<T extends BaseAsynchronousMessage>
{
    /**
     * Will be called, when new asynchronous message has come from generator.
     * @param message arrived message
     */
    void onAsynchronousMessage(T message);
}
