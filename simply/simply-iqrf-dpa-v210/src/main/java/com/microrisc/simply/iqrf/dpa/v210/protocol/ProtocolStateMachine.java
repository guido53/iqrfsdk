/*
 * Copyright 2014 MICRORISC s.r.o..
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

package com.microrisc.simply.iqrf.dpa.v210.protocol;

import com.microrisc.simply.CallRequest;
import com.microrisc.simply.ManageableObject;
import com.microrisc.simply.SimplyException;
import com.microrisc.simply.iqrf.dpa.broadcasting.BroadcastRequest;
import com.microrisc.simply.iqrf.dpa.v210.types.DPA_Confirmation;
import java.util.Arrays;
import org.slf4j.LoggerFactory;

/**
 * State machine for better handling individual states within the process of 
 * DPA protocol's message exchange. 
 * 
 * @author Michal Konopa
 */
public final class ProtocolStateMachine implements ManageableObject {
    /** Logger. */
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProtocolStateMachine.class);

    
    /**
     * States of the machine.
     */
    private enum State {
        FREE_FOR_SEND,
        WAITING_FOR_CONFIRMATION,
        WAITING_AFTER_CONFIRMATION,
        WAITING_FOR_RESPONSE,
        WAITING_AFTER_RESPONSE
    } 
    
    // actual state
    private State actualState = State.FREE_FOR_SEND;
    
    // object for synchronized access to entities
    private final Object synchroObjects = new Object();
    
    private final Object synchroWaiting = new Object();
    
    // counts timeslot length
    private static long countTimeslotLength(int responseDataLength) {
        if ( responseDataLength < 19 ) {
            return 80;
        }
        if ( responseDataLength < 41 ) {
            return 90;
        }
        return 100;
    }
    
    private long countWaitingTimeAfterResponse() {
        long actualRespTimeslotLength = countTimeslotLength(responseDataLength);
        
        if ( countWithConfirmation ) {
            if ( confirmation == null ) {
                throw new IllegalStateException(
                        "Confirmation needed for calculation of waiting time after response "
                                + "but not present."
                );
            }
            return ( confirmation.getHops() + 1 ) * confirmation.getTimeslotLength() * 10
                + ( confirmation.getHopsResponse() + 1 ) * actualRespTimeslotLength  * 10
                - (System.currentTimeMillis() - responseRecvTime);
        }
        
        return ( actualRespTimeslotLength * 10 ) - (System.currentTimeMillis() - responseRecvTime);
    }
    
    private long countWaitingTimeAfterConfirmation() {
        return ( confirmation.getHops() + 1 ) * confirmation.getTimeslotLength() * 10
                - (System.currentTimeMillis() - responseRecvTime);
    }
    
    // counts waiting time for specified state
    private long countWaitingTime(State state) {
        if ( state == State.WAITING_AFTER_RESPONSE ) {
            return countWaitingTimeAfterResponse();
        }
        
        if ( state == State.WAITING_AFTER_CONFIRMATION ) {
            return countWaitingTimeAfterConfirmation();
        }
        
        throw new IllegalArgumentException("Incorrect state: " + state);
    }
    
    
    private class WaitingTimeCounter extends Thread {
        @Override
        public void run() {
            while ( true ) {
                if ( this.isInterrupted() ) {
                    logger.info("Waiting time counter end");
                    return;
                }
                
                // waiting for signal for waiting
                synchronized ( synchroWaiting ) {
                    try {
                        synchroWaiting.wait();
                    } catch ( InterruptedException ex ) {
                        logger.warn("Waiting time counter interrupted while waiting", ex);
                        return;
                    }
                }

                long waitingTime = 0;
                synchronized ( synchroObjects ) {
                    switch ( actualState ) {
                        case WAITING_AFTER_CONFIRMATION:
                        case WAITING_AFTER_RESPONSE:
                            waitingTime = countWaitingTime(actualState);
                            if ( waitingTime < 0 ) {
                                waitingTime = 0;
                            }
                            break;
                        default:
                            logger.error(
                                "Bad state for waiting: {}. Waiting time counter finished", actualState
                            );
                            return;
                    }
                    
                    // countWithConfirmation already used
                    if ( actualState == ProtocolStateMachine.State.WAITING_AFTER_RESPONSE ) {
                        countWithConfirmation = false;
                    }
                }
                
                logger.info("Time to wait: {}", waitingTime);
                
                try {
                    Thread.sleep(waitingTime);
                } catch ( InterruptedException ex ) {
                    logger.warn(
                        "Waiting time counter interrupted while waiting on routing end", ex
                    );
                    return;
                }

                // updating actual state and listener notification
                synchronized ( synchroObjects ) {
                    actualState = ProtocolStateMachine.State.FREE_FOR_SEND;
                    if ( listener != null ) {
                        listener.onFreeForSend();
                    }
                }
                
                logger.info("Free for send");
            }
        }
    }
    
    /** Waiting time counter thread. */
    private Thread waitingTimeCounter = null;
    
    // timeout to wait for worker threads to join
    private static final long JOIN_WAIT_TIMEOUT = 2000;
    
    /**
     * Terminates waiting time counter thread.
     */
    private void terminateWaitingTimeCounter() {
        logger.debug("terminateWaitingTimeCounter - start:");
        
        // termination signal
        waitingTimeCounter.interrupt();
        
        // indicates, wheather this thread is interrupted
        boolean isInterrupted = false;
        
        try {
            if ( waitingTimeCounter.isAlive( )) {
                waitingTimeCounter.join(JOIN_WAIT_TIMEOUT);
            }
        } catch ( InterruptedException e ) {
            isInterrupted = true;
            logger.warn("waiting time counter terminating - thread interrupted");
        }
        
        if ( !waitingTimeCounter.isAlive() ) {
            logger.info("Waiting time counter stopped.");
        }
        
        if ( isInterrupted ) {
            Thread.currentThread().interrupt();
        }
        
        logger.debug("terminateWaitingTimeCounter - end");
    }
    
    
    // time of reception of a confirmation
    private long confirmRecvTime = -1;
    
    // received confirmation
    private DPA_Confirmation confirmation = null;
    
    // indicates, wheather to count with confirmation in calculation of 
    // waiting time
    private boolean countWithConfirmation = false;
    
    // time of reception of a response
    private long responseRecvTime = -1;
    
    // response data
    private int responseDataLength = -1;
    
    // listener
    private ProtocolStateMachineListener listener = null;
    
    private boolean isRequestForCoordinator(CallRequest request) {
        return request.getNodeId().equals("0");
    }
    
    
    public ProtocolStateMachine() {
        waitingTimeCounter = new WaitingTimeCounter();
    }
    
    @Override
    public void start() throws SimplyException {
        logger.debug("start - start:");
        
        waitingTimeCounter.start();
        
        logger.info("Protocol Machine started");
        logger.debug("start - end");
    }
    
    /**
     * Registers specified listener.
     * @param listener listener to register
     */
    public void registerListener(ProtocolStateMachineListener listener) {
        logger.debug("registerListener - start: listener={}", listener);
        
        synchronized ( synchroObjects ) {
            this.listener = listener;
        }
        
        logger.info("Listener registered.");
        logger.debug("registerListener - end");
    } 
    
    /**
     * Unregister previously registered listener.
     * Does nothing, if there is no registered listener.
     */
    public void unregisterListener() {
        logger.debug("unregisterListener - start: ");
        
        synchronized ( synchroObjects ) {
            this.listener = null;
        }
        
        logger.info("Listener unregistered.");
        logger.debug("unregisterListener - end");
    }
    
    /**
     * Indicates, wheather it is possible to send next request.
     * @return {@code true} if it is possible to send next request
     *         {@code false} otherwise
     */
    public boolean isFreeForSend() {
        logger.debug("isFreeForSend - start: ");
        
        boolean isFreeForSend = false;
        synchronized ( synchroObjects ) {
            isFreeForSend = ( actualState == State.FREE_FOR_SEND ); 
        }
        
        logger.debug("isFreeForSend - end: {}", isFreeForSend);
        return isFreeForSend;
    }
    
    /**
     * Informs the machine, that new request has been sent.
     * @param request sent request
     */
    public void newRequest(CallRequest request) {
        logger.debug("newRequest - start: request={}", request);
        
        synchronized ( synchroObjects ) {
            if ( actualState != State.FREE_FOR_SEND ) {
                throw new IllegalArgumentException(
                    "Cannot send new request because in the " + actualState + " state."
                );
            }

            if ( request instanceof BroadcastRequest ) {
                actualState = State.WAITING_FOR_CONFIRMATION;
                return;
            }

            if ( isRequestForCoordinator(request) ) {
                actualState = State.WAITING_FOR_RESPONSE;
                countWithConfirmation = false;
            } else {
                actualState = State.WAITING_FOR_CONFIRMATION;
                confirmation = null;
                countWithConfirmation = true;
            }
        }
        
        logger.debug("newRequest - end");
    }
    
    /**
     * Informs the machine, that confirmation has been received.
     * @param recvTime time of confirmation reception
     * @param confirmation received confirmation
     */
    public void confirmationReceived(long recvTime, DPA_Confirmation confirmation) {
        logger.debug("confirmationReceived - start: recvTime={}, confirmation={}",
                recvTime, confirmation
        );
        
        synchronized ( synchroObjects ) { 
            if ( actualState != State.WAITING_FOR_CONFIRMATION ) {
                throw new IllegalArgumentException(
                    "Unexpected reception of confirmation. Actual state: " + actualState
                );
            }

            // broadcast - no response
            if ( confirmation.getHopsResponse() == 0 ) {
                actualState = State.WAITING_AFTER_CONFIRMATION;
                synchronized ( synchroWaiting ) {
                    synchroWaiting.notifyAll();
                }
            } else {
                actualState = State.WAITING_FOR_RESPONSE;
            }
            
            this.confirmation = confirmation;
            this.confirmRecvTime = recvTime;
        }
        
        logger.debug("confirmationReceived - end");
    }
    
    /**
     * Informs the machine, that confirmation has been received. Time of calling 
     * of this method will be used as the time of the confirmation reception.
     * @param confirmation received confirmation
     */
    public void confirmationReceived(DPA_Confirmation confirmation) {
        confirmationReceived(System.currentTimeMillis(), confirmation);
    }
    
    /**
     * Informs the machine, that response has been received.
     * @param recvTime time of response reception
     * @param responseData data of the received response
     */
    public void responseReceived(long recvTime, short[] responseData) {
        logger.debug("responseReceived - start: recvTime={}, responseData={}",
                recvTime, Arrays.toString(responseData)
        );
        
        synchronized ( synchroObjects ) {
            if ( actualState != State.WAITING_FOR_RESPONSE ) {
                throw new IllegalArgumentException(
                    "Unexpected reception of the response. Actual state: " + actualState
                );
            }
            
            this.responseRecvTime = recvTime;
            this.responseDataLength = responseData.length;
            this.actualState = State.WAITING_AFTER_RESPONSE;
            
            synchronized ( synchroWaiting ) {
                synchroWaiting.notifyAll();
            }
        }
        
        logger.debug("responseReceived - end");
    }
    
    /**
     * Informs the machine, that response has been received. Time of calling of
     * this method will be used as the time of the response reception.
     * @param responseData data of the received response
     */
    public void responseReceived(short[] responseData) {
        responseReceived(System.currentTimeMillis(), responseData);
    }
    
    @Override
    public void destroy() {
        logger.debug("destroy - start:");
        
        terminateWaitingTimeCounter();
        
        logger.info("Destroyed.");
        logger.debug("destroy - end");
    }
}