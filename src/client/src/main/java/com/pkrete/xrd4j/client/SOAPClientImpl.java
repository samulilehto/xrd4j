/**
 * MIT License
 *
 * Copyright (C) 2014 Petteri Kivimäki
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.pkrete.xrd4j.client;

import com.pkrete.xrd4j.client.deserializer.ServiceResponseDeserializer;
import com.pkrete.xrd4j.common.message.ServiceRequest;
import com.pkrete.xrd4j.common.message.ServiceResponse;
import com.pkrete.xrd4j.client.serializer.ServiceRequestSerializer;
import com.pkrete.xrd4j.common.util.SOAPHelper;
import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a SOAP client that can be used for sending
 * SOAPMessage and ServiceRequest objects to SOAP endpoints.
 *
 * @author Petteri Kivimäki
 */
public class SOAPClientImpl implements SOAPClient {

    private static final Logger logger = LoggerFactory.getLogger(SOAPClientImpl.class);
    private SOAPConnectionFactory connectionFactory;

    /**
     * Constructs and initializes a new SOAPClientImpl.
     * @throws SOAPException if there's a SOAP error
     */
    public SOAPClientImpl() throws SOAPException {
        this.connectionFactory = SOAPConnectionFactory.newInstance();
    }

    /**
     * Sends the given message to the specified endpoint and blocks until it
     * has returned the response. Null is returned if the given url is
     * malformed or if sending the message fails.
     * @param request the SOAPMessage object to be sent
     * @param url an URL that identifies where the message should be sent
     * @return the SOAPMessage object that is the response to the request
     * message that was sent.
     * @throws SOAPException if there's a SOAP error
     * @throws MalformedURLException if no protocol is specified, or an
     * unknown protocol is found, or url is null
     */
    public SOAPMessage send(final SOAPMessage request, final String url) throws SOAPException, MalformedURLException {
        URL client = new URL(url);
        SOAPConnection connection = connectionFactory.createConnection();
        logger.debug("Send SOAP message to \"{}\".", url);
        logger.trace("Outgoing SOAP request : \"{}\".", SOAPHelper.toString(request));
        SOAPMessage response = connection.call(request, client);
        logger.debug("SOAP response received.");
        logger.trace("Incoming SOAP response : \"{}\".", SOAPHelper.toString(response));
        connection.close();
        return response;
    }

    /**
     * Sends the given message to the specified endpoint and blocks until it
     * has returned the response. Null is returned if the given url is
     * malformed or if sending the message fails. Serialization and
     * deserialization from/to SOAPMessage is done inside the method.
     * @param request the ServiceRequest object to be sent
     * @param url url an URL that identifies where the message should be sent
     * @param serializer the ServiceRequestSerializer object that serializes
     * the request to SOAPMessage
     * @param deserializer the ServiceResponseDeserializer object that
     * deserializes SOAPMessage response to ServiceResponse
     * @return the ServiceResponse object that is the response to the message
     * that was sent.
     * @throws SOAPException if there's a SOAP error
     * @throws MalformedURLException if no protocol is specified, or an
     * unknown protocol is found, or url is null
     */
    public ServiceResponse send(final ServiceRequest request, final String url, final ServiceRequestSerializer serializer, final ServiceResponseDeserializer deserializer) throws SOAPException, MalformedURLException {
        SOAPMessage soapRequest = serializer.serialize(request);
        logger.info("Send ServiceRequest to \"{}\". Request id : \"{}\"", url, request.getId());
        logger.debug("Consumer : {}", request.getConsumer().toString());
        logger.debug("Producer : {}", request.getProducer().toString());
        SOAPMessage soapResponse = this.send(soapRequest, url);
        String producerNamespaceURI = request.getProducer().getNamespaceUrl() == null || request.getProducer().getNamespaceUrl().isEmpty() ? "*" : request.getProducer().getNamespaceUrl();
        ServiceResponse response = deserializer.deserialize(soapResponse, producerNamespaceURI);
        logger.info("ServiceResponse received. Request id : \"{}\"", request.getId());
        return response;
    }
}