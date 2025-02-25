/*
 * JetS3t : Java S3 Toolkit
 * Project hosted at http://bitbucket.org/jmurty/jets3t/
 *
 * Copyright 2006-2011 James Murty
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jets3t.service.impl.rest.httpclient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.MultipartUploadChunk;
import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.VersionOrDeleteMarkersChunk;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser.CompleteMultipartUploadResultHandler;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser.ListMultipartPartsResultHandler;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser.ListMultipartUploadsResultHandler;
import org.jets3t.service.impl.rest.XmlResponsesSaxParser.ListVersionsResultsHandler;
import org.jets3t.service.model.*;
import org.jets3t.service.model.container.ObjectKeyAndVersion;
import org.jets3t.service.security.AWSDevPayCredentials;
import org.jets3t.service.security.AWSSessionCredentials;
import org.jets3t.service.security.ProviderCredentials;
import org.jets3t.service.utils.RestUtils;
import org.jets3t.service.utils.ServiceUtils;

import com.jamesmurty.utils.XMLBuilder;

/**
 * REST/HTTP implementation of an S3Service based on the
 * <a href="http://jakarta.apache.org/commons/httpclient/">HttpClient</a> library.
 * <p>
 * This class uses properties obtained through {@link Jets3tProperties}. For more information on
 * these properties please refer to
 * <a href="http://www.jets3t.org/toolkit/configuration.html">JetS3t Configuration</a>
 * </p>
 *
 * @author James Murty
 */
public class RestS3Service extends S3Service {

    private static final Log log = LogFactory.getLog(RestS3Service.class);
    private static final String AWS_SIGNATURE_IDENTIFIER = "AWS";
    private static final String AWS_REST_HEADER_PREFIX = "x-amz-";
    private static final String AWS_REST_METADATA_PREFIX = "x-amz-meta-";

    private String awsDevPayUserToken = null;
    private String awsDevPayProductToken = null;

    private boolean isRequesterPaysEnabled = false;

    /**
     * Constructs the service and initialises the properties.
     *
     * @param credentials
     * the user credentials to use when communicating with S3, may be null in which case the
     * communication is done as an anonymous user.
     */
    public RestS3Service(ProviderCredentials credentials) {
        this(credentials, null, null);
    }

    /**
     * Constructs the service and initialises the properties.
     *
     * @param credentials
     * the S3 user credentials to use when communicating with S3, may be null in which case the
     * communication is done as an anonymous user.
     * @param invokingApplicationDescription
     * a short description of the application using the service, suitable for inclusion in a
     * user agent string for REST/HTTP requests. Ideally this would include the application's
     * version number, for example: <code>Cockpit/0.7.3</code> or <code>My App Name/1.0</code>
     * @param credentialsProvider
     * an implementation of the HttpClient CredentialsProvider interface, to provide a means for
     * prompting for credentials when necessary.
     */
    public RestS3Service(ProviderCredentials credentials, String invokingApplicationDescription,
        CredentialsProvider credentialsProvider)
    {
        this(credentials, invokingApplicationDescription, credentialsProvider,
            Jets3tProperties.getInstance(Constants.JETS3T_PROPERTIES_FILENAME));
    }

    /**
     * Constructs the service and initialises the properties.
     *
     * @param credentials
     * the S3 user credentials to use when communicating with S3, may be null in which case the
     * communication is done as an anonymous user.
     * @param invokingApplicationDescription
     * a short description of the application using the service, suitable for inclusion in a
     * user agent string for REST/HTTP requests. Ideally this would include the application's
     * version number, for example: <code>Cockpit/0.7.3</code> or <code>My App Name/1.0</code>
     * @param credentialsProvider
     * an implementation of the HttpClient CredentialsProvider interface, to provide a means for
     * prompting for credentials when necessary.
     * @param jets3tProperties
     * JetS3t properties that will be applied within this service.
     */
    public RestS3Service(ProviderCredentials credentials, String invokingApplicationDescription,
        CredentialsProvider credentialsProvider, Jets3tProperties jets3tProperties)
    {
        super(credentials, invokingApplicationDescription, credentialsProvider, jets3tProperties);

        if (credentials instanceof AWSDevPayCredentials) {
            AWSDevPayCredentials awsDevPayCredentials = (AWSDevPayCredentials) credentials;
            this.awsDevPayUserToken = awsDevPayCredentials.getUserToken();
            this.awsDevPayProductToken = awsDevPayCredentials.getProductToken();
        } else {
            this.awsDevPayUserToken = jets3tProperties.getStringProperty("devpay.user-token", null);
            this.awsDevPayProductToken = jets3tProperties.getStringProperty("devpay.product-token", null);
        }

        this.setRequesterPaysEnabled(
            getJetS3tProperties().getBoolProperty("httpclient.requester-pays-buckets-enabled", false));
    }

    @Override
    protected boolean isTargettingGoogleStorageService() {
        return Constants.GS_DEFAULT_HOSTNAME.equals(
            this.getJetS3tProperties().getStringProperty("s3service.s3-endpoint", null));
    }

    @Override
    protected XmlResponsesSaxParser getXmlResponseSaxParser() throws ServiceException {
        return new XmlResponsesSaxParser(getJetS3tProperties(), false);
    }

    @Override
    protected StorageBucket newBucket() {
        return new S3Bucket();
    }

    @Override
    protected StorageObject newObject() {
        return new S3Object();
    }

    /**
     * Set the User Token value to use for requests to a DevPay S3 account.
     * The user token is not required for DevPay web products for which the
     * user token was created after 15th May 2008.
     *
     * @param userToken
     * the user token value provided by the AWS DevPay activation service.
     */
    public void setDevPayUserToken(String userToken) {
        this.awsDevPayUserToken = userToken;
    }

    /**
     * @return
     * the user token value to use in requests to a DevPay S3 account, or null
     * if no such token value has been set.
     */
    public String getDevPayUserToken() {
        return this.awsDevPayUserToken;
    }

    /**
     * Set the Product Token value to use for requests to a DevPay S3 account.
     *
     * @param productToken
     * the token that identifies your DevPay product.
     */
    public void setDevPayProductToken(String productToken) {
        this.awsDevPayProductToken = productToken;
    }

    /**
     * @return
     * the product token value to use in requests to a DevPay S3 account, or
     * null if no such token value has been set.
     */
    public String getDevPayProductToken() {
        return this.awsDevPayProductToken;
    }

    /**
     * Instruct the service whether to generate Requester Pays requests when
     * uploading data to S3, or retrieving data from the service. The default
     * value for the Requester Pays Enabled setting is set according to the
     * jets3t.properties setting
     * <code>httpclient.requester-pays-buckets-enabled</code>.
     *
     * @param isRequesterPays
     * if true, all subsequent S3 service requests will include the Requester
     * Pays flag.
     */
    public void setRequesterPaysEnabled(boolean isRequesterPays) {
        this.isRequesterPaysEnabled = isRequesterPays;
    }

    /**
     * Is this service configured to generate Requester Pays requests when
     * uploading data to S3, or retrieving data from the service. The default
     * value for the Requester Pays Enabled setting is set according to the
     * jets3t.properties setting
     * <code>httpclient.requester-pays-buckets-enabled</code>.
     *
     * @return
     * true if S3 service requests will include the Requester Pays flag, false
     * otherwise.
     */
    public boolean isRequesterPaysEnabled() {
        return this.isRequesterPaysEnabled;
    }

    /**
     * Creates an {@link HttpUriRequest} object to handle a particular connection method.
     *
     * @param method
     * the HTTP method/connection-type to use, must be one of: PUT, HEAD, GET, DELETE
     * @param bucketName
     * the bucket's name
     * @param objectKey
     * the object's key name, may be null if the operation is on a bucket only.
     * @return
     * the HTTP method object used to perform the request
     *
     * @throws org.jets3t.service.S3ServiceException
     */
    @Override
    protected HttpUriRequest setupConnection(HTTP_METHOD method, String bucketName, String objectKey,
        Map<String, String> requestParameters) throws S3ServiceException
    {
        HttpUriRequest httpMethod;
        try {
            httpMethod = super.setupConnection(method, bucketName, objectKey, requestParameters);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }

        // Set DevPay request headers.
        if (getDevPayUserToken() != null || getDevPayProductToken() != null) {
            // DevPay tokens have been provided, include these with the request.
            if (getDevPayProductToken() != null) {
                String securityToken = getDevPayUserToken() + "," + getDevPayProductToken();
                httpMethod.setHeader(Constants.AMZ_SECURITY_TOKEN, securityToken);
                if (log.isDebugEnabled()) {
                    log.debug("Including DevPay user and product tokens in request: "
                        + Constants.AMZ_SECURITY_TOKEN + "=" + securityToken);
                }
            } else {
                httpMethod.setHeader(Constants.AMZ_SECURITY_TOKEN, getDevPayUserToken());
                if (log.isDebugEnabled()) {
                    log.debug("Including DevPay user token in request: "
                        + Constants.AMZ_SECURITY_TOKEN + "=" + getDevPayUserToken());
                }
            }
        }

        // Set the session token from Temporary Security (Session) Credentials
        // NOTE: a session token will override any DevPay credential values set above.
        if (getProviderCredentials() instanceof AWSSessionCredentials) {
            String sessionToken = ((AWSSessionCredentials)getProviderCredentials()).getSessionToken();
            httpMethod.setHeader(Constants.AMZ_SECURITY_TOKEN, sessionToken);
            if (log.isDebugEnabled()) {
                log.debug("Including AWS session token in request: "
                    + Constants.AMZ_SECURITY_TOKEN + "=" + sessionToken);
            }
        }

        // Set Requester Pays header to allow access to these buckets.
        if (this.isRequesterPaysEnabled()) {
            String[] requesterPaysHeaderAndValue = Constants.REQUESTER_PAYS_BUCKET_FLAG.split("=");
            httpMethod.setHeader(requesterPaysHeaderAndValue[0], requesterPaysHeaderAndValue[1]);
            if (log.isDebugEnabled()) {
                log.debug("Including Requester Pays header in request: " +
                    Constants.REQUESTER_PAYS_BUCKET_FLAG);
            }
        }

        return httpMethod;
    }

    /**
     * @return
     * the endpoint to be used to connect to S3.
     */
    @Override
    public String getEndpoint() {
    	return getJetS3tProperties().getStringProperty(
                "s3service.s3-endpoint", Constants.S3_DEFAULT_HOSTNAME);
    }

    /**
     * @return
     * the virtual path inside the S3 server.
     */
    @Override
    protected String getVirtualPath() {
    	return getJetS3tProperties().getStringProperty(
                "s3service.s3-endpoint-virtual-path", "");
    }

    /**
     * @return
     * the identifier for the signature algorithm.
     */
    @Override
    protected String getSignatureIdentifier() {
    	return AWS_SIGNATURE_IDENTIFIER;
    }

    /**
     * @return
     * header prefix for general Amazon headers: x-amz-.
     */
    @Override
    public String getRestHeaderPrefix() {
    	return AWS_REST_HEADER_PREFIX;
    }

    @Override
    public List<String> getResourceParameterNames() {
        // Special HTTP parameter names that refer to resources in S3
        return Arrays.asList("acl", "policy",
                "torrent",
                "logging",
                "location",
                "requestPayment",
                "versions", "versioning", "versionId",
                "uploads", "uploadId", "partNumber",
                "website", "notification", "lifecycle",
                "delete", // multiple object delete
                // Response-altering special parameters
                "response-content-type", "response-content-language",
                "response-expires", "reponse-cache-control",
                "response-content-disposition", "response-content-encoding"
                );
    }

    /**
     * @return
     * header prefix for Amazon metadata headers: x-amz-meta-.
     */
    @Override
    public String getRestMetadataPrefix() {
    	return AWS_REST_METADATA_PREFIX;
    }

    /**
     * @return
     * the port number to be used for insecure connections over HTTP.
     */
    @Override
    protected int getHttpPort() {
      return getJetS3tProperties().getIntProperty("s3service.s3-endpoint-http-port", 80);
    }

    /**
     * @return
     * the port number to be used for secure connections over HTTPS.
     */
    @Override
    protected int getHttpsPort() {
      return getJetS3tProperties().getIntProperty("s3service.s3-endpoint-https-port", 443);
    }

    /**
     * @return
     * If true, all communication with S3 will be via encrypted HTTPS connections,
     * otherwise communications will be sent unencrypted via HTTP.
     */
    @Override
    protected boolean getHttpsOnly() {
      return getJetS3tProperties().getBoolProperty("s3service.https-only", true);
    }

    /**
     * @return
     * If true, JetS3t will specify bucket names in the request path of the HTTP message
     * instead of the Host header.
     */
    @Override
    protected boolean getDisableDnsBuckets() {
      return getJetS3tProperties().getBoolProperty("s3service.disable-dns-buckets", false);
    }

    /**
     * @return
     * If true, JetS3t will enable support for Storage Classes.
     */
    @Override
    protected boolean getEnableStorageClasses() {
        return getJetS3tProperties().getBoolProperty("s3service.enable-storage-classes",
                // Enable non-standard storage classes by default for AWS, not for Google endpoints.
                !isTargettingGoogleStorageService());
    }

    /**
     * @return
     * If true, JetS3t will enable support for Server-Side Encryption. Only enabled for
     * Amazon S3 end-point by default.
     */
    @Override
    protected boolean getEnableServerSideEncryption() {
        return ! isTargettingGoogleStorageService();
    }

    @Override
    protected BaseVersionOrDeleteMarker[] listVersionedObjectsImpl(String bucketName,
        String prefix, String delimiter, String keyMarker, String versionMarker,
        long maxListingLength) throws S3ServiceException
    {
        return listVersionedObjectsInternal(bucketName, prefix, delimiter,
            maxListingLength, true, keyMarker, versionMarker).getItems();
    }

    @Override
    protected void updateBucketVersioningStatusImpl(String bucketName,
        boolean enabled, boolean multiFactorAuthDeleteEnabled,
        String multiFactorSerialNumber, String multiFactorAuthCode)
        throws S3ServiceException
    {
        if (log.isDebugEnabled()) {
            log.debug( (enabled ? "Enabling" : "Suspending")
                + " versioning for bucket " + bucketName
                + (multiFactorAuthDeleteEnabled ? " with Multi-Factor Auth enabled" : ""));
        }
        try {
            XMLBuilder builder = XMLBuilder
                .create("VersioningConfiguration").a("xmlns", Constants.XML_NAMESPACE)
                    .e("Status").t( (enabled ? "Enabled" : "Suspended") ).up()
                    .e("MfaDelete").t( (multiFactorAuthDeleteEnabled ? "Enabled" : "Disabled"));
            Map<String, String> requestParams = new HashMap<String, String>();
            requestParams.put("versioning", null);
            Map<String, Object> metadata = new HashMap<String, Object>();
            if (multiFactorSerialNumber != null || multiFactorAuthCode != null) {
                metadata.put(Constants.AMZ_MULTI_FACTOR_AUTH_CODE,
                    multiFactorSerialNumber + " " + multiFactorAuthCode);
            }
            try {
                performRestPutWithXmlBuilder(bucketName, null, metadata, requestParams, builder);
            } catch (ServiceException se) {
                throw new S3ServiceException(se);
            }
        } catch (ParserConfigurationException e) {
            throw new S3ServiceException("Failed to build XML document for request", e);
        }
    }

    @Override
    protected S3BucketVersioningStatus getBucketVersioningStatusImpl(String bucketName)
        throws S3ServiceException
    {
        try {
            if (log.isDebugEnabled()) {
                log.debug( "Checking status of versioning for bucket " + bucketName);
            }
            Map<String, String> requestParams = new HashMap<String, String>();
            requestParams.put("versioning", null);
            HttpResponse response = performRestGet(bucketName, null, requestParams, null);
            return getXmlResponseSaxParser()
                .parseVersioningConfigurationResponse(new HttpMethodReleaseInputStream(response));
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    protected VersionOrDeleteMarkersChunk listVersionedObjectsInternal(
        String bucketName, String prefix, String delimiter, long maxListingLength,
        boolean automaticallyMergeChunks, String nextKeyMarker, String nextVersionIdMarker)
        throws S3ServiceException
    {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("versions", null);
        if (prefix != null) {
            parameters.put("prefix", prefix);
        }
        if (delimiter != null) {
            parameters.put("delimiter", delimiter);
        }
        if (maxListingLength > 0) {
            parameters.put("max-keys", String.valueOf(maxListingLength));
        }

        List<BaseVersionOrDeleteMarker> items = new ArrayList<BaseVersionOrDeleteMarker>();
        List<String> commonPrefixes = new ArrayList<String>();

        boolean incompleteListing = true;
        int ioErrorRetryCount = 0;

        while (incompleteListing) {
            if (nextKeyMarker != null) {
                parameters.put("key-marker", nextKeyMarker);
            } else {
                parameters.remove("key-marker");
            }
            if (nextVersionIdMarker != null) {
                parameters.put("version-id-marker", nextVersionIdMarker);
            } else {
                parameters.remove("version-id-marker");
            }

            HttpResponse httpResponse;
            try {
                httpResponse = performRestGet(bucketName, null, parameters, null);
            } catch (ServiceException se) {
                throw new S3ServiceException(se);
            }
            ListVersionsResultsHandler handler;

            try {
                handler = getXmlResponseSaxParser()
                    .parseListVersionsResponse(
                        new HttpMethodReleaseInputStream(httpResponse));
                ioErrorRetryCount = 0;
            } catch (ServiceException se) {
                if (se.getCause() instanceof IOException && ioErrorRetryCount < 5) {
                    ioErrorRetryCount++;
                    if (log.isWarnEnabled()) {
                        log.warn("Retrying bucket listing failure due to IO error", se);
                    }
                    continue;
                } else {
                    throw new S3ServiceException(se);
                }
            }

            BaseVersionOrDeleteMarker[] partialItems = handler.getItems();
            if (log.isDebugEnabled()) {
                log.debug("Found " + partialItems.length + " items in one batch");
            }
            items.addAll(Arrays.asList(partialItems));

            String[] partialCommonPrefixes = handler.getCommonPrefixes();
            if (log.isDebugEnabled()) {
                log.debug("Found " + partialCommonPrefixes.length + " common prefixes in one batch");
            }
            commonPrefixes.addAll(Arrays.asList(partialCommonPrefixes));

            incompleteListing = handler.isListingTruncated();
            nextKeyMarker = handler.getNextKeyMarker();
            nextVersionIdMarker = handler.getNextVersionIdMarker();
            if (incompleteListing) {
                if (log.isDebugEnabled()) {
                    log.debug("Yet to receive complete listing of bucket versions, "
                        + "continuing with key-marker=" + nextKeyMarker
                        + " and version-id-marker=" + nextVersionIdMarker);
                }
            }

            if (!automaticallyMergeChunks) {
                break;
            }
        }
        if (automaticallyMergeChunks) {
            if (log.isDebugEnabled()) {
                log.debug("Found " + items.size() + " items in total");
            }
            return new VersionOrDeleteMarkersChunk(
                prefix, delimiter,
                items.toArray(new BaseVersionOrDeleteMarker[items.size()]),
                commonPrefixes.toArray(new String[commonPrefixes.size()]),
                null, null);
        } else {
            return new VersionOrDeleteMarkersChunk(
                prefix, delimiter,
                items.toArray(new BaseVersionOrDeleteMarker[items.size()]),
                commonPrefixes.toArray(new String[commonPrefixes.size()]),
                nextKeyMarker, nextVersionIdMarker);
        }
    }

    @Override
    protected VersionOrDeleteMarkersChunk listVersionedObjectsChunkedImpl(String bucketName,
        String prefix, String delimiter, long maxListingLength, String priorLastKey,
        String priorLastVersion, boolean completeListing) throws S3ServiceException
    {
        return listVersionedObjectsInternal(bucketName, prefix, delimiter,
            maxListingLength, completeListing, priorLastKey, priorLastVersion);
    }

    @Override
    protected String getBucketLocationImpl(String bucketName)
        throws S3ServiceException
    {
        try {
            return super.getBucketLocationImpl(bucketName);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected String getBucketPolicyImpl(String bucketName)
        throws S3ServiceException
    {
        try {
            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("policy", "");

            HttpResponse httpResponse = performRestGet(bucketName, null, requestParameters, null);
            return EntityUtils.toString(httpResponse.getEntity());
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        } catch (IOException  e) {
            throw new S3ServiceException(e);
        }
    }

    @Override
    protected void setBucketPolicyImpl(String bucketName, String policyDocument)
        throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("policy", "");

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("Content-Type", "text/plain");

        try {
            performRestPut(bucketName, null, metadata, requestParameters,
                new StringEntity(policyDocument, ContentType.create("text/plain", Constants.DEFAULT_ENCODING)),
                true);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected void deleteBucketPolicyImpl(String bucketName)
        throws S3ServiceException
    {
        try {
            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("policy", "");
            performRestDelete(bucketName, null, requestParameters, null, null);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected boolean isRequesterPaysBucketImpl(String bucketName)
        throws S3ServiceException
    {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving Request Payment Configuration settings for Bucket: " + bucketName);
        }

        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("requestPayment", "");

        try {
            HttpResponse httpResponse = performRestGet(bucketName, null, requestParameters, null);
            return getXmlResponseSaxParser()
                .parseRequestPaymentConfigurationResponse(
                    new HttpMethodReleaseInputStream(httpResponse));
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected void setRequesterPaysBucketImpl(String bucketName, boolean requesterPays) throws S3ServiceException {
        if (log.isDebugEnabled()) {
            log.debug("Setting Request Payment Configuration settings for bucket: " + bucketName);
        }

        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("requestPayment", "");

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("Content-Type", "text/plain");

        try {
            String xml =
                "<RequestPaymentConfiguration xmlns=\"" + Constants.XML_NAMESPACE + "\">" +
                    "<Payer>" +
                        (requesterPays ? "Requester" : "BucketOwner") +
                    "</Payer>" +
                "</RequestPaymentConfiguration>";

            performRestPut(bucketName, null, metadata, requestParameters,
                new StringEntity(xml, ContentType.create("text/plain", Constants.DEFAULT_ENCODING)),
                true);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    protected MultipartUpload multipartStartUploadImpl(String bucketName, String objectKey,
        Map<String, Object> metadataProvided, AccessControlList acl, String storageClass)
        throws S3ServiceException
    {
        return this.multipartStartUploadImpl(
            bucketName, objectKey, metadataProvided, acl, storageClass, null, null);
    }

    @Override
    protected MultipartUpload multipartStartUploadImpl(String bucketName, String objectKey,
        Map<String, Object> metadataProvided, AccessControlList acl, String storageClass,
        String serverSideEncryptionAlgorithm, String serverSideEncryptionKmsKeyId)
        throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("uploads", "");

        Map<String, Object> metadata = new HashMap<String, Object>();

        // Use metadata provided, but ignore some items that don't make sense
        if (metadataProvided != null) {
            for (Map.Entry<String, Object> entry: metadataProvided.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(BaseStorageItem.METADATA_HEADER_CONTENT_LENGTH)) {
                    metadata.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Apply per-object or default object properties when uploading object
        prepareStorageClass(metadata, storageClass, true, objectKey);
        prepareServerSideEncryption(metadata, serverSideEncryptionAlgorithm, serverSideEncryptionKmsKeyId, objectKey);
        prepareRESTHeaderAcl(metadata, acl);

        try {
            HttpResponse httpResponse = performRestPost(
                bucketName, objectKey, metadata, requestParameters, null, false);
            MultipartUpload multipartUpload = getXmlResponseSaxParser()
                .parseInitiateMultipartUploadResult(
                    new HttpMethodReleaseInputStream(httpResponse));
            multipartUpload.setMetadata(metadata); // Add object's known metadata to result object.
            return multipartUpload;
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected MultipartPart multipartUploadPartImpl(String uploadId, String bucketName,
        Integer partNumber, S3Object object) throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("uploadId", uploadId);
        requestParameters.put("partNumber", String.valueOf(partNumber));

        // Remove all non-HTTP headers from object metadata for multipart part uploads
        synchronized(object) { // Thread-safe header handling.
            List<String> metadataNamesToRemove = new ArrayList<String>();
            for (String name: object.getMetadataMap().keySet()) {
                if (!RestUtils.HTTP_HEADER_METADATA_NAMES.contains(name.toLowerCase())
                    // Special-case handling of "x-amz-content-sha256" header
                    // which should be passed through to permit AWSv4 signing
                    && !"x-amz-content-sha256".equals(name.toLowerCase()) )
                {
                    // Actual metadata name in object does not include the prefix
                    metadataNamesToRemove.add(name);
                }
            }
            for (String name: metadataNamesToRemove) {
                object.removeMetadata(name);
            }
        }

        try {
            // Always disable live MD5 hash check for MultiPart Part uploads, since the ETag
            // hash value returned by S3 is not an MD5 hash of the uploaded data anyway (Issue #141).
            boolean isLiveMD5HashingRequired = false;

            HttpEntity requestEntity = null;
            if (object.getDataInputStream() != null) {
                if (object.containsMetadata(StorageObject.METADATA_HEADER_CONTENT_LENGTH)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Uploading multipart part data with Content-Length: "
                            + object.getContentLength());
                    }
                    requestEntity = new RepeatableRequestEntity(object.getKey(),
                        object.getDataInputStream(), object.getContentType(), object.getContentLength(),
                        getJetS3tProperties(), isLiveMD5HashingRequired);
                } else {
                    // Use InputStreamRequestEntity for objects with an unknown content length, as the
                    // entity will cache the results and doesn't need to know the data length in advance.
                    if (log.isWarnEnabled()) {
                        log.warn("Content-Length of multipart part stream not set, "
                            + "will automatically determine data length in memory");
                    }
                    requestEntity = new InputStreamEntity(
                        object.getDataInputStream(), -1);
                }
            }

            // Override any storage class with an empty value, which means don't apply one (Issue #121)
            object.setStorageClass("");
            this.putObjectWithRequestEntityImpl(bucketName, object, requestEntity, requestParameters);

            // Populate part with response data that is accessible via the object's metadata
            return new MultipartPart(partNumber, object.getLastModifiedDate(),
                object.getETag(), object.getContentLength());
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected MultipartPart multipartUploadPartCopyImpl(String uploadId,
        String targetBucketName, String targetObjectKey, Integer partNumber,
        String sourceBucketName, String sourceObjectKey,
        Calendar ifModifiedSince, Calendar ifUnmodifiedSince,
        String[] ifMatchTags, String[] ifNoneMatchTags,
        Long byteRangeStart, Long byteRangeEnd,
        String versionId) throws S3ServiceException
    {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Multipart Copy Object from " + sourceBucketName + ":" + sourceObjectKey
                    + " to upload id=" + uploadId + "as part" + partNumber);
            }

            Map<String, Object> metadata = new HashMap<String, Object>();

            String sourceKey = RestUtils.encodeUrlPath(sourceBucketName + "/" + sourceObjectKey, "/");

            if (versionId != null) {
                sourceKey += "?versionId=" + versionId;
            }

            metadata.put(getRestHeaderPrefix() + "copy-source", sourceKey);

            if (ifModifiedSince != null) {
                metadata.put(getRestHeaderPrefix() + "copy-source-if-modified-since",
                    ServiceUtils.formatRfc822Date(ifModifiedSince.getTime()));
                if (log.isDebugEnabled()) {
                    log.debug("Only copy object if-modified-since:" + ifModifiedSince);
                }
            }
            if (ifUnmodifiedSince != null) {
                metadata.put(getRestHeaderPrefix() + "copy-source-if-unmodified-since",
                    ServiceUtils.formatRfc822Date(ifUnmodifiedSince.getTime()));
                if (log.isDebugEnabled()) {
                    log.debug("Only copy object if-unmodified-since:" + ifUnmodifiedSince);
                }
            }
            if (ifMatchTags != null) {
                String tags = ServiceUtils.join(ifMatchTags, ",");
                metadata.put(getRestHeaderPrefix() + "copy-source-if-match", tags);
                if (log.isDebugEnabled()) {
                    log.debug("Only copy object based on hash comparison if-match:" + tags);
                }
            }
            if (ifNoneMatchTags != null) {
                String tags = ServiceUtils.join(ifNoneMatchTags, ",");
                metadata.put(getRestHeaderPrefix() + "copy-source-if-none-match", tags);
                if (log.isDebugEnabled()) {
                    log.debug("Only copy object based on hash comparison if-none-match:" + tags);
                }
            }

            if ((byteRangeStart != null) || (byteRangeEnd != null)) {
                if ((byteRangeStart == null) || (byteRangeEnd == null)) {
                    throw new IllegalArgumentException("both range start and end must be set");
                }
                String range = String.format("bytes=%s-%s", byteRangeStart, byteRangeEnd);
                metadata.put(getRestHeaderPrefix() + "copy-source-range", range);
                if (log.isDebugEnabled()) {
                    log.debug("Copy object range:" + range);
                }
            }

            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("partNumber", String.valueOf(partNumber));
            requestParameters.put("uploadId", String.valueOf(uploadId));

            HttpResponseAndByteCount responseAndByteCount = this.performRestPut(
                targetBucketName, targetObjectKey, metadata, requestParameters, null, false);

            MultipartPart part = getXmlResponseSaxParser()
                .parseMultipartUploadPartCopyResult(
                    new HttpMethodReleaseInputStream(responseAndByteCount.getHttpResponse()));

            // CopyPartResult XML response does not include part number or size info.
            // We can compensate for the lack of part number, but cannot for size...
            return new MultipartPart(partNumber, part.getLastModified(), part.getEtag(), -1l);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected void multipartAbortUploadImpl(String uploadId, String bucketName,
        String objectKey) throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("uploadId", uploadId);

        try {
            performRestDelete(bucketName, objectKey, requestParameters, null, null);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected MultipartCompleted multipartCompleteUploadImpl(String uploadId, String bucketName,
        String objectKey, List<MultipartPart> parts) throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("uploadId", uploadId);

        // Ensure part list is sorted by part number
        MultipartPart[] sortedParts = parts.toArray(new MultipartPart[parts.size()]);
        Arrays.sort(sortedParts, new MultipartPart.PartNumberComparator());
        try {
            XMLBuilder builder = XMLBuilder
                .create("CompleteMultipartUpload").a("xmlns", Constants.XML_NAMESPACE);
            for (MultipartPart part: sortedParts) {
                builder.e("Part")
                    .e("PartNumber").t(String.valueOf(part.getPartNumber())).up()
                    .e("ETag").t(part.getEtag());
            }

            HttpResponse httpResponse = performRestPostWithXmlBuilder(
                bucketName, objectKey, null, requestParameters, builder);
            CompleteMultipartUploadResultHandler handler = getXmlResponseSaxParser()
                .parseCompleteMultipartUploadResult(
                    new HttpMethodReleaseInputStream(httpResponse));

            // Check whether completion actually succeeded
            if (handler.getServiceException() != null) {
                ServiceException e = handler.getServiceException();
                e.setResponseHeaders(RestUtils.convertHeadersToMap(
                        httpResponse.getAllHeaders()));
                throw e;
            }
            return handler.getMultipartCompleted();
        } catch (S3ServiceException se) {
            throw se;
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        } catch (ParserConfigurationException e) {
            throw new S3ServiceException(e);
        } catch (FactoryConfigurationError e) {
            throw new S3ServiceException(e);
        }
    }

    @Override
    protected MultipartUploadChunk multipartListUploadsChunkedImpl(
            String bucketName,
            String prefix,
            String delimiter,
            String keyMarker,
            String uploadIdMarker,
            Integer maxUploads,
            boolean autoMergeChunks) throws S3ServiceException
    {
        if (bucketName == null || bucketName.length()==0){
            throw new IllegalArgumentException(
                "The bucket name parameter must be specified when listing multipart uploads");
        }
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("uploads", "");
        if (prefix != null) {
            requestParameters.put("prefix", prefix);
        }
        if (delimiter != null) {
            requestParameters.put("delimiter", delimiter);
        }
        if (maxUploads != null) {
            requestParameters.put("max-uploads", maxUploads.toString());
        }
        if (keyMarker != null) {
            requestParameters.put("key-marker", keyMarker);
        }
        if (uploadIdMarker != null) {
            requestParameters.put("upload-id-marker", uploadIdMarker);
        }

        String nextKeyMarker = keyMarker;
        String nextUploadIdMarker = uploadIdMarker;

        List<MultipartUpload> uploads = new ArrayList<MultipartUpload>();
        List<String> commonPrefixes = new ArrayList<String>();

        boolean incompleteListing = true;
        int ioErrorRetryCount = 0;
        int ioErrorRetryMaxCount = getJetS3tProperties().getIntProperty(
            "httpclient.retry-max", 5);

        try {
            while (incompleteListing) {
                if (nextKeyMarker != null) {
                    requestParameters.put("key-marker", nextKeyMarker);
                } else {
                    requestParameters.remove("key-marker");
                }
                if (nextUploadIdMarker != null) {
                    requestParameters.put("upload-id-marker", nextUploadIdMarker);
                } else {
                    requestParameters.remove("upload-id-marker");
                }

                HttpResponse httpResponse = performRestGet(bucketName, null, requestParameters, null);
                ListMultipartUploadsResultHandler handler;
                try {
                    handler = getXmlResponseSaxParser().parseListMultipartUploadsResult(
                        new HttpMethodReleaseInputStream(httpResponse));
                    ioErrorRetryCount = 0;
                } catch (ServiceException e) {
                    if (e.getCause() instanceof IOException
                        && ioErrorRetryCount < ioErrorRetryMaxCount)
                    {
                        ioErrorRetryCount++;
                        if (log.isWarnEnabled()) {
                            log.warn("Retrying bucket listing failure due to IO error", e);
                        }
                        continue;
                    } else {
                        throw e;
                    }
                }

                List<MultipartUpload> partial = handler.getMultipartUploadList();
                if (log.isDebugEnabled()) {
                    log.debug("Found " + partial.size() + " objects in one batch");
                }
                uploads.addAll(partial);

                String[] partialCommonPrefixes = handler.getCommonPrefixes();
                if (log.isDebugEnabled()) {
                    log.debug("Found " + partialCommonPrefixes.length + " common prefixes in one batch");
                }
                commonPrefixes.addAll(Arrays.asList(partialCommonPrefixes));

                incompleteListing = handler.isTruncated();

                if (incompleteListing){
                    nextKeyMarker = handler.getNextKeyMarker();
                    nextUploadIdMarker = handler.getNextUploadIdMarker();
                    // Sanity check for valid pagination values.
                    if (nextKeyMarker == null && nextUploadIdMarker == null) {
                        throw new ServiceException("Unable to retrieve paginated "
                            + "ListMultipartUploadsResult without valid NextKeyMarker "
                            + " or NextUploadIdMarker value.");
                    }
                } else {
                    nextKeyMarker = null;
                    nextUploadIdMarker = null;
                }

                if (!autoMergeChunks){
                    break;
                }
            }

            if (autoMergeChunks && log.isDebugEnabled()) {
                log.debug("Found " + uploads.size() + " uploads in total");
            }

            return new MultipartUploadChunk(
                    prefix,
                    delimiter,
                    uploads.toArray(new MultipartUpload[uploads.size()]),
                    commonPrefixes.toArray(new String[commonPrefixes.size()]),
                    nextKeyMarker,
                    nextUploadIdMarker);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected List<MultipartPart> multipartListPartsImpl(String uploadId,
        String bucketName, String objectKey) throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("uploadId", uploadId);
        requestParameters.put("max-parts", "1000");

        try {
            List<MultipartPart> parts = new ArrayList<MultipartPart>();
            String nextPartNumberMarker = null;
            boolean incompleteListing;
            do {
                if (nextPartNumberMarker != null) {
                    requestParameters.put("part-number-marker", nextPartNumberMarker);
                } else {
                    requestParameters.remove("part-number-marker");
                }

                HttpResponse httpResponse = performRestGet(bucketName, objectKey, requestParameters, null);
                ListMultipartPartsResultHandler handler = getXmlResponseSaxParser()
                    .parseListMultipartPartsResult(
                        new HttpMethodReleaseInputStream(httpResponse));
                parts.addAll(handler.getMultipartPartList());

                incompleteListing = handler.isTruncated();
                nextPartNumberMarker = handler.getNextPartNumberMarker();

                // Sanity check for valid pagination values.
                if (incompleteListing && nextPartNumberMarker == null)
                {
                    throw new ServiceException("Unable to retrieve paginated "
                        + "ListMultipartPartsResult without valid NextKeyMarker value.");
                }
            } while (incompleteListing);
            return parts;
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected NotificationConfig getNotificationConfigImpl(String bucketName)
        throws S3ServiceException
    {
        try {
            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("notification", "");

            HttpResponse getMethod = performRestGet(bucketName, null, requestParameters, null);
            return getXmlResponseSaxParser().parseNotificationConfigurationResponse(
                new HttpMethodReleaseInputStream(getMethod));
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    protected void setNotificationConfigImpl(String bucketName, NotificationConfig config)
        throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("notification", "");

        Map<String, Object> metadata = new HashMap<String, Object>();

        String xml;
        try {
            xml = config.toXml();
        } catch (Exception e) {
            throw new S3ServiceException("Unable to build NotificationConfig XML document", e);
        }

        try {
            performRestPut(bucketName, null, metadata, requestParameters,
                new StringEntity(xml, ContentType.create("text/plain", Constants.DEFAULT_ENCODING)),
                true);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    public LifecycleConfig getLifecycleConfigImpl(String bucketName) throws S3ServiceException {
        try {
            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("lifecycle", "");

            // Unset LifecycleConfig annoying returns 404, not an empty config. Fix that.
            int[] expectedStatusCodes = {200, 404};

            HttpResponse getMethod = performRestGet(
                bucketName, null, requestParameters, null, expectedStatusCodes);
            if (getMethod.getStatusLine().getStatusCode() == 404) {
                releaseConnection(getMethod);
                return null;
            } else {
                return getXmlResponseSaxParser().parseLifecycleConfigurationResponse(
                    new HttpMethodReleaseInputStream(getMethod));
            }
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    public void setLifecycleConfigImpl(String bucketName, LifecycleConfig config)
        throws S3ServiceException
    {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("lifecycle", "");

        String xml;
        String xmlMd5Hash;
        try {
            xml = config.toXml();
            xmlMd5Hash = ServiceUtils.toBase64(
                ServiceUtils.computeMD5Hash(xml.getBytes(Constants.DEFAULT_ENCODING)));
        } catch (Exception e) {
            throw new S3ServiceException("Unable to build LifecycleConfig XML document", e);
        }

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("Content-MD5", xmlMd5Hash);

        try {
            performRestPut(bucketName, null, metadata, requestParameters,
                new StringEntity(xml, ContentType.create("text/plain", Constants.DEFAULT_ENCODING)),
                true);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    public void deleteLifecycleConfigImpl(String bucketName) throws S3ServiceException {
        try {
            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("lifecycle", "");
            performRestDelete(bucketName, null, requestParameters, null, null);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    public void setAccelerateConfigImpl(String bucketName, AccelerateConfig config) throws S3ServiceException {
        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("accelerate", "");

        String xml;
        String xmlMd5Hash;
        try {
            xml = config.toXml();
            xmlMd5Hash = ServiceUtils.toBase64(
                    ServiceUtils.computeMD5Hash(xml.getBytes(Constants.DEFAULT_ENCODING)));
        } catch (Exception e) {
            throw new S3ServiceException("Unable to build AccelerateConfig XML document", e);
        }

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("Content-MD5", xmlMd5Hash);

        try {
            performRestPut(bucketName, null, metadata, requestParameters,
                    new StringEntity(xml, ContentType.create("text/plain", Constants.DEFAULT_ENCODING)),
                    true);
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    public AccelerateConfig getAccelerateConfigImpl(String bucketName) throws S3ServiceException {
        try {
            Map<String, String> requestParameters = new HashMap<String, String>();
            requestParameters.put("accelerate", "");

            int[] expectedStatusCodes = {200};

            HttpResponse getMethod = performRestGet(
                bucketName, null, requestParameters, null, expectedStatusCodes);
            return getXmlResponseSaxParser().parseAccelerateConfigurationResponse(
                new HttpMethodReleaseInputStream(getMethod));
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

    @Override
    public MultipleDeleteResult deleteMultipleObjectsWithMFAImpl(
        String bucketName, ObjectKeyAndVersion[] objectNameAndVersions,
        String multiFactorSerialNumber, String multiFactorAuthCode,
        boolean isQuiet) throws S3ServiceException
    {
        String xml, xmlMd5Hash;
        try {
            XMLBuilder builder = XMLBuilder.create("Delete")
                .attr("xmlns", Constants.XML_NAMESPACE)
                .elem("Quiet").text( (isQuiet ? String.valueOf(true) : String.valueOf(false)) ).up();
            for (ObjectKeyAndVersion nav: objectNameAndVersions) {
                XMLBuilder objectBuilder =
                    builder.elem("Object")
                        .elem("Key").text(nav.getKey()).up();
                if (nav.getVersion() != null) {
                    objectBuilder.elem("VersionId").text(nav.getVersion());
                }
            }
            xml = builder.asString();
            xmlMd5Hash = ServiceUtils.toBase64(
                ServiceUtils.computeMD5Hash(xml.getBytes(Constants.DEFAULT_ENCODING)));
        } catch (Exception e) {
            throw new S3ServiceException("Failed to build XML request document", e);
        }

        Map<String, String> requestParameters = new HashMap<String, String>();
        requestParameters.put("delete", "");

        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("Content-Type", "text/plain");
        metadata.put("Content-MD5", xmlMd5Hash);
        if (multiFactorSerialNumber != null || multiFactorAuthCode != null) {
            metadata.put(Constants.AMZ_MULTI_FACTOR_AUTH_CODE,
                multiFactorSerialNumber + " " + multiFactorAuthCode);
        }

        try {
            HttpResponse httpResponse = performRestPost(
                bucketName, null, metadata, requestParameters,
                    new StringEntity(xml, ContentType.create("text/plain", Constants.DEFAULT_ENCODING)), false);
            return getXmlResponseSaxParser().parseMultipleDeleteResponse(
                new HttpMethodReleaseInputStream(httpResponse));
        } catch (ServiceException se) {
            throw new S3ServiceException(se);
        }
    }

}
