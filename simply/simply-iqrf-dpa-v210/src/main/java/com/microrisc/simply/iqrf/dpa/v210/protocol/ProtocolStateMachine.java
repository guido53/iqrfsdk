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
 * State machine for better handling of individual states within the process of 
 * DPA protocol's message exchange. 
 * 
 * @author Michal Konopa
 */
final class ProtocolStateMachine implements ManageableObject {
    /** Logger. */
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProtocolStateMachine.class);

    /**
     * Events, which occur during DPA protocol running, 
     * e.g confirmation arrival, response arrival etc. 
     */
    private static class Event {}
    
    private static class NewRequestEvent extends Event {
        CallRequest request;
        boolean countWithConfirmation = false;
        
        NewRequestEvent( CallRequest request) {
            this.request = request;
        }
    }
    
    private static class ConfirmationReceivedEvent extends Event {
        long recvTime;
        DPA_Confirmation confirmation;
        
        ConfirmationReceivedEvent(long recvTime, DPA_Confirmation confirmation) {
            this.recvTime = recvTime;
            this.confirmation = confirmation;
        }
    }
    
    private static class ResponseReceivedEvent extends Event {
        long recvTime;
        short[] responseData;

        ResponseReceivedEvent(long recvTime, short[] responseData) {
            this.recvTime = recvTime;
            this.responseData = responseData;
        }
    }
    
    private static class ResetEvent extends Event {}
    
    // new event
    private Event newEvent = null;
    
    // synchronization object for access to newEvent
    private final Object synchroNewEvent = new Object();
    
    
    /**
     * States of the machine.
     */
    public static enum State {
        FREE_FOR_SEND,
        WAITING_FOR_CONFIRMATION,
        WAITING_FOR_CONFIRMATION_ERROR,
        WAITING_AFTER_CONFIRMATION,
        WAITING_FOR_RESPONSE,
        WAITING_FOR_RESPONSE_ERROR,
        WAITING_AFTER_RESPONSE
    } 
    
    // actual state
    private State actualState = State.FREE_FOR_SEND;
    
    // synchronization object for actualState
    private final Object synchroActualState = new Object();
    
    
    // first new state after processing of new event
    // IMPORTANT: some states are NOT fixed but they are rather timeout dependent.
    //      For example WAITING_AFTER_CONFIRMATION. After specific timeout
    //      elapses and no events come during that timeout, the next state will 
    //      be FREE_FOR_SEND.    
    private State firstNewStateAfterEvent = null;
    
    // signal of state change
    private final Object stateChangeSignal = new Object();
    
    // event triggered state change timeout [in ms]
    private static final long STATE_CHANGE_TIMEOUT = 1000;
    
    // waits for specified state to come in
    private void waitForStateChangeSignal(State stateToWaitFor) {
        synchronized ( stateChangeSignal ) {
            while ( firstNewStateAfterEvent != stateToWaitFor ) {
                try {
                    long startTime = System.currentTimeMillis();
                    stateChangeSignal.wait(STATE_CHANGE_TIMEOUT);
                    long endTime = System.currentTimeMillis();
                    if ( (endTime - startTime) >= STATE_CHANGE_TIMEOUT ) {
                        throw new IllegalStateException (
                            "Waiting for state: " + stateToWaitFor  +  " timeouted."
                        );
                    }
                } catch ( InterruptedException ex ) {
                    logger.warn("Waiting for next exptected state interrupted.");
                }
            }
        }
    }
    
    
    /** Default time to wait for confirmation [ in ms ]. */
    public static final long TIME_TO_WAIT_FOR_CONFIRMATION_DEFAULT = 500;
    
    // actual time to wait for confirmation
    private volatile long timeToWaitForConfirmation = TIME_TO_WAIT_FOR_CONFIRMATION_DEFAULT;
    
    
    /** Default base time to wait for response [ in ms ]. */
    public static final long BASE_TIME_TO_WAIT_FOR_RESPONSE_DEFAULT = 500;
    
    // actual base time to wait for response
    private volatile long baseTimeToWaitForResponse = BASE_TIME_TO_WAIT_FOR_RESPONSE_DEFAULT;
    
    
    // counts timeslot length in 10 ms units
    private static long countTimeslotLength(int responseDataLength) {
        if ( responseDataLength < 19 ) {
            return 8;
        }
        if ( responseDataLength < 41 ) {
            return 9;
        }
        return 10;
    }
    
    private long countWaitingTimeForConfirmation() {
        return timeToWaitForConfirmation;
    }
    
    private long countWaitingTimeForResponse() {
        long requestRoutingTime = 0;
        if ( countWithConfirmation ) {
            requestRoutingTime = (confirmation.getHops() + 1) * confirmation.getTimeslotLength() * 10;
            return baseTimeToWaitForResponse + requestRoutingTime + 100;
        }
        
        return baseTimeToWaitForResponse + 100;
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
    
    private long countWaitingTime() {
        long waitingTime = 0;
        switch ( actualState ) {
            case FREE_FOR_SEND:
                waitingTime = 0;
                break;
            case WAITING_FOR_CONFIRMATION:
                waitingTime = countWaitingTimeForConfirmation();
                break;
            case WAITING_FOR_RESPONSE:
                waitingTime = countWaitingTimeForResponse();
                break;
            case WAITING_AFTER_CONFIRMATION:
                waitingTime = countWaitingTimeAfterConfirmation();
                break;
            case WAITING_AFTER_RESPONSE:
                waitingTime = countWaitingTimeAfterResponse();
                break;
            default:
                throw new IllegalStateException("Incorrect state to start waiting from: " + actualState);
        }

        if ( waitingTime < 0 ) {
            waitingTime = 0;
        }
        
        return waitingTime;
    }
    
    
    private class WaitingTimeCounter extends Thread {
        
        private void doTransitionForNewRequest() {
            synchronized ( synchroActualState ) {
                if ( request instanceof BroadcastRequest ) {
                    actualState = ProtocolStateMachine.State.WAITING_FOR_CONFIRMATION;
                } else {
                    if ( isRequestForCoordinator(request) ) {
                        actualState = ProtocolStateMachine.State.WAITING_FOR_RESPONSE;
                    } else {
                        actualState = ProtocolStateMachine.State.WAITING_FOR_CONFIRMATION;
                    }
                }
            }
        }
        
        // will next state be: waiting after confirmation or waiting for response?
        private void doTransitionForWaitingForConfirmation() {
            synchronized ( synchroActualState ) {
                if ( willWaitForResponse ) {
                    actualState = ProtocolStateMachine.State.WAITING_FOR_RESPONSE;
                } else {
                    actualState = ProtocolStateMachine.State.WAITING_AFTER_CONFIRMATION;
                }
            }
        }
        
        // do transition from current state to next state
        private void doTransition() {
            switch ( actualState ) {
                case FREE_FOR_SEND:
                    doTransitionForNewRequest();
                    break;
                case WAITING_FOR_CONFIRMATION:
                    doTransitionForWaitingForConfirmation();
                    break;
                case WAITING_FOR_RESPONSE:
                    actualState = ProtocolStateMachine.State.WAITING_AFTER_RESPONSE;
                    break;
                case WAITING_AFTER_CONFIRMATION:
                case WAITING_AFTER_RESPONSE:
                    actualState = ProtocolStateMachine.State.FREE_FOR_SEND;
                    synchronized ( synchroListener ) {
                        if ( listener != null ) {
                            listener.onFreeForSend();
                        }
                    }
                    break;
                case WAITING_FOR_CONFIRMATION_ERROR:
                    actualState = ProtocolStateMachine.State.FREE_FOR_SEND;
                    synchronized ( synchroListener ) {
                        if ( listener != null ) {
                            listener.onFreeForSend();
                        }
                    }
                    break;
                case WAITING_FOR_RESPONSE_ERROR:
                    actualState = ProtocolStateMachine.State.FREE_FOR_SEND;
                    synchronized ( synchroListener ) {
                        if ( listener != null ) {
                            listener.onFreeForSend();
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException("Incorrect state to wait in: " + actualState);
            }
        }
        
        // do error transition - required event doesn't come in timeout
        private void doErrorTransition() {
            switch ( actualState ) {
                case WAITING_FOR_CONFIRMATION:
                    actualState = ProtocolStateMachine.State.WAITING_FOR_CONFIRMATION_ERROR;
                    synchronized ( synchroListener ) {
                        if ( listener != null ) {
                            listener.onConfirmationTimeouted();
                        }
                    }
                    break;
                case WAITING_FOR_RESPONSE:
                    actualState = ProtocolStateMachine.State.WAITING_FOR_RESPONSE_ERROR;
                    synchronized ( synchroListener ) {
                        if ( listener != null ) {
                            listener.onResponseTimeouted();
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException("Cannot do error transition for state: " + actualState);
            }
        }
        
        // consumes new event
        private void consumeNewEvent() {
            synchronized ( synchroNewEvent ) {
                if ( newEvent instanceof NewRequestEvent ) {
                    request = ((NewRequestEvent)newEvent).request;
                    countWithConfirmation = ((NewRequestEvent)newEvent).countWithConfirmation;
                    if ( ((NewRequestEvent)newEvent).request instanceof BroadcastRequest ) {
                        willWaitForResponse = false;
                    } else {
                        willWaitForResponse = true;
                    }
                } else if ( newEvent instanceof ConfirmationReceivedEvent ) {
                    confirmation = ((ConfirmationReceivedEvent)newEvent).confirmation;
                    confirmRecvTime = ((ConfirmationReceivedEvent)newEvent).recvTime; 
                } else if ( newEvent instanceof ResponseReceivedEvent ) {
                    responseDataLength = ((ResponseReceivedEvent)newEvent).responseData.length;
                    responseRecvTime =((ResponseReceivedEvent)newEvent).recvTime;
                } else {
                }
                
                newEvent = null;
            }
        }
        
        @Override
        public void run() {
            // time to wait in some states ( waiting states )
            long waitingTime = 0;
            
            while ( true ) {
                if ( this.isInterrupted() ) {
                    logger.info("Waiting time counter end");
                    return;
                }
                
                // indicates, wheather waiting for new event timeouted 
                // more precisely: waiting on some type of events 
                boolean timeouted = false;
                
                // waiting on new event
                synchronized ( synchroNewEvent ) {
                    while ( newEvent == null ) {
                        // states, where it is possible to wait in for potentionaly
                        // unlimited amount of time
                        if ( actualState == ProtocolStateMachine.State.FREE_FOR_SEND
                            || actualState == ProtocolStateMachine.State.WAITING_FOR_CONFIRMATION_ERROR
                            || actualState == ProtocolStateMachine.State.WAITING_FOR_RESPONSE_ERROR    
                        ) {
                            try {
                                synchroNewEvent.wait();
                            } catch ( InterruptedException ex ) {
                                logger.warn("Waiting time counter interrupted while waiting on new event");
                                return;
                            }
                        }
                        
                        // states, where new event must come in in a limited amount of time
                        else if (
                                actualState == ProtocolStateMachine.State.WAITING_FOR_CONFIRMATION
                                || actualState == ProtocolStateMachine.State.WAITING_FOR_RESPONSE
                          ) {
                            try {
                                long startTime = System.currentTimeMillis();
                                synchroNewEvent.wait(waitingTime);
                                long endTime = System.currentTimeMillis();
                                
                                if ( (endTime - startTime) >= waitingTime ) {
                                    timeouted = true;
                                    // IMPORTANT !!! Go out of a while-cycle.
                                    break;
                                }
                            } catch ( InterruptedException ex ) {
                                logger.warn("Waiting time counter interrupted while waiting on new event");
                                return;
                            }
                        }
                        
                        // AFTER states: it is mandatory to wait for a minimal
                        // amount of time
                        else {
                            try {
                                Thread.sleep(waitingTime);
                            } catch ( InterruptedException ex ) {
                                logger.warn("Waiting time counter interrupted while sleeping in 'AFTER' state");
                                return;
                            }
                           
                            // IMPORTANT !!! Go out of a while-cycle.
                            break;
                        }
                    }
                    
                    if ( newEvent != null ) {
                        // consume new event icluding update of variables by
                        // information present in the event
                        consumeNewEvent();
                    }
                }
                
                // if waiting for new event timeouted, do error transition
                if ( timeouted ) {
                    doErrorTransition();
                    continue;
                }
                
                // do transition to next state
                doTransition();
                
                // count waiting time - can be 0 if there is no need for mandatory waiting, 
                // for example for FREE_FOR_SEND state
                waitingTime = countWaitingTime();
                
                // notify waiting clients about the state change
                synchronized ( stateChangeSignal ) {
                    firstNewStateAfterEvent = actualState;
                    stateChangeSignal.notifyAll();
                }
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
    
    // request
    private CallRequest request = null;
    
    // time of reception of a confirmation
    private long confirmRecvTime = -1;
    
    // received confirmation
    private DPA_Confirmation confirmation = null;
    
    // indicates, wheather to count with confirmation in calculation of 
    // waiting time
    private boolean countWithConfirmation = false;
    
    // if machine will wait for a reponse, or will wait for end of confirmation routing
    // this applies only to broadcast as there are not any responses for broadcast requests
    private boolean willWaitForResponse = false;
    
    // time of reception of a response
    private long responseRecvTime = -1;
    
    // response data
    private int responseDataLength = -1;
   
    
    // listener
    private ProtocolStateMachineListener listener = null;
    
    // synchronization object for listener
    private final Object synchroListener = new Object();
    
    
    private boolean isRequestForCoordinator(CallRequest request) {
        return request.getNodeId().equals("0");
    }
    
    private static long checkTimeToWaitForConfirmation(long time) {
        if ( time < 0 ) {
            throw new IllegalArgumentException(
                    "Time to wait for confirmation cannot be less then 0"
            );
        }
        return time;
    }
    
    private static long checkBaseTimeToWaitForResponse(long time) {
        if ( time < 0 ) {
            throw new IllegalArgumentException(
                    "Base time to wait for response cannot be less then 0"
            );
        }
        return time;
    }
    
    
    /**
     * Creates new object of Protocol Machine.
     */
    public ProtocolStateMachine() {
        waitingTimeCounter = new WaitingTimeCounter();
        logger.info("Protocol machine successfully created.");
    }
    
    /**
     * Returns actual value of time to wait for confirmation arrival [ in ms ].
     * @return actual value of time to wait for confirmation arrival
     */
    synchronized public long getTimeToWaitForConfirmation() {
        return timeToWaitForConfirmation;
    }
    
    /**
     * Sets time to wait for confirmation arrival.
     * @param time new value of time [ in ms ] to wait for confirmation, cannot be less then 0
     * @throws IllegalArgumentException if specified time is less then 0
     */
    synchronized public void setTimeToWaitForConfirmation(long time) {
        this.timeToWaitForConfirmation = checkTimeToWaitForConfirmation(time);
    }
    
    /**
     * Returns actual value of base time to wait for response arrival [ in ms ].
     * @return actual value of base time to wait for response arrival
     */
    synchronized public long getBaseTimeToWaitForResponse() {
        return baseTimeToWaitForResponse;
    }
    
    /**
     * Sets base time to wait for response arrival.
     * @param time new value of base time [ in ms ] to wait for response, cannot be less then 0
     * @throws IllegalArgumentException if specified time is less then 0
     */
    synchronized public void setBaseTimeToWaitForResponse(long time) {
        this.baseTimeToWaitForResponse = checkBaseTimeToWaitForResponse(time);
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
        
        synchronized ( synchroListener ) {
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
        
        synchronized ( synchroListener ) {
            this.listener = null;
        }
        
        logger.info("Listener unregistered.");
        logger.debug("unregisterListener - end");
    }
    
    /**
     * Returns the actual state of the machine.
     * @return the actual state of the machine
     */
    synchronized public State getState() {
        logger.debug("getState - start: ");
        
        State state = null;
        synchronized ( synchroActualState ) {
            state = actualState;
        }
        
        logger.debug("getState - end: {}", state);
        return state;
    }
    
    /**
     * Indicates, wheather it is possible to send next request.
     * @return {@code true} if it is possible to send next request
     *         {@code false} otherwise
     */
    synchronized public boolean isFreeForSend() {
        logger.debug("isFreeForSend - start: ");
        
        boolean isFreeForSend = false;
        synchronized ( synchroActualState ) {
            isFreeForSend = ( actualState == State.FREE_FOR_SEND ); 
        }
        
        logger.debug("isFreeForSend - end: {}", isFreeForSend);
        return isFreeForSend;
    }
    
    /**
     * Informs the machine, that new request has been sent.
     * @param request sent request
     */
    synchronized public void newRequest(CallRequest request) {
        logger.debug("newRequest - start: request={}", request);
        
        // actual state must be FREE FOR SEND
        synchronized ( synchroActualState ) {
            if ( actualState != State.FREE_FOR_SEND ) {
                throw new IllegalArgumentException(
                    "Cannot send new request because in the " + actualState + " state."
                );
            }
        }
        
        State nextExpectedState = null; 
        
        // signaling that new event has come in and what is the next expected state
        synchronized ( synchroNewEvent ) {
            newEvent = new NewRequestEvent(request);
            if ( request instanceof BroadcastRequest ) {
                nextExpectedState = State.WAITING_FOR_CONFIRMATION;
            } else {
                if ( isRequestForCoordinator(request) ) {
                    ((NewRequestEvent)newEvent).countWithConfirmation = false;
                    nextExpectedState = State.WAITING_FOR_RESPONSE;
                } else {
                    ((NewRequestEvent)newEvent).countWithConfirmation = true;
                    nextExpectedState = State.WAITING_FOR_CONFIRMATION; 
                }
            }
            synchroNewEvent.notifyAll();
        }
        
        // waiting till actual state changes to the expected one
        waitForStateChangeSignal(nextExpectedState);
        
        logger.debug("newRequest - end");
    }
    
    /**
     * Informs the machine, that confirmation has been received.
     * @param recvTime time of confirmation reception
     * @param confirmation received confirmation
     * @throws IllegalArgumentException if the machine is not in {@code WAITING_FOR_CONFIRMATION} state
     * @throws StateTimeoutedException if {@code WAITING_FOR_CONFIRMATION} state was
     *         timeouted during processing of the specified confirmation
     */
    synchronized public void confirmationReceived(long recvTime, DPA_Confirmation confirmation)
        throws StateTimeoutedException 
    {
        logger.debug("confirmationReceived - start: recvTime={}, confirmation={}",
                recvTime, confirmation
        );
        
        synchronized ( synchroActualState ) {
            if ( actualState != State.WAITING_FOR_CONFIRMATION ) {
                throw new IllegalArgumentException(
                    "Unexpected reception of confirmation. Actual state: " + actualState
                );
            }
        }
        
        State nextExpectedState = null; 
        
        // signaling that confirmation has come in
        synchronized ( synchroNewEvent ) {
            newEvent = new ConfirmationReceivedEvent(recvTime, confirmation);
            
            // broadcast - no response
            if ( confirmation.getHopsResponse() == 0 ) {
                nextExpectedState = State.WAITING_AFTER_CONFIRMATION;
            } else {
                nextExpectedState = State.WAITING_FOR_RESPONSE;
            }
            synchroNewEvent.notifyAll();
        }
        
        try {
            waitForStateChangeSignal(nextExpectedState);
        } catch ( IllegalStateException e ) {
            State actualStateCopy = null;
            synchronized ( synchroActualState ) {
                actualStateCopy = actualState;
            }
            if ( actualStateCopy == State.WAITING_FOR_CONFIRMATION_ERROR ) {
                throw new StateTimeoutedException("Waiting on confirmation timeouted.");
            } else {
                throw e;
            }
        }
        
        logger.debug("confirmationReceived - end");
    }
    
    /**
     * Informs the machine, that confirmation has been received. Time of calling 
     * of this method will be used as the time of the confirmation reception.
     * @param confirmation received confirmation
     * @throws IllegalArgumentException if the machine is not in {@code WAITING_FOR_CONFIRMATION} state
     * @throws StateTimeoutedException if {@code WAITING_FOR_CONFIRMATION} state was
     *         timeouted during processing of the specified confirmation
     */
    synchronized public void confirmationReceived(DPA_Confirmation confirmation) 
            throws StateTimeoutedException 
    {
        confirmationReceived(System.currentTimeMillis(), confirmation);
    }
    
    /**
     * Informs the machine, that response has been received.
     * @param recvTime time of response reception
     * @param responseData data of the received response
     * @throws IllegalArgumentException if the machine is not in {@code WAITING_FOR_RESPONSE} state
     * @throws StateTimeoutedException if {@code WAITING_FOR_RESPONSE} state was
     *         timeouted during processing of the specified response data
     */
    synchronized public void responseReceived(long recvTime, short[] responseData) 
        throws StateTimeoutedException 
    {
        logger.debug("responseReceived - start: recvTime={}, responseData={}",
                recvTime, Arrays.toString(responseData)
        );
        
        synchronized ( synchroActualState ) {
            if ( actualState != State.WAITING_FOR_RESPONSE ) {
                throw new IllegalArgumentException(
                    "Unexpected reception of the response. Actual state: " + actualState
                );
            }
        }
        
        State nextExpectedState = null;
        
        synchronized ( synchroNewEvent ) {
            newEvent = new ResponseReceivedEvent(recvTime, responseData);
            nextExpectedState = State.WAITING_AFTER_RESPONSE;
            synchroNewEvent.notifyAll();
        }
        
        try {
            waitForStateChangeSignal(nextExpectedState);
        } catch ( IllegalStateException e ) {
            State actualStateCopy = null;
            synchronized ( synchroActualState ) {
                actualStateCopy = actualState;
            }
            if ( actualStateCopy == State.WAITING_FOR_CONFIRMATION_ERROR ) {
                throw new StateTimeoutedException("Waiting on response timeouted.");
            } else {
                throw e;
            }
        }
        
        logger.debug("responseReceived - end");
    }
    
    /**
     * Informs the machine, that response has been received. Time of calling of
     * this method will be used as the time of the response reception.
     * @param responseData data of the received response
     * @throws StateTimeoutedException if {@code WAITING_FOR_RESPONSE} state was
     *         timeouted during processing of the specified response data
     */
    synchronized public void responseReceived(short[] responseData) 
            throws StateTimeoutedException  
    {
        responseReceived(System.currentTimeMillis(), responseData);
    }
    
    /**
     * Reseting the machine after some of error states has occured. 
     */
    synchronized public void resetAfterError() {
        logger.debug("resetAfterError - start:");
        
        synchronized ( synchroActualState ) {
            if ( 
                (actualState != State.WAITING_FOR_CONFIRMATION_ERROR)
                && (actualState != State.WAITING_FOR_RESPONSE_ERROR)     
            ) {
                throw new IllegalArgumentException(
                    "Reseting can be performed only in error states. Actual state: " + actualState
                );
            }
        }
        
        synchronized ( synchroNewEvent ) {
            newEvent = new ResetEvent();
            synchroNewEvent.notifyAll();
        }
        
        waitForStateChangeSignal(State.FREE_FOR_SEND);
        
        logger.info("Reseted.");
        logger.debug("resetAfterError - end");
    }
    
    @Override
    public void destroy() {
        logger.debug("destroy - start:");
        
        terminateWaitingTimeCounter();
        
        logger.info("Destroyed.");
        logger.debug("destroy - end");
    }
}
