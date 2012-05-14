/**
 *  Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.transport.passthru;

import java.io.IOException;

import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpServerConnection;
import org.wso2.carbon.transport.passthru.config.TargetConfiguration;
import org.wso2.carbon.transport.passthru.connections.HostConnections;
import org.wso2.carbon.transport.passthru.jmx.PassThroughTransportMetricsCollector;

/**
 * This class is handling events from the transport -- > client.
 */
public class TargetHandler implements NHttpClientHandler {
    private static Log log = LogFactory.getLog(TargetHandler.class);

    /** Delivery agent */
    private final DeliveryAgent deliveryAgent;

    /** Configuration used by the sender */
    private final TargetConfiguration targetConfiguration;

    /** Error handler for injecting faults */
    private final TargetErrorHandler targetErrorHandler;

    private PassThroughTransportMetricsCollector metrics = null;

    public TargetHandler(DeliveryAgent deliveryAgent,
                         TargetConfiguration configuration) {
        this.targetConfiguration = configuration;
        this.deliveryAgent = deliveryAgent;
        this.targetErrorHandler = new TargetErrorHandler(targetConfiguration);
        this.metrics = targetConfiguration.getMetrics();
    }

    public void connected(NHttpClientConnection conn, Object o) {
        assert o instanceof HostConnections : "Attachment should be a HostConnections";

        HostConnections pool = (HostConnections) o;
        conn.getContext().setAttribute(PassThroughConstants.CONNECTION_POOL, pool);

        // create the connection information and set it to request ready
        TargetContext.create(conn, ProtocolState.REQUEST_READY, targetConfiguration);

        // notify the pool about the new connection
        targetConfiguration.getConnections().addConnection(conn);

        // notify about the new connection
        deliveryAgent.connected(pool.getHost(), pool.getPort());

        metrics.connected();
        
        //when desitination connected will collect the time
        
        conn.getContext().setAttribute(PassThroughConstants.REQ_DEPARTURE_TIME, System.currentTimeMillis());
    }

    public void requestReady(NHttpClientConnection conn) {
        ProtocolState connState = null;
        try {
            connState = TargetContext.getState(conn);

            if (connState == ProtocolState.REQUEST_DONE) {
                return;
            }

            if (connState != ProtocolState.REQUEST_READY &&
                    connState != ProtocolState.RESPONSE_DONE) {
                handleInvalidState(conn, "Request not started");
            }

            TargetRequest request = TargetContext.getRequest(conn);

            request.start(conn);

            targetConfiguration.getMetrics().incrementMessagesSent();
        } catch (IOException e) {
            logIOException(conn, e);
            TargetContext.updateState(conn, ProtocolState.CLOSED);
            targetConfiguration.getConnections().shutdownConnection(conn);

            MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
            if (requestMsgCtx != null) {
                targetErrorHandler.handleError(requestMsgCtx,
                        ErrorCodes.SND_IO_ERROR,
                        "Error in Sender",
                        null,
                        connState);
            }
        } catch (HttpException e) {
            log.error(e.getMessage(), e);
            TargetContext.updateState(conn, ProtocolState.CLOSED);
            targetConfiguration.getConnections().shutdownConnection(conn);

            MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
            if (requestMsgCtx != null) {
                targetErrorHandler.handleError(requestMsgCtx,
                        ErrorCodes.SND_HTTP_ERROR,
                        "Error in Sender",
                        null,
                        connState);
            }
        }
        conn.getContext().setAttribute(PassThroughConstants.REQ_DEPARTURE_TIME, System.currentTimeMillis());
    }

    public void outputReady(NHttpClientConnection conn, ContentEncoder encoder) {
        ProtocolState connState = null;
        try {
            connState = TargetContext.getState(conn);
            if (connState != ProtocolState.REQUEST_HEAD &&
                    connState != ProtocolState.REQUEST_DONE) {
                handleInvalidState(conn, "Writing message body");
            }

            TargetRequest request = TargetContext.getRequest(conn);
            if (request.hasEntityBody()) {
                int bytesWritten = request.write(conn, encoder);
                metrics.incrementBytesSent(bytesWritten);
            }
        } catch (IOException ex) {
            logIOException(conn, ex);
            TargetContext.updateState(conn, ProtocolState.CLOSING);
            targetConfiguration.getConnections().shutdownConnection(conn);

            informWriterError(conn);

            MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
            if (requestMsgCtx != null) {
                targetErrorHandler.handleError(requestMsgCtx,
                        ErrorCodes.SND_HTTP_ERROR,
                        "Error in Sender",
                        null,
                        connState);
            }
        } catch (Exception e) {
            log.error("Error occurred while writing data to the target", e);
            TargetContext.updateState(conn, ProtocolState.CLOSED);
            targetConfiguration.getConnections().shutdownConnection(conn);

            informWriterError(conn);

            MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
            if (requestMsgCtx != null) {
                targetErrorHandler.handleError(requestMsgCtx,
                        ErrorCodes.SND_HTTP_ERROR,
                        "Error in Sender",
                        null,
                        connState);
            }
        }
    }

    public void responseReceived(NHttpClientConnection conn) {
   
    	//Collect response  header receive time to the 
    	conn.getContext().setAttribute(PassThroughConstants.RES_HEADER_ARRIVAL_TIME, System.currentTimeMillis());
    	
        ProtocolState connState;
        try {
            connState = TargetContext.getState(conn);
            if (connState != ProtocolState.REQUEST_DONE) {
                handleInvalidState(conn, "Receiving response");
            }

            HttpResponse response = conn.getHttpResponse();
            TargetRequest targetRequest = TargetContext.getRequest(conn);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < HttpStatus.SC_OK) {
                if (log.isDebugEnabled()) {
                    log.debug("Received a 100 Continue response");
                }
                // Ignore 1xx response
                return;
            }

            boolean canResponseHaveBody =
                    isResponseHaveBodyExpected(targetRequest.getMethod(), response);
            TargetResponse targetResponse = new TargetResponse(
                    targetConfiguration, response, conn, canResponseHaveBody);
            TargetContext.setResponse(conn, targetResponse);
            targetResponse.start(conn);

            MessageContext requestMsgContext = TargetContext.get(conn).getRequestMsgCtx();
                       
            targetConfiguration.getWorkerPool().execute(
                    new ClientWorker(targetConfiguration.getConfigurationContext(),
                            requestMsgContext, targetResponse));

            targetConfiguration.getMetrics().incrementMessagesReceived();
            
            NHttpServerConnection sourceConn = (NHttpServerConnection) requestMsgContext.getProperty(
                           PassThroughConstants.PASS_THROUGH_SOURCE_CONNECTION);
            if(sourceConn != null){
            	sourceConn.getContext().setAttribute(PassThroughConstants.RES_HEADER_ARRIVAL_TIME, conn.getContext().getAttribute(PassThroughConstants.RES_HEADER_ARRIVAL_TIME));
            	sourceConn.getContext().setAttribute(PassThroughConstants.REQ_DEPARTURE_TIME,conn.getContext().getAttribute(PassThroughConstants.REQ_DEPARTURE_TIME));
 
            }
            
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);

            informReaderError(conn);

            TargetContext.updateState(conn, ProtocolState.CLOSED);
            targetConfiguration.getConnections().shutdownConnection(conn);
        }
    }

    public void inputReady(NHttpClientConnection conn, ContentDecoder decoder) {
        ProtocolState connState;
        try {
            connState = TargetContext.getState(conn);
            if (connState != ProtocolState.RESPONSE_HEAD &&
                    connState != ProtocolState.RESPONSE_BODY) {
                handleInvalidState(conn, "Response received");
            }

            TargetContext.updateState(conn, ProtocolState.RESPONSE_BODY);

            TargetResponse response = TargetContext.getResponse(conn);

            int responseRead = response.read(conn, decoder);

            metrics.incrementBytesReceived(responseRead);
        } catch (IOException e) {
            logIOException(conn, e);

            informReaderError(conn);

            TargetContext.updateState(conn, ProtocolState.CLOSED);
            targetConfiguration.getConnections().shutdownConnection(conn);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);

            informReaderError(conn);

            TargetContext.updateState(conn, ProtocolState.CLOSED);
            targetConfiguration.getConnections().shutdownConnection(conn);
        }
    }

    public void closed(NHttpClientConnection conn) {
        ProtocolState state = TargetContext.getState(conn);
        
        boolean sendFault = false;

        if (state == ProtocolState.REQUEST_READY || state == ProtocolState.RESPONSE_DONE) {
            if (log.isDebugEnabled()) {
                log.debug("Keep-Alive Connection closed");
            }
        } else if (state == ProtocolState.REQUEST_HEAD || state == ProtocolState.REQUEST_BODY) {
            informWriterError(conn);
            log.warn("Connection closed by target host while sending the request");
            sendFault = true;
        } else if (state == ProtocolState.RESPONSE_HEAD || state == ProtocolState.RESPONSE_BODY) {
            informReaderError(conn);
            log.warn("Connection closed by target host while receiving the response");
            sendFault = false;
        } else if (state == ProtocolState.REQUEST_DONE) {
            informWriterError(conn);
            log.warn("Connection closed by target host before receiving the request");
            sendFault = true;
        }

        if (sendFault) {
            MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
            if (requestMsgCtx != null) {
                targetErrorHandler.handleError(requestMsgCtx,
                        ErrorCodes.CONNECTION_CLOSED,
                        "Error in Sender",
                        null,
                        state);
            }
        }

        metrics.disconnected();

        TargetContext.updateState(conn, ProtocolState.CLOSED);
        targetConfiguration.getConnections().shutdownConnection(conn);
    }

    public void exception(NHttpClientConnection conn, IOException e) {
        ProtocolState state = TargetContext.getState(conn);

        logIOException(conn, e);

        MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
        if (requestMsgCtx != null) {
            targetErrorHandler.handleError(requestMsgCtx,
                    ErrorCodes.SND_IO_ERROR,
                    "Error in Sender",
                    null,
                    state);
        }

        TargetContext.updateState(conn, ProtocolState.CLOSING);
        targetConfiguration.getConnections().shutdownConnection(conn);
    }

    private void logIOException(NHttpClientConnection conn, IOException e) {
        String message = getErrorMessage("I/O error : " + e.getMessage(), conn);

        if (e instanceof ConnectionClosedException || (e.getMessage() != null &&
                e.getMessage().toLowerCase().contains("connection reset by peer") ||
                e.getMessage().toLowerCase().contains("forcibly closed"))) {
            if (log.isDebugEnabled()) {
                log.debug("I/O error (Probably the keep-alive connection " +
                        "was closed):" + e.getMessage());
            }
        } else if (e.getMessage() != null) {
            String msg = e.getMessage().toLowerCase();
            if (msg.indexOf("broken") != -1) {
                log.warn("I/O error (Probably the connection " +
                        "was closed by the remote party):" + e.getMessage());
            } else {
                log.error("I/O error: " + e.getMessage(), e);
            }
        } else {
            log.error(message, e);
        }
    }

    public void exception(NHttpClientConnection conn, HttpException e) {
        ProtocolState state = TargetContext.getState(conn);

        String message = getErrorMessage("HTTP protocol violation : " + e.getMessage(), conn);
        log.error(message, e);

        MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
        if (requestMsgCtx != null) {
            targetErrorHandler.handleError(requestMsgCtx,
                    ErrorCodes.PROTOCOL_VIOLATION,
                    "Error in Sender",
                    null,
                    state);
        }

        TargetContext.updateState(conn, ProtocolState.CLOSED);
        targetConfiguration.getConnections().shutdownConnection(conn);
    }

    public void timeout(NHttpClientConnection conn) {
        ProtocolState state = TargetContext.getState(conn);

        String message = getErrorMessage("Connection timeout", conn);
        if (log.isDebugEnabled()) {
            log.debug(message);
        }

        if (state != null &&
                (state == ProtocolState.REQUEST_READY || state == ProtocolState.RESPONSE_DONE)) {
            if (log.isDebugEnabled()) {
                log.debug(getErrorMessage("Keep-alive connection timed out", conn));
            }
        } else if (state != null ) {
            if (state == ProtocolState.REQUEST_BODY) {
                metrics.incrementTimeoutsSending();
                informWriterError(conn);
            }

            if (state == ProtocolState.RESPONSE_BODY || state == ProtocolState.REQUEST_HEAD) {
                metrics.incrementTimeoutsReceiving();
                informReaderError(conn);
            }

            if (state.compareTo(ProtocolState.REQUEST_DONE) <= 0) {
                MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();

                log.warn("Connection time out while in state: " + state);
                if (requestMsgCtx != null) {
                    targetErrorHandler.handleError(requestMsgCtx,
                            ErrorCodes.CONNECTION_TIMEOUT,
                            "Error in Sender",
                            null,
                            state);
                }
            }
        }

        TargetContext.updateState(conn, ProtocolState.CLOSED);
        targetConfiguration.getConnections().shutdownConnection(conn);
    }

    private boolean isResponseHaveBodyExpected(
            final String method, final HttpResponse response) {

        if ("HEAD".equalsIgnoreCase(method)) {
            return false;
        }

        int status = response.getStatusLine().getStatusCode();
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    /**
     * Include remote host and port information to an error message
     *
     * @param message the initial message
     * @param conn    the connection encountering the error
     * @return the updated error message
     */
    private String getErrorMessage(String message, NHttpClientConnection conn) {
        if (conn != null && conn instanceof DefaultNHttpClientConnection) {
            DefaultNHttpClientConnection c = ((DefaultNHttpClientConnection) conn);

            if (c.getRemoteAddress() != null) {
                return message + " For : " + c.getRemoteAddress().getHostAddress() + ":" +
                        c.getRemotePort();
            }
        }
        return message;
    }

    private void handleInvalidState(NHttpClientConnection conn, String action) {
        ProtocolState state = TargetContext.getState(conn);

        log.warn(action + " while the handler is in an inconsistent state " +
                TargetContext.getState(conn));
        TargetContext.updateState(conn, ProtocolState.CLOSED);
        targetConfiguration.getConnections().shutdownConnection(conn);

        MessageContext requestMsgCtx = TargetContext.get(conn).getRequestMsgCtx();
        if (requestMsgCtx != null) {
            targetErrorHandler.handleError(requestMsgCtx,
                    ErrorCodes.SND_INVALID_STATE,
                    "Error in Sender",
                    null,
                    state);
        }
    }

    private void informReaderError(NHttpClientConnection conn) {
        Pipe reader = TargetContext.get(conn).getReader();

        metrics.incrementFaultsReceiving();

        if (reader != null) {
            reader.producerError();
        }
    }

    private void informWriterError(NHttpClientConnection conn) {
        Pipe writer = TargetContext.get(conn).getWriter();

        metrics.incrementFaultsReceiving();

        if (writer != null) {
            writer.consumerError();
        }
    }
}
