package io.openio.sds.proxy;

import static io.openio.sds.common.Check.checkArgument;
import static io.openio.sds.common.JsonUtils.gson;
import static io.openio.sds.common.OioConstants.ACCOUNT_HEADER;
import static io.openio.sds.common.OioConstants.ACTION_MODE_HEADER;
import static io.openio.sds.common.OioConstants.CONTAINER_DEL_PROP;
import static io.openio.sds.common.OioConstants.CONTAINER_GET_PROP;
import static io.openio.sds.common.OioConstants.CONTAINER_SET_PROP;
import static io.openio.sds.common.OioConstants.CONTAINER_SYS_NAME_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_CHUNK_METHOD_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_CTIME_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_HASH_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_HASH_METHOD_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_ID_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_LENGTH_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_MIME_TYPE_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_POLICY_HEADER;
import static io.openio.sds.common.OioConstants.CONTENT_META_VERSION_HEADER;
import static io.openio.sds.common.OioConstants.CREATE_CONTAINER_FORMAT;
import static io.openio.sds.common.OioConstants.CS_GETSRV_FORMAT;
import static io.openio.sds.common.OioConstants.CS_NSINFO_FORMAT;
import static io.openio.sds.common.OioConstants.DELETE_CONTAINER_FORMAT;
import static io.openio.sds.common.OioConstants.DELETE_OBJECT_FORMAT;
import static io.openio.sds.common.OioConstants.DELIMITER_PARAM;
import static io.openio.sds.common.OioConstants.DIR_LINK_SRV_FORMAT;
import static io.openio.sds.common.OioConstants.DIR_LIST_SRV_FORMAT;
import static io.openio.sds.common.OioConstants.DIR_REF_CREATE_FORMAT;
import static io.openio.sds.common.OioConstants.DIR_REF_DELETE_FORMAT;
import static io.openio.sds.common.OioConstants.DIR_REF_SHOW_FORMAT;
import static io.openio.sds.common.OioConstants.DIR_UNLINK_SRV_FORMAT;
import static io.openio.sds.common.OioConstants.FLUSH_PARAM;
import static io.openio.sds.common.OioConstants.GET_BEANS_FORMAT;
import static io.openio.sds.common.OioConstants.GET_CONTAINER_INFO_FORMAT;
import static io.openio.sds.common.OioConstants.GET_OBJECT_FORMAT;
import static io.openio.sds.common.OioConstants.INVALID_URL_MSG;
import static io.openio.sds.common.OioConstants.LIST_MARKER_HEADER;
import static io.openio.sds.common.OioConstants.LIST_OBJECTS_FORMAT;
import static io.openio.sds.common.OioConstants.LIST_TRUNCATED_HEADER;
import static io.openio.sds.common.OioConstants.M2_CTIME_HEADER;
import static io.openio.sds.common.OioConstants.M2_INIT_HEADER;
import static io.openio.sds.common.OioConstants.M2_USAGE_HEADER;
import static io.openio.sds.common.OioConstants.M2_VERSION_HEADER;
import static io.openio.sds.common.OioConstants.MARKER_PARAM;
import static io.openio.sds.common.OioConstants.MAX_PARAM;
import static io.openio.sds.common.OioConstants.NS_HEADER;
import static io.openio.sds.common.OioConstants.OBJECT_DEL_PROP;
import static io.openio.sds.common.OioConstants.OBJECT_GET_PROP;
import static io.openio.sds.common.OioConstants.OBJECT_SET_PROP;
import static io.openio.sds.common.OioConstants.OIO_ACTION_MODE_HEADER;
import static io.openio.sds.common.OioConstants.OIO_CHARSET;
import static io.openio.sds.common.OioConstants.PREFIX_PARAM;
import static io.openio.sds.common.OioConstants.PROP_HEADER_PREFIX;
import static io.openio.sds.common.OioConstants.PROP_HEADER_PREFIX_LEN;
import static io.openio.sds.common.OioConstants.PUT_OBJECT_FORMAT;
import static io.openio.sds.common.OioConstants.SCHEMA_VERSION_HEADER;
import static io.openio.sds.common.OioConstants.TYPE_HEADER;
import static io.openio.sds.common.OioConstants.USER_NAME_HEADER;
import static io.openio.sds.common.OioConstants.VERSION_MAIN_ADMIN_HEADER;
import static io.openio.sds.common.OioConstants.VERSION_MAIN_ALIASES_HEADER;
import static io.openio.sds.common.OioConstants.VERSION_MAIN_CHUNKS_HEADER;
import static io.openio.sds.common.OioConstants.VERSION_MAIN_CONTENTS_HEADER;
import static io.openio.sds.common.OioConstants.VERSION_MAIN_PROPERTIES_HEADER;
import static io.openio.sds.common.Strings.nullOrEmpty;
import static io.openio.sds.http.OioHttpHelper.longHeader;
import static io.openio.sds.http.Verifiers.CONTAINER_VERIFIER;
import static io.openio.sds.http.Verifiers.OBJECT_VERIFIER;
import static io.openio.sds.http.Verifiers.REFERENCE_VERIFIER;
import static io.openio.sds.http.Verifiers.STANDALONE_VERIFIER;
import static java.lang.String.format;
import io.openio.sds.RequestContext;
import io.openio.sds.common.JsonUtils;
import io.openio.sds.common.OioConstants;
import io.openio.sds.common.Strings;
import io.openio.sds.exceptions.ContainerExistException;
import io.openio.sds.exceptions.ContainerNotFoundException;
import io.openio.sds.exceptions.ObjectNotFoundException;
import io.openio.sds.exceptions.OioException;
import io.openio.sds.exceptions.OioSystemException;
import io.openio.sds.http.OioHttp;
import io.openio.sds.http.OioHttpResponse;
import io.openio.sds.http.OioHttp.RequestBuilder;
import io.openio.sds.models.BeansRequest;
import io.openio.sds.models.ChunkInfo;
import io.openio.sds.models.ContainerInfo;
import io.openio.sds.models.LinkedServiceInfo;
import io.openio.sds.models.ListOptions;
import io.openio.sds.models.NamespaceInfo;
import io.openio.sds.models.ObjectInfo;
import io.openio.sds.models.ObjectList;
import io.openio.sds.models.OioUrl;
import io.openio.sds.models.ReferenceInfo;
import io.openio.sds.models.ServiceInfo;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

/**
 * Simple OpenIO proxyd http client based on API reference available at
 * https://github.com/open-io/oio-sds/wiki/OpenIO-Proxyd-API-Reference
 */
public class ProxyClient {
    private OioHttp http;
    private ProxySettings settings;
    private List<InetSocketAddress> hosts = null;

    public ProxyClient(OioHttp http, ProxySettings settings) {
        this.http = http;
        this.settings = settings;
        this.hosts = this.settings.allHosts();
    }

    /* -- CS -- */

    /**
     * Retrieves technical informations relative to the proxyd served namespace
     *
     * @param reqCtx
     *            common parameters to all requests
     * @return the matching {@code NamespaceInfo}
     */
    public NamespaceInfo getNamespaceInfo(RequestContext reqCtx) throws OioException {
        return http.get(format(CS_NSINFO_FORMAT, settings.url(), settings.ns())).hosts(hosts)
                .verifier(STANDALONE_VERIFIER).withRequestContext(reqCtx)
                .execute(NamespaceInfo.class);
    }

    /**
     * Returns available services of the specified type
     * 
     * @param type
     *            the type of service to get
     * @param reqCtx
     *            common parameters to all requests
     * @return the list of matching services
     * @throws OioException
     *             if any error occurs during request execution
     */
    public List<ServiceInfo> getServices(String type, RequestContext reqCtx) throws OioException {
        OioHttpResponse resp = http
                .get(format(CS_GETSRV_FORMAT, settings.url(), settings.ns(), type)).hosts(hosts)
                .verifier(STANDALONE_VERIFIER).withRequestContext(reqCtx).execute();
        return serviceInfoListAndClose(resp);
    }

    /* -- DIRECTORY -- */

    /**
     * Creates a reference in Oio directory.
     * 
     * @param url
     *            the url of the reference to create
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void createReference(OioUrl url) throws OioException {
        createReference(url, new RequestContext());
    }

    /**
     * Creates a reference in Oio directory.
     * 
     * @param url
     *            the url of the reference
     * @param reqCtx
     *            common parameters to all requests
     * @throws OioException
     *             if any error occurs during request execution
     */
    public void createReference(OioUrl url, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        http.post(
                format(DIR_REF_CREATE_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts).verifier(REFERENCE_VERIFIER).withRequestContext(reqCtx).execute()
                .close();
    }

    /**
     * Retrieves informations about the specified reference
     * 
     * @param url
     *            the url of the reference to look for.
     * @return informations about the reference
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public ReferenceInfo showReference(OioUrl url) throws OioException {
        return showReference(url, new RequestContext());
    }

    /**
     * Retrieves informations about the specified reference
     * 
     * @param url
     *            the url of the reference to look for.
     * @param reqCtx
     *            common parameters to all requests
     * @return informations about the reference
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ReferenceInfo showReference(OioUrl url, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        return http
                .get(format(DIR_REF_SHOW_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts).withRequestContext(reqCtx).verifier(REFERENCE_VERIFIER)
                .execute(ReferenceInfo.class);
    }

    /**
     * Deletes a reference from Oio directory. Reference should not be linked to
     * a any service to be dropped
     * 
     * @param url
     *            the url of the reference
     * @throws OioException
     *             if any error occurs during request execution
     * 
     */
    @Deprecated
    public void deleteReference(OioUrl url) throws OioException {
        deleteReference(url, new RequestContext());
    }

    /**
     * Deletes a reference from Oio directory. Reference should not be linked to
     * a any service to be dropped
     * 
     * @param url
     *            the url of the reference
     * @param reqCtx
     *            common parameters to all requests
     * @throws OioException
     *             if any error occurs during request execution
     * 
     */
    public void deleteReference(OioUrl url, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        http.post(
                format(DIR_REF_DELETE_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts).withRequestContext(reqCtx).verifier(REFERENCE_VERIFIER).execute()
                .close();
    }

    /**
     * Attachs a service of the specified type to a reference
     * 
     * @param url
     *            the url of the reference
     * @param type
     *            the type of service to link
     * @return the linked services
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public List<LinkedServiceInfo> linkService(OioUrl url, String type) throws OioException {
        return linkService(url, type, new RequestContext());
    }

    /**
     * Attachs a service of the specified type to a reference
     * 
     * @param url
     *            the url of the reference
     * @param type
     *            the type of service to link
     * @param reqCtx
     *            common parameters to all requests
     * @return the linked services
     * @throws OioException
     *             if any error occurs during request execution
     */
    public List<LinkedServiceInfo> linkService(OioUrl url, String type, RequestContext reqCtx)
            throws OioException {
        checkArgument(!nullOrEmpty(type), "Missing type");
        OioHttpResponse resp = http
                .post(format(DIR_LINK_SRV_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()), type))
                .hosts(hosts).verifier(REFERENCE_VERIFIER).withRequestContext(reqCtx).execute();

        return listAndClose(resp);
    }

    /**
     * Retrieves informations about the specified reference
     * 
     * @param url
     *            the url of the reference to look for.
     * @param type
     *            the type of service to list. Could be {@code null} to list all
     *            services
     * @return the linked services
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public List<LinkedServiceInfo> listServices(OioUrl url, String type) throws OioException {
        return listServices(url, type, new RequestContext());
    }

    /**
     * Retrieves informations about the specified reference
     * 
     * @param url
     *            the url of the reference to look for.
     * @param type
     *            the type of service to list. Could be {@code null} to list all
     *            services
     * @param reqCtx
     *            common parameters to all requests
     * @return the linked services
     * @throws OioException
     *             if any error occurs during request execution
     */
    public List<LinkedServiceInfo> listServices(OioUrl url, String type, RequestContext reqCtx)
            throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        checkArgument(!nullOrEmpty(type));
        return http
                .get(format(DIR_LIST_SRV_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()), type))
                .hosts(hosts).verifier(REFERENCE_VERIFIER).withRequestContext(reqCtx)
                .execute(ReferenceInfo.class).srv();
    }

    /**
     * Detach services from the specified URL
     * 
     * @param url
     *            the url of the reference
     * @param type
     *            the service to unlink
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void unlinkService(OioUrl url, String type) throws OioException {
        unlinkService(url, type, new RequestContext());
    }

    /**
     * Detachs services from the specified url
     * 
     * @param url
     *            the url of the reference
     * @param type
     *            the type of service to unlink
     * @param reqCtx
     *            common parameters to all requests
     * @throws OioException
     *             if any error occurs during request execution
     */
    public void unlinkService(OioUrl url, String type, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        checkArgument(!nullOrEmpty(type));
        http.post(
                format(DIR_UNLINK_SRV_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()), type))
                .hosts(hosts).verifier(REFERENCE_VERIFIER).withRequestContext(reqCtx).execute()
                .close();

    }

    /* -- STORAGE -- */

    /**
     * Creates a container using the specified {@code OioUrl}
     *
     * @param url
     *            the url of the container to create
     * @return {@code ContainerInfo}
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public ContainerInfo createContainer(OioUrl url) throws OioException {
        return createContainer(url, null, new RequestContext());
    }

    /**
     * Creates a container using the specified {@code OioUrl}
     *
     * @param url
     *            the url of the container to create
     * @param reqCtx
     *            common parameters to all requests
     * @return {@code ContainerInfo}
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ContainerInfo createContainer(OioUrl url, RequestContext reqCtx)
            throws OioException {
        return createContainer(url, null, reqCtx);
    }

    /**
     * Creates a container using the specified {@code OioUrl}
     *
     * @param url
     *            the url of the container to create
     * @param properties
     *            the properties to add
     * @param reqCtx
     *            common parameters to all requests
     * @return {@code ContainerInfo}
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ContainerInfo createContainer(OioUrl url,
            Map<String, String> properties, RequestContext reqCtx)
            throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        String body = String.format("{\"properties\": %1$s}",
                properties != null ? gson().toJson(properties) : "{}");
        OioHttpResponse resp = http.post(
                format(CREATE_CONTAINER_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()),
                        Strings.urlEncode(url.container())))
                .header(OIO_ACTION_MODE_HEADER, "autocreate")
                .body(body)
                .hosts(hosts).withRequestContext(reqCtx).verifier(CONTAINER_VERIFIER).execute()
                .close();
        if (204 == resp.code())
            throw new ContainerExistException("Container already present");

        return new ContainerInfo(url.container());
    }

    /**
     * Returns informations about the specified container
     * 
     * @param url
     *            the url of the container
     * @return the container informations
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public ContainerInfo getContainerInfo(OioUrl url) throws OioException {
        return getContainerInfo(url, new RequestContext());
    }

    /**
     * Returns informations about the specified container
     * 
     * @param url
     *            the url of the container
     * @param reqCtx
     *            common parameters to all requests
     * @return the container informations
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ContainerInfo getContainerInfo(OioUrl url, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        OioHttpResponse r = http
                .get(format(GET_CONTAINER_INFO_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts).verifier(CONTAINER_VERIFIER).withRequestContext(reqCtx).execute()
                .close();

        return new ContainerInfo(url.container()).account(r.header(ACCOUNT_HEADER))
                .ctime(longHeader(r, M2_CTIME_HEADER)).init(longHeader(r, M2_INIT_HEADER))
                .usage(longHeader(r, M2_USAGE_HEADER)).version(longHeader(r, M2_VERSION_HEADER))
                .id(r.header(CONTAINER_SYS_NAME_HEADER)).ns(r.header(NS_HEADER))
                .type(r.header(TYPE_HEADER)).user(r.header(USER_NAME_HEADER))
                .schemavers(r.header(SCHEMA_VERSION_HEADER))
                .versionMainAdmin(r.header(VERSION_MAIN_ADMIN_HEADER))
                .versionMainAliases(r.header(VERSION_MAIN_ALIASES_HEADER))
                .versionMainChunks(r.header(VERSION_MAIN_CHUNKS_HEADER))
                .versionMainContents(r.header(VERSION_MAIN_CONTENTS_HEADER))
                .versionMainProperties(r.header(VERSION_MAIN_PROPERTIES_HEADER));
    }

    /**
     * Lists all object available inside a container.
     * 
     * @param url
     *            the {@code url} of the container to list
     * @param options
     *            the options to specified to the list request. See
     *            {@linkplain ListOptions} documentation
     * @return an {@link ObjectList} matching the specified parameters.
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public ObjectList listContainer(OioUrl url, ListOptions options) throws OioException {
        return listObjects(url, options, new RequestContext());
    }

    /**
     * Lists all object available inside a container.
     * 
     * @param url
     *            the {@code url} of the container to list
     * @param options
     *            the options to specified to the list request. See
     *            {@linkplain ListOptions} documentation
     * @param reqCtx
     *            common parameters to all requests
     * @return an {@link ObjectList} matching the specified parameters.
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ObjectList listObjects(OioUrl url, ListOptions options, RequestContext reqCtx)
            throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        checkArgument(null != options, "Invalid options");
        OioHttpResponse resp = http
                .get(format(LIST_OBJECTS_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts)
                .query(MAX_PARAM, options.limit() > 0 ? String.valueOf(options.limit()) : null)
                .query(PREFIX_PARAM, options.prefix()).query(MARKER_PARAM, options.marker())
                .query(DELIMITER_PARAM, options.delimiter()).verifier(CONTAINER_VERIFIER)
                .withRequestContext(reqCtx).execute();
        boolean success = false;
        try {
            ObjectList objectList = gson().fromJson(
                    new JsonReader(new InputStreamReader(resp.body(), OIO_CHARSET)),
                    ObjectList.class);
            String truncated = resp.header(LIST_TRUNCATED_HEADER);
            if (truncated != null) {
                objectList.truncated(Boolean.parseBoolean(truncated));
                objectList.nextMarker(resp.header(LIST_MARKER_HEADER));
            }
            success = true;
            return objectList;
        } finally {
            resp.close(success);
        }
    }

    /**
     * Deletes a container from the OpenIO namespace. The container should be
     * empty to be destroyed.
     * 
     * @param url
     *            the URL of the container to destroy
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteContainer(OioUrl url) throws OioException {
        deleteContainer(url, new RequestContext());
    }

    /**
     * Deletes a container from the OpenIO namespace. The container should be
     * empty to be destroyed.
     * 
     * @param url
     *            the URL of the container to destroy
     * @param reqCtx
     *            common parameters to all requests
     * @throws OioException
     *             if any error occurs during request execution
     */
    public void deleteContainer(OioUrl url, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        http.post(
                format(DELETE_CONTAINER_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts).verifier(CONTAINER_VERIFIER).withRequestContext(reqCtx).execute()
                .close();
    }

    /**
     * Prepares an object upload by asking some chunks available location.
     * 
     * @param url
     *            the URL of the future object to create
     * @param size
     *            the size of the future object
     * @param reqCtx
     *            Common parameters to all requests
     * @return an {@link ObjectInfo} which contains all informations to upload
     *         the object
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ObjectInfo preparePutObject(OioUrl url, long size, RequestContext reqCtx)
            throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        OioHttpResponse resp = http
                .post(format(GET_BEANS_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()),
                        Strings.urlEncode(url.object())))
                .body(gson().toJson(new BeansRequest().size(size)))
                .hosts(hosts)
                .header(ACTION_MODE_HEADER,
                        settings.autocreate() ? OioConstants.AUTOCREATE_ACTION_MODE : null)
                .withRequestContext(reqCtx).verifier(OBJECT_VERIFIER).execute();
        return getBeansObjectInfoAndClose(url, resp);
    }

    /**
     * Validate an object upload in the OpenIO-SDS namespace.
     *
     * @param oinf
     *            the {@link ObjectInfo} containing informations about the
     *            uploaded object
     * @param version
     *            the version to set (could be {@code null} to the object
     * @param reqCtx
     *            Common parameters to all requests
     * @return the validated object.
     * @throws OioException
     *             if any error occurs during request execution
     */
    public ObjectInfo putObject(ObjectInfo oinf, Long version, RequestContext reqCtx)
            throws OioException {
        checkArgument(oinf != null, "Invalid objectInfo");
        Map<String, String> props = oinf.properties();
        String body = String.format("{\"chunks\": %1$s, \"properties\": %2$s}",
                gson().toJson(oinf.chunks()), props != null ? gson().toJson(props) : "{}");
        http.post(
                format(PUT_OBJECT_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(oinf.url().account()),
                        Strings.urlEncode(oinf.url().container()),
                        Strings.urlEncode(oinf.url().object())))
                .header(CONTENT_META_LENGTH_HEADER, String.valueOf(oinf.size()))
                .header(CONTENT_META_HASH_HEADER, oinf.hash())
                .header(CONTENT_META_POLICY_HEADER, oinf.policy())
                .header(CONTENT_META_CHUNK_METHOD_HEADER, oinf.chunkMethod())
                .header(CONTENT_META_VERSION_HEADER, versionHeader(oinf, version))
                .header(CONTENT_META_ID_HEADER, oinf.oid())
                .body(body)
                .hosts(hosts).withRequestContext(reqCtx).verifier(OBJECT_VERIFIER).execute()
                .close();
        return oinf;
    }

    /**
     * Returns informations about the specified object
     * 
     * @param url
     *            the url of the object to look for
     * @return an {@link ObjectInfo} containing informations about the object
     */
    @Deprecated
    public ObjectInfo getObjectInfo(OioUrl url) throws OioException {
        return getObjectInfo(url, true);
    }

    /**
     * Returns informations about the specified object
     *
     * @param url
     *            the url of the object to look for
     * @param loadProperties
     *            Whether or not to load properties
     * @return an {@link ObjectInfo} containing informations about the object
     */
    @Deprecated
    public ObjectInfo getObjectInfo(OioUrl url, boolean loadProperties) throws OioException {
        return getObjectInfo(url, null, loadProperties);
    }

    /**
     * Returns informations about the specified object
     * 
     * @param url
     *            the url of the object to look for
     * @param version
     *            the version of the content to get
     * @return an {@link ObjectInfo} containing informations about the object
     */
    @Deprecated
    public ObjectInfo getObjectInfo(OioUrl url, Long version) throws OioException {
        return getObjectInfo(url, version, true);
    }

    /**
     * Returns informations about the specified object
     *
     * @param url
     *            the url of the object to look for
     * @param version
     *            the version of the content to get
     * @param loadProperties
     *            Whether or not to load properties
     * @return an {@link ObjectInfo} containing informations about the object
     */
    @Deprecated
    public ObjectInfo getObjectInfo(OioUrl url, Long version, boolean loadProperties)
            throws OioException {
        return getObjectInfo(url, version, new RequestContext(), loadProperties);
    }

    /**
     * Returns informations about the specified object
     * 
     * @param url
     *            the url of the object to look for
     * @param version
     *            the version to get (could be {@code null} to get latest
     *            version)
     * @param reqCtx
     *            common parameters to all requests
     * @return an {@link ObjectInfo} containing informations about the object
     */
    public ObjectInfo getObjectInfo(OioUrl url, Long version, RequestContext reqCtx)
            throws OioException {
        return getObjectInfo(url, version, reqCtx, true);
    }

    /**
     * Returns informations about the specified object
     *
     * @param url
     *            the url of the object to look for
     * @param version
     *            the version to get (could be {@code null} to get latest
     *            version)
     * @param reqCtx
     *            common parameters to all requests
     * @param loadProperties
     *            Whether or not to load properties
     * @return an {@link ObjectInfo} containing informations about the object
     */
    public ObjectInfo getObjectInfo(OioUrl url, Long version, RequestContext reqCtx,
            boolean loadProperties) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        String uri = format(GET_OBJECT_FORMAT, settings.url(), settings.ns(),
                Strings.urlEncode(url.account()), Strings.urlEncode(url.container()),
                Strings.urlEncode(url.object()));
        if (version != null) {
            uri += "&version=" + version.toString();
        }
        OioHttpResponse resp = http.get(uri).hosts(hosts).verifier(OBJECT_VERIFIER)
                .withRequestContext(reqCtx).execute();
        ObjectInfo info = objectShowObjectInfoAndClose(url, resp);
        if (loadProperties) {
            info.properties(getObjectProperties(url, reqCtx));
        }
        return info;
    }

    /**
     * Deletes an object from its container
     * 
     * @param url
     *            the url of the object to delete
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteObject(OioUrl url) throws OioException {
        deleteObject(url, null, new RequestContext());
    }

    /**
     * Deletes an object from its container
     * 
     * @param url
     *            the url of the object to delete
     * @param version
     *            the version to delete (could be {@code null} to delete latest
     *            version)
     * @throws OioException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteObject(OioUrl url, Long version) throws OioException {
        deleteObject(url, version, new RequestContext());
    }

    /**
     * Deletes an object from its container
     * 
     * @param url
     *            the url of the object to delete
     * @param version
     *            the version to delete (could be {@code null} to delete latest
     *            version)
     * @param reqCtx
     *            common paramters to all requests
     * @throws OioException
     *             if any error occurs during request execution
     */
    public void deleteObject(OioUrl url, Long version, RequestContext reqCtx) throws OioException {
        checkArgument(null != url, INVALID_URL_MSG);
        http.post(
                format(DELETE_OBJECT_FORMAT, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()),
                        Strings.urlEncode(url.object())))
                .header(CONTENT_META_VERSION_HEADER, null == version ? null : version.toString())
                .verifier(OBJECT_VERIFIER).withRequestContext(reqCtx).hosts(hosts).execute()
                .close();
    }

    /* -- PROPERTIES -- */

    /**
     * Add properties to the specified container. The properties must be
     * prefixed with "user." and this prefix will be stored, and finally used to
     * query the parameters later
     * 
     * @param url
     *            the URL of the container to add properties
     * @param properties
     *            the properties to add
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void setContainerProperties(OioUrl url,
            Map<String, String> properties) {
        setContainerProperties(url, properties, false, new RequestContext());
    }

    /**
     * Add properties to the specified container. The properties must be
     * prefixed with "user." and this prefix will be stored, and finally used to
     * query the parameters later
     * 
     * @param url
     *            the URL of the container to add properties
     * @param properties
     *            the properties to add
     * @param reqCtx
     *            common parameters to all requests
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void setContainerProperties(OioUrl url,
            Map<String, String> properties, RequestContext reqCtx) {
        setContainerProperties(url, properties, false, reqCtx);
    }

    /**
     * Add properties to the specified container. The properties must be
     * prefixed with "user." and this prefix will be stored, and finally used to
     * query the parameters later
     * 
     * @param url
     *            the URL of the container to add properties
     * @param properties
     *            the properties to add
     * @param reqCtx
     *            common parameters to all requests
     * @param clear
     *            clear previous properties
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public void setContainerProperties(OioUrl url,
            Map<String, String> properties, boolean clear,
            RequestContext reqCtx) {
        checkArgument(null != url, INVALID_URL_MSG);
        checkArgument(null != properties && properties.size() > 0, "Invalid properties");
        String props = gson().toJson(properties);
        String root = String.format("{\"properties\": %1$s}", props);
        RequestBuilder request = http.post(format(CONTAINER_SET_PROP,
                settings.url(), settings.ns(),
                Strings.urlEncode(url.account()),
                Strings.urlEncode(url.container())));
        if (clear)
            request.query(FLUSH_PARAM, "1");
        request.verifier(CONTAINER_VERIFIER)
                .withRequestContext(reqCtx).hosts(hosts).body(root)
                .execute().close();
    }

    /**
     * Retrieves user properties of the specified container
     * 
     * @param url
     *            the url of the object
     * @return the user properties (i.e. prefixed with "user.") found on the
     *         object
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public Map<String, String> getContainerProperties(OioUrl url) {
        return getContainerProperties(url, new RequestContext());
    }

    /**
     * Retrieves user properties of the specified container
     * 
     * @param url
     *            the url of the object
     * @param reqCtx
     *            common parameters to all requests
     * @return the user properties (i.e. prefixed with "user.") found on the
     *         object
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public Map<String, String> getContainerProperties(OioUrl url, RequestContext reqCtx) {
        checkArgument(null != url, INVALID_URL_MSG);
        OioHttpResponse resp = http
                .post(format(CONTAINER_GET_PROP, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .hosts(hosts).verifier(CONTAINER_VERIFIER).withRequestContext(reqCtx).execute();
        try {
            Map<String, Map<String, String>> res = JsonUtils.jsonToMapMap(resp.body());
            return res.get("properties");
        } finally {
            resp.close();
        }

    }

    /**
     * Deletes user properties from the specified container
     * 
     * @param url
     *            the url of the container
     * @param keys
     *            the property keys to drop
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteContainerProperties(OioUrl url, String... keys) {
        deleteContainerProperties(new RequestContext(), url, keys);
    }

    /**
     * Deletes user properties from the specified container
     *
     * @param reqCtx
     *            common parameters to all requests
     * @param url
     *            the url of the container
     * @param keys
     *            the property keys to drop
     * 
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public void deleteContainerProperties(RequestContext reqCtx, OioUrl url, String... keys) {
        checkArgument(null != url, INVALID_URL_MSG);
        checkArgument(null != keys && 0 < keys.length);
        http.post(
                format(CONTAINER_DEL_PROP, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .body(gson().toJson(keys)).hosts(hosts).verifier(CONTAINER_VERIFIER)
                .withRequestContext(reqCtx).execute().close();
    }

    /**
     * Deletes user properties from the specified container
     * 
     * @param url
     *            the url of the container
     * @param keys
     *            the property keys to drop
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteContainerProperties(OioUrl url, List<String> keys) {
        deleteContainerProperties(url, keys, new RequestContext());
    }

    /**
     * Deletes user properties from the specified container
     * 
     * @param url
     *            the url of the container
     * @param keys
     *            the property keys to drop
     * @param reqCtx
     *            common parameters to all requests
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public void deleteContainerProperties(OioUrl url, List<String> keys, RequestContext reqCtx) {
        checkArgument(null != url, INVALID_URL_MSG);
        checkArgument(null != keys && 0 < keys.size());
        http.post(
                format(CONTAINER_DEL_PROP, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container())))
                .body(gson().toJson(keys)).hosts(hosts).verifier(CONTAINER_VERIFIER)
                .withRequestContext(reqCtx).execute().close();
    }

    /**
     * Add properties to the specified object. The properties must be prefixed
     * with "user." and this prefix will be stored, and finally used to query
     * the parameters later.
     *
     * @param url
     *            the URL of the object
     * @param properties
     *            the properties to set
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void setObjectProperties(OioUrl url, Map<String, String> properties) {
        setObjectProperties(url, properties, false, new RequestContext());
    }

    /**
     * Add properties to the specified object. The properties must be prefixed
     * with "user." and this prefix will be stored, and finally used to query
     * the parameters later.
     *
     * @param url
     *            the URL of the object
     * @param properties
     *            the properties to set
     * @param reqCtx
     *            common parameters to all requests
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void setObjectProperties(OioUrl url, Map<String, String> properties,
            RequestContext reqCtx) {
        setObjectProperties(url, properties, false, reqCtx);
    }

    /**
     * Add properties to the specified object. The properties must be prefixed
     * with "user." and this prefix will be stored, and finally used to query
     * the parameters later.
     *
     * @param url
     *            the URL of the object
     * @param properties
     *            the properties to set
     * @param clear
     *            clear previous properties
     * @param reqCtx
     *            common parameters to all requests
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public void setObjectProperties(OioUrl url, Map<String, String> properties,
            boolean clear, RequestContext reqCtx) {
        checkArgument(null != url && null != url.object(), INVALID_URL_MSG);
        checkArgument(null != properties && properties.size() > 0, "Invalid properties");
        String body = String.format("{\"properties\": %1$s}",
                gson().toJson(properties));
        RequestBuilder request = http.post(format(OBJECT_SET_PROP,
                settings.url(), settings.ns(),
                Strings.urlEncode(url.account()),
                Strings.urlEncode(url.container()),
                Strings.urlEncode(url.object())));
        if (clear)
            request.query(FLUSH_PARAM, "1");
        request.verifier(OBJECT_VERIFIER)
                .withRequestContext(reqCtx).hosts(hosts).body(body)
                .execute().close();
    }

    /**
     * Retrieves user properties of the specified object
     * 
     * @param url
     *            the url of the object
     * @return the user properties (i.e. prefixed with "user.") found on the
     *         object
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public Map<String, String> getObjectProperties(OioUrl url) {
        return getObjectProperties(url, new RequestContext());
    }

    /**
     * Retrieves user properties of the specified object
     * 
     * @param url
     *            the url of the object
     * @param reqCtx
     *            common parameters to all requests
     * @return the user properties (i.e. prefixed with "user.") found on the
     *         object
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public Map<String, String> getObjectProperties(OioUrl url, RequestContext reqCtx) {
        checkArgument(null != url && null != url.object(), INVALID_URL_MSG);
        OioHttpResponse resp = http
                .post(format(OBJECT_GET_PROP, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()),
                        Strings.urlEncode(url.object()))).hosts(hosts).verifier(OBJECT_VERIFIER)
                .withRequestContext(reqCtx).execute();
        try {
            Map<String, Map<String, String>> rootMap = JsonUtils.jsonToMapMap(resp.body());
            return rootMap.get("properties");
        } finally {
            resp.close();
        }
    }

    /**
     * Deletes the specified properties from the object
     * 
     * @param url
     *            the url of the object
     * @param keys
     *            the property keys to drop
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteObjectProperties(OioUrl url, String... keys) {
        deleteObjectProperties(new RequestContext(), url, keys);
    }

    /**
     * Deletes the specified properties from the object
     * 
     * @param url
     *            the url of the object
     * @param keys
     *            the property keys to drop
     * @param reqCtx
     *            common parameters to all requests
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public void deleteObjectProperties(RequestContext reqCtx, OioUrl url, String... keys) {
        checkArgument(null != url && null != url.object(), INVALID_URL_MSG);
        String body = "[]";
        if (keys != null)
            body = gson().toJson(keys);
        http.post(
                format(OBJECT_DEL_PROP, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()),
                        Strings.urlEncode(url.object()))).hosts(hosts).body(body)
                .verifier(CONTAINER_VERIFIER).withRequestContext(reqCtx).execute().close();
    }

    /**
     * Deletes the specified properties from the object
     * 
     * @param url
     *            the url of the object
     * @param keys
     *            the property keys to drop
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    @Deprecated
    public void deleteObjectProperties(OioUrl url, List<String> keys) {
        deleteObjectProperties(url, keys, new RequestContext());
    }

    /**
     * Deletes the specified properties from the object
     * 
     * @param url
     *            the url of the object
     * @param keys
     *            the property keys to drop
     * @param reqCtx
     *            common parameters to all requests
     * @throws ContainerNotFoundException
     *             if the specified container doesn't exist
     * @throws ObjectNotFoundException
     *             if the specified object doesn't exist
     * @throws OioSystemException
     *             if any error occurs during request execution
     */
    public void deleteObjectProperties(OioUrl url, List<String> keys, RequestContext reqCtx) {
        checkArgument(null != url && null != url.object(), INVALID_URL_MSG);
        String body = "[]";
        if (keys != null)
            body = gson().toJson(keys);
        http.post(
                format(OBJECT_DEL_PROP, settings.url(), settings.ns(),
                        Strings.urlEncode(url.account()), Strings.urlEncode(url.container()),
                        Strings.urlEncode(url.object()))).hosts(hosts).body(body)
                .verifier(CONTAINER_VERIFIER).withRequestContext(reqCtx).execute().close();
    }

    /* -- INTERNALS -- */

    private ObjectInfo getBeansObjectInfoAndClose(OioUrl url, OioHttpResponse resp) {
        boolean success = false;
        try {
            ObjectInfo oinf = fillObjectInfo(url, resp);
            List<ChunkInfo> chunks = bodyChunk(resp);
            // check if we are using EC with ec daemon
            if (oinf.isEC()) {
                if (settings.ecdrain()) {
                    if (Strings.nullOrEmpty(settings.ecd()))
                        throw new OioException("Missing proxy#ecd configuration");
                } else {
                    // TODO impl EC in java
                    throw new IllegalStateException(
                            "Invalid configuration, we cannot do EC without ecd ATM");
                }
            }
            oinf.chunks(chunks);

            success = true;
            return oinf;
        } finally {
            resp.close(success);
        }
    }

    private ObjectInfo objectShowObjectInfoAndClose(OioUrl url, OioHttpResponse resp) {
        boolean success = false;
        try {
            ObjectInfo oinf = fillObjectInfo(url, resp);
            List<ChunkInfo> chunks = bodyChunk(resp);
            // check if we are using EC with ecd
            if (oinf.chunkMethod().startsWith(OioConstants.EC_PREFIX)
                    && (!settings.ecdrain() || Strings.nullOrEmpty(settings.ecd())))
                throw new OioException("Unable to decode EC encoded object without ecd");
            oinf.chunks(chunks);
            success = true;
            return oinf;
        } finally {
            resp.close(success);
        }
    }

    private ObjectInfo fillObjectInfo(OioUrl url, OioHttpResponse r) {
        return new ObjectInfo().url(url).oid(r.header(CONTENT_META_ID_HEADER))
                .size(longHeader(r, CONTENT_META_LENGTH_HEADER))
                .ctime(longHeader(r, CONTENT_META_CTIME_HEADER))
                .chunkMethod(r.header(CONTENT_META_CHUNK_METHOD_HEADER))
                .policy(r.header(CONTENT_META_POLICY_HEADER))
                .version(longHeader(r, CONTENT_META_VERSION_HEADER))
                .hash(r.header(OioConstants.CONTENT_META_HASH_HEADER))
                .hashMethod(r.header(CONTENT_META_HASH_METHOD_HEADER))
                .mtype(r.header(CONTENT_META_MIME_TYPE_HEADER))
                .properties(propsFromHeaders(r.headers()))
                .withRequestContext(r.requestContext());
    }

    private List<ChunkInfo> bodyChunk(OioHttpResponse resp) throws OioException {
        try {
            return gson().fromJson(new JsonReader(new InputStreamReader(resp.body(), OIO_CHARSET)),
                    new TypeToken<List<ChunkInfo>>() {
                    }.getType());
        } catch (Exception e) {
            throw new OioException("Body extraction error", e);
        }
    }

    private <T> List<T> listAndClose(OioHttpResponse resp) {
        boolean success = false;
        try {
            Type t = new TypeToken<List<T>>() {
            }.getType();
            List<T> res = gson().fromJson(
                    new JsonReader(new InputStreamReader(resp.body(), OIO_CHARSET)), t);
            success = true;
            return res;
        } catch (Exception e) {
            throw new OioException("Body extraction error", e);
        } finally {
            resp.close(success);
        }
    }

    private List<ServiceInfo> serviceInfoListAndClose(OioHttpResponse resp) {
        boolean success = false;
        try {
            Type t = new TypeToken<List<ServiceInfo>>() {
            }.getType();
            List<ServiceInfo> res = gson().fromJson(
                    new JsonReader(new InputStreamReader(resp.body(), OIO_CHARSET)), t);
            success = true;
            return res;
        } catch (Exception e) {
            throw new OioException("Body extraction error", e);
        } finally {
            resp.close(success);
        }
    }

    private String versionHeader(ObjectInfo oinf, Long version) {
        return null == version ? null == oinf.version() ? null : oinf.version().toString()
                : version.toString();
    }

    private Map<String, String> propsFromHeaders(HashMap<String, String> headers) {
        HashMap<String, String> res = new HashMap<String, String>();
        for (Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().startsWith(PROP_HEADER_PREFIX))
                res.put(e.getKey().substring(PROP_HEADER_PREFIX_LEN), e.getValue());
        }
        return res;
    }
}
