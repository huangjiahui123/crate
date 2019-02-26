/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.es;

import io.crate.es.common.ParsingException;
import io.crate.es.common.blobstore.BlobStoreException;
import io.crate.es.common.breaker.CircuitBreakingException;
import io.crate.es.common.io.stream.NotSerializableExceptionWrapper;
import io.crate.es.common.settings.NoClassSettingsException;
import io.crate.es.common.settings.SettingsException;
import io.crate.es.common.util.CancellableThreads;
import io.crate.es.common.util.concurrent.UncategorizedExecutionException;
import io.crate.es.action.admin.indices.alias.AliasesNotFoundException;
import io.crate.es.action.support.replication.ReplicationOperation;
import io.crate.es.cluster.action.shard.ShardStateAction;
import io.crate.es.common.CheckedFunction;
import io.crate.es.common.Nullable;
import io.crate.es.common.ParseField;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.io.stream.Writeable;
import io.crate.es.common.logging.LoggerMessageFormat;
import io.crate.es.common.xcontent.ToXContentFragment;
import io.crate.es.common.xcontent.XContentBuilder;
import io.crate.es.common.xcontent.XContentParseException;
import io.crate.es.common.xcontent.XContentParser;
import io.crate.es.index.Index;
import io.crate.es.index.shard.ShardId;
import io.crate.es.rest.RestStatus;
import io.crate.es.transport.TcpTransport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static io.crate.es.cluster.metadata.IndexMetaData.INDEX_UUID_NA_VALUE;
import static io.crate.es.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static io.crate.es.common.xcontent.XContentParserUtils.ensureFieldName;

/**
 * A base class for all elasticsearch exceptions.
 */
public class ElasticsearchException extends RuntimeException implements ToXContentFragment, Writeable {

    private static final Version UNKNOWN_VERSION_ADDED = Version.fromId(0);

    /**
     * Passed in the {@link Params} of {@link #generateThrowableXContent(XContentBuilder, Params, Throwable)}
     * to control if the {@code caused_by} element should render. Unlike most parameters to {@code toXContent} methods this parameter is
     * internal only and not available as a URL parameter.
     */
    private static final String REST_EXCEPTION_SKIP_CAUSE = "rest.exception.cause.skip";
    /**
     * Passed in the {@link Params} of {@link #generateThrowableXContent(XContentBuilder, Params, Throwable)}
     * to control if the {@code stack_trace} element should render. Unlike most parameters to {@code toXContent} methods this parameter is
     * internal only and not available as a URL parameter. Use the {@code error_trace} parameter instead.
     */
    public static final String REST_EXCEPTION_SKIP_STACK_TRACE = "rest.exception.stacktrace.skip";
    public static final boolean REST_EXCEPTION_SKIP_STACK_TRACE_DEFAULT = true;
    private static final boolean REST_EXCEPTION_SKIP_CAUSE_DEFAULT = false;
    private static final String INDEX_METADATA_KEY = "es.index";
    private static final String INDEX_METADATA_KEY_UUID = "es.index_uuid";
    private static final String SHARD_METADATA_KEY = "es.shard";
    private static final String RESOURCE_METADATA_TYPE_KEY = "es.resource.type";
    private static final String RESOURCE_METADATA_ID_KEY = "es.resource.id";

    private static final String TYPE = "type";
    private static final String REASON = "reason";
    private static final String CAUSED_BY = "caused_by";
    private static final ParseField SUPPRESSED = new ParseField("suppressed");
    private static final String STACK_TRACE = "stack_trace";
    private static final String HEADER = "header";
    private static final String ERROR = "error";
    private static final String ROOT_CAUSE = "root_cause";

    private static final Map<Integer, CheckedFunction<StreamInput, ? extends ElasticsearchException, IOException>> ID_TO_SUPPLIER;
    private static final Map<Class<? extends ElasticsearchException>, ElasticsearchExceptionHandle> CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE;
    private final Map<String, List<String>> metadata = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();

    /**
     * Construct a <code>ElasticsearchException</code> with the specified cause exception.
     */
    public ElasticsearchException(Throwable cause) {
        super(cause);
    }

    /**
     * Construct a <code>ElasticsearchException</code> with the specified detail message.
     *
     * The message can be parameterized using <code>{}</code> as placeholders for the given
     * arguments
     *
     * @param msg  the detail message
     * @param args the arguments for the message
     */
    public ElasticsearchException(String msg, Object... args) {
        super(LoggerMessageFormat.format(msg, args));
    }

    /**
     * Construct a <code>ElasticsearchException</code> with the specified detail message
     * and nested exception.
     *
     * The message can be parameterized using <code>{}</code> as placeholders for the given
     * arguments
     *
     * @param msg   the detail message
     * @param cause the nested exception
     * @param args  the arguments for the message
     */
    public ElasticsearchException(String msg, Throwable cause, Object... args) {
        super(LoggerMessageFormat.format(msg, args), cause);
    }

    public ElasticsearchException(StreamInput in) throws IOException {
        super(in.readOptionalString(), in.readException());
        readStackTrace(this, in);
        headers.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
        metadata.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
    }

    /**
     * Adds a new piece of metadata with the given key.
     * If the provided key is already present, the corresponding metadata will be replaced
     */
    public void addMetadata(String key, String... values) {
        addMetadata(key, Arrays.asList(values));
    }

    /**
     * Adds a new piece of metadata with the given key.
     * If the provided key is already present, the corresponding metadata will be replaced
     */
    public void addMetadata(String key, List<String> values) {
        //we need to enforce this otherwise bw comp doesn't work properly, as "es." was the previous criteria to split headers in two sets
        if (key.startsWith("es.") == false) {
            throw new IllegalArgumentException("exception metadata must start with [es.], found [" + key + "] instead");
        }
        this.metadata.put(key, values);
    }

    /**
     * Returns a set of all metadata keys on this exception
     */
    public Set<String> getMetadataKeys() {
        return metadata.keySet();
    }

    /**
     * Returns the list of metadata values for the given key or {@code null} if no metadata for the
     * given key exists.
     */
    public List<String> getMetadata(String key) {
        return metadata.get(key);
    }

    protected Map<String, List<String>> getMetadata() {
        return metadata;
    }

    /**
     * Adds a new header with the given key.
     * This method will replace existing header if a header with the same key already exists
     */
    public void addHeader(String key, List<String> value) {
        //we need to enforce this otherwise bw comp doesn't work properly, as "es." was the previous criteria to split headers in two sets
        if (key.startsWith("es.")) {
            throw new IllegalArgumentException("exception headers must not start with [es.], found [" + key + "] instead");
        }
        this.headers.put(key, value);
    }

    /**
     * Returns a set of all header keys on this exception
     */
    public Set<String> getHeaderKeys() {
        return headers.keySet();
    }

    /**
     * Returns the list of header values for the given key or {@code null} if no header for the
     * given key exists.
     */
    public List<String> getHeader(String key) {
        return headers.get(key);
    }

    protected Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Returns the rest status code associated with this exception.
     */
    public RestStatus status() {
        Throwable cause = unwrapCause();
        if (cause == this) {
            return RestStatus.INTERNAL_SERVER_ERROR;
        } else {
            return ExceptionsHelper.status(cause);
        }
    }

    /**
     * Unwraps the actual cause from the exception for cases when the exception is a
     * {@link ElasticsearchWrapperException}.
     *
     * @see ExceptionsHelper#unwrapCause(Throwable)
     */
    public Throwable unwrapCause() {
        return ExceptionsHelper.unwrapCause(this);
    }

    /**
     * Return the detail message, including the message from the nested exception
     * if there is one.
     */
    public String getDetailedMessage() {
        if (getCause() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(toString()).append("; ");
            if (getCause() instanceof ElasticsearchException) {
                sb.append(((ElasticsearchException) getCause()).getDetailedMessage());
            } else {
                sb.append(getCause());
            }
            return sb.toString();
        } else {
            return super.toString();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(this.getMessage());
        out.writeException(this.getCause());
        writeStackTraces(this, out);
        out.writeMapOfLists(headers, StreamOutput::writeString, StreamOutput::writeString);
        out.writeMapOfLists(metadata, StreamOutput::writeString, StreamOutput::writeString);
    }

    public static ElasticsearchException readException(StreamInput input, int id) throws IOException {
        CheckedFunction<StreamInput, ? extends ElasticsearchException, IOException> elasticsearchException = ID_TO_SUPPLIER.get(id);
        if (elasticsearchException == null) {
            throw new IllegalStateException("unknown exception for id: " + id);
        }
        return elasticsearchException.apply(input);
    }

    /**
     * Returns <code>true</code> iff the given class is a registered for an exception to be read.
     */
    public static boolean isRegistered(Class<? extends Throwable> exception, Version version) {
        ElasticsearchExceptionHandle elasticsearchExceptionHandle = CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE.get(exception);
        if (elasticsearchExceptionHandle != null) {
            return version.onOrAfter(elasticsearchExceptionHandle.versionAdded);
        }
        return false;
    }

    /**
     * Returns the serialization id the given exception.
     */
    public static int getId(Class<? extends ElasticsearchException> exception) {
        return CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE.get(exception).id;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        Throwable ex = ExceptionsHelper.unwrapCause(this);
        if (ex != this) {
            generateThrowableXContent(builder, params, this);
        } else {
            innerToXContent(builder, params, this, getExceptionName(), getMessage(), headers, metadata, getCause());
        }
        return builder;
    }

    protected static void innerToXContent(XContentBuilder builder, Params params,
                                          Throwable throwable, String type, String message, Map<String, List<String>> headers,
                                          Map<String, List<String>> metadata, Throwable cause) throws IOException {
        builder.field(TYPE, type);
        builder.field(REASON, message);

        for (Map.Entry<String, List<String>> entry : metadata.entrySet()) {
            headerToXContent(builder, entry.getKey().substring("es.".length()), entry.getValue());
        }

        if (throwable instanceof ElasticsearchException) {
            ElasticsearchException exception = (ElasticsearchException) throwable;
            exception.metadataToXContent(builder, params);
        }

        if (params.paramAsBoolean(REST_EXCEPTION_SKIP_CAUSE, REST_EXCEPTION_SKIP_CAUSE_DEFAULT) == false) {
            if (cause != null) {
                builder.field(CAUSED_BY);
                builder.startObject();
                generateThrowableXContent(builder, params, cause);
                builder.endObject();
            }
        }

        if (headers.isEmpty() == false) {
            builder.startObject(HEADER);
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                headerToXContent(builder, entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }

        if (params.paramAsBoolean(REST_EXCEPTION_SKIP_STACK_TRACE, REST_EXCEPTION_SKIP_STACK_TRACE_DEFAULT) == false) {
            builder.field(STACK_TRACE, ExceptionsHelper.stackTrace(throwable));
        }

        Throwable[] allSuppressed = throwable.getSuppressed();
        if (allSuppressed.length > 0) {
            builder.startArray(SUPPRESSED.getPreferredName());
            for (Throwable suppressed : allSuppressed) {
                builder.startObject();
                generateThrowableXContent(builder, params, suppressed);
                builder.endObject();
            }
            builder.endArray();
        }
    }

    private static void headerToXContent(XContentBuilder builder, String key, List<String> values) throws IOException {
        if (values != null && values.isEmpty() == false) {
            if (values.size() == 1) {
                builder.field(key, values.get(0));
            } else {
                builder.startArray(key);
                for (String value : values) {
                    builder.value(value);
                }
                builder.endArray();
            }
        }
    }

    /**
     * Renders additional per exception information into the XContent
     */
    protected void metadataToXContent(XContentBuilder builder, Params params) throws IOException {
    }

    /**
     * Generate a {@link ElasticsearchException} from a {@link XContentParser}. This does not
     * return the original exception type (ie NodeClosedException for example) but just wraps
     * the type, the reason and the cause of the exception. It also recursively parses the
     * tree structure of the cause, returning it as a tree structure of {@link ElasticsearchException}
     * instances.
     */
    public static ElasticsearchException fromXContent(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.nextToken();
        ensureExpectedToken(XContentParser.Token.FIELD_NAME, token, parser::getTokenLocation);
        return innerFromXContent(parser, false);
    }

    public static ElasticsearchException innerFromXContent(XContentParser parser, boolean parseRootCauses) throws IOException {
        XContentParser.Token token = parser.currentToken();
        ensureExpectedToken(XContentParser.Token.FIELD_NAME, token, parser::getTokenLocation);

        String type = null, reason = null, stack = null;
        ElasticsearchException cause = null;
        Map<String, List<String>> metadata = new HashMap<>();
        Map<String, List<String>> headers = new HashMap<>();
        List<ElasticsearchException> rootCauses = new ArrayList<>();
        List<ElasticsearchException> suppressed = new ArrayList<>();

        for (; token == XContentParser.Token.FIELD_NAME; token = parser.nextToken()) {
            String currentFieldName = parser.currentName();
            token = parser.nextToken();

            if (token.isValue()) {
                if (TYPE.equals(currentFieldName)) {
                    type = parser.text();
                } else if (REASON.equals(currentFieldName)) {
                    reason = parser.text();
                } else if (STACK_TRACE.equals(currentFieldName)) {
                    stack = parser.text();
                } else if (token == XContentParser.Token.VALUE_STRING) {
                    metadata.put(currentFieldName, Collections.singletonList(parser.text()));
                }
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (CAUSED_BY.equals(currentFieldName)) {
                    cause = fromXContent(parser);
                } else if (HEADER.equals(currentFieldName)) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                        if (token == XContentParser.Token.FIELD_NAME) {
                            currentFieldName = parser.currentName();
                        } else {
                            List<String> values = headers.getOrDefault(currentFieldName, new ArrayList<>());
                            if (token == XContentParser.Token.VALUE_STRING) {
                                values.add(parser.text());
                            } else if (token == XContentParser.Token.START_ARRAY) {
                                while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                                    if (token == XContentParser.Token.VALUE_STRING) {
                                        values.add(parser.text());
                                    } else {
                                        parser.skipChildren();
                                    }
                                }
                            } else if (token == XContentParser.Token.START_OBJECT) {
                                parser.skipChildren();
                            }
                            headers.put(currentFieldName, values);
                        }
                    }
                } else {
                    // Any additional metadata object added by the metadataToXContent method is ignored
                    // and skipped, so that the parser does not fail on unknown fields. The parser only
                    // support metadata key-pairs and metadata arrays of values.
                    parser.skipChildren();
                }
            } else if (token == XContentParser.Token.START_ARRAY) {
                if (parseRootCauses && ROOT_CAUSE.equals(currentFieldName)) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                        rootCauses.add(fromXContent(parser));
                    }
                } else if (SUPPRESSED.match(currentFieldName, parser.getDeprecationHandler())) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                        suppressed.add(fromXContent(parser));
                    }
                } else {
                    // Parse the array and add each item to the corresponding list of metadata.
                    // Arrays of objects are not supported yet and just ignored and skipped.
                    List<String> values = new ArrayList<>();
                    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                        if (token == XContentParser.Token.VALUE_STRING) {
                            values.add(parser.text());
                        } else {
                            parser.skipChildren();
                        }
                    }
                    if (values.size() > 0) {
                        if (metadata.containsKey(currentFieldName)) {
                            values.addAll(metadata.get(currentFieldName));
                        }
                        metadata.put(currentFieldName, values);
                    }
                }
            }
        }

        ElasticsearchException e = new ElasticsearchException(buildMessage(type, reason, stack), cause);
        for (Map.Entry<String, List<String>> entry : metadata.entrySet()) {
            //subclasses can print out additional metadata through the metadataToXContent method. Simple key-value pairs will be
            //parsed back and become part of this metadata set, while objects and arrays are not supported when parsing back.
            //Those key-value pairs become part of the metadata set and inherit the "es." prefix as that is currently required
            //by addMetadata. The prefix will get stripped out when printing metadata out so it will be effectively invisible.
            //TODO move subclasses that print out simple metadata to using addMetadata directly and support also numbers and booleans.
            //TODO rename metadataToXContent and have only SearchPhaseExecutionException use it, which prints out complex objects
            e.addMetadata("es." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            e.addHeader(header.getKey(), header.getValue());
        }

        // Adds root causes as suppressed exception. This way they are not lost
        // after parsing and can be retrieved using getSuppressed() method.
        for (ElasticsearchException rootCause : rootCauses) {
            e.addSuppressed(rootCause);
        }
        for (ElasticsearchException s : suppressed) {
            e.addSuppressed(s);
        }
        return e;
    }

    /**
     * Static toXContent helper method that renders {@link ElasticsearchException} or {@link Throwable} instances
     * as XContent, delegating the rendering to {@link #toXContent(XContentBuilder, Params)}
     * or {@link #innerToXContent(XContentBuilder, Params, Throwable, String, String, Map, Map, Throwable)}.
     *
     * This method is usually used when the {@link Throwable} is rendered as a part of another XContent object, and its result can
     * be parsed back using the {@link #fromXContent(XContentParser)} method.
     */
    public static void generateThrowableXContent(XContentBuilder builder, Params params, Throwable t) throws IOException {
        t = ExceptionsHelper.unwrapCause(t);

        if (t instanceof ElasticsearchException) {
            ((ElasticsearchException) t).toXContent(builder, params);
        } else {
            innerToXContent(builder, params, t, getExceptionName(t), t.getMessage(), emptyMap(), emptyMap(), t.getCause());
        }
    }

    /**
     * Render any exception as a xcontent, encapsulated within a field or object named "error". The level of details that are rendered
     * depends on the value of the "detailed" parameter: when it's false only a simple message based on the type and message of the
     * exception is rendered. When it's true all detail are provided including guesses root causes, cause and potentially stack
     * trace.
     *
     * This method is usually used when the {@link Exception} is rendered as a full XContent object, and its output can be parsed
     * by the {@link #failureFromXContent(XContentParser)} method.
     */
    public static void generateFailureXContent(XContentBuilder builder, Params params, @Nullable Exception e, boolean detailed)
            throws IOException {
        // No exception to render as an error
        if (e == null) {
            builder.field(ERROR, "unknown");
            return;
        }

        // Render the exception with a simple message
        if (detailed == false) {
            String message = "No ElasticsearchException found";
            Throwable t = e;
            for (int counter = 0; counter < 10 && t != null; counter++) {
                if (t instanceof ElasticsearchException) {
                    message = t.getClass().getSimpleName() + "[" + t.getMessage() + "]";
                    break;
                }
                t = t.getCause();
            }
            builder.field(ERROR, message);
            return;
        }

        // Render the exception with all details
        final ElasticsearchException[] rootCauses = ElasticsearchException.guessRootCauses(e);
        builder.startObject(ERROR);
        {
            builder.startArray(ROOT_CAUSE);
            for (ElasticsearchException rootCause : rootCauses) {
                builder.startObject();
                rootCause.toXContent(builder, new DelegatingMapParams(singletonMap(REST_EXCEPTION_SKIP_CAUSE, "true"), params));
                builder.endObject();
            }
            builder.endArray();
        }
        generateThrowableXContent(builder, params, e);
        builder.endObject();
    }

    /**
     * Parses the output of {@link #generateFailureXContent(XContentBuilder, Params, Exception, boolean)}
     */
    public static ElasticsearchException failureFromXContent(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.currentToken();
        ensureFieldName(parser, token, ERROR);

        token = parser.nextToken();
        if (token.isValue()) {
            return new ElasticsearchException(buildMessage("exception", parser.text(), null));
        }

        ensureExpectedToken(XContentParser.Token.START_OBJECT, token, parser::getTokenLocation);
        token = parser.nextToken();

        // Root causes are parsed in the innerFromXContent() and are added as suppressed exceptions.
        return innerFromXContent(parser, true);
    }

    /**
     * Returns the root cause of this exception or multiple if different shards caused different exceptions
     */
    public ElasticsearchException[] guessRootCauses() {
        final Throwable cause = getCause();
        if (cause != null && cause instanceof ElasticsearchException) {
            return ((ElasticsearchException) cause).guessRootCauses();
        }
        return new ElasticsearchException[]{this};
    }

    /**
     * Returns the root cause of this exception or multiple if different shards caused different exceptions.
     * If the given exception is not an instance of {@link ElasticsearchException} an empty array
     * is returned.
     */
    public static ElasticsearchException[] guessRootCauses(Throwable t) {
        Throwable ex = ExceptionsHelper.unwrapCause(t);
        if (ex instanceof ElasticsearchException) {
            // ElasticsearchException knows how to guess its own root cause
            return ((ElasticsearchException) ex).guessRootCauses();
        }
        if (ex instanceof XContentParseException) {
            /*
             * We'd like to unwrap parsing exceptions to the inner-most
             * parsing exception because that is generally the most interesting
             * exception to return to the user. If that exception is caused by
             * an ElasticsearchException we'd like to keep unwrapping because
             * ElasticserachExceptions tend to contain useful information for
             * the user.
             */
            Throwable cause = ex.getCause();
            if (cause != null) {
                if (cause instanceof XContentParseException || cause instanceof ElasticsearchException) {
                    return guessRootCauses(ex.getCause());
                }
            }
        }
        return new ElasticsearchException[]{new ElasticsearchException(t.getMessage(), t) {
            @Override
            protected String getExceptionName() {
                return getExceptionName(getCause());
            }
        }};
    }

    protected String getExceptionName() {
        return getExceptionName(this);
    }

    /**
     * Returns a underscore case name for the given exception. This method strips {@code Elasticsearch} prefixes from exception names.
     */
    public static String getExceptionName(Throwable ex) {
        String simpleName = ex.getClass().getSimpleName();
        if (simpleName.startsWith("Elasticsearch")) {
            simpleName = simpleName.substring("Elasticsearch".length());
        }
        // TODO: do we really need to make the exception name in underscore casing?
        return toUnderscoreCase(simpleName);
    }

    static String buildMessage(String type, String reason, String stack) {
        StringBuilder message = new StringBuilder("Elasticsearch exception [");
        message.append(TYPE).append('=').append(type).append(", ");
        message.append(REASON).append('=').append(reason);
        if (stack != null) {
            message.append(", ").append(STACK_TRACE).append('=').append(stack);
        }
        message.append(']');
        return message.toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (metadata.containsKey(INDEX_METADATA_KEY)) {
            builder.append(getIndex());
            if (metadata.containsKey(SHARD_METADATA_KEY)) {
                builder.append('[').append(getShardId()).append(']');
            }
            builder.append(' ');
        }
        return builder.append(ExceptionsHelper.detailedMessage(this).trim()).toString();
    }

    /**
     * Deserializes stacktrace elements as well as suppressed exceptions from the given output stream and
     * adds it to the given exception.
     */
    public static <T extends Throwable> T readStackTrace(T throwable, StreamInput in) throws IOException {
        final int stackTraceElements = in.readVInt();
        StackTraceElement[] stackTrace = new StackTraceElement[stackTraceElements];
        for (int i = 0; i < stackTraceElements; i++) {
            final String declaringClasss = in.readString();
            final String fileName = in.readOptionalString();
            final String methodName = in.readString();
            final int lineNumber = in.readVInt();
            stackTrace[i] = new StackTraceElement(declaringClasss, methodName, fileName, lineNumber);
        }
        throwable.setStackTrace(stackTrace);

        int numSuppressed = in.readVInt();
        for (int i = 0; i < numSuppressed; i++) {
            throwable.addSuppressed(in.readException());
        }
        return throwable;
    }

    /**
     * Serializes the given exceptions stacktrace elements as well as it's suppressed exceptions to the given output stream.
     */
    public static <T extends Throwable> T writeStackTraces(T throwable, StreamOutput out) throws IOException {
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        out.writeVInt(stackTrace.length);
        for (StackTraceElement element : stackTrace) {
            out.writeString(element.getClassName());
            out.writeOptionalString(element.getFileName());
            out.writeString(element.getMethodName());
            out.writeVInt(element.getLineNumber());
        }
        Throwable[] suppressed = throwable.getSuppressed();
        out.writeVInt(suppressed.length);
        for (Throwable t : suppressed) {
            out.writeException(t);
        }
        return throwable;
    }

    /**
     * This is the list of Exceptions Elasticsearch can throw over the wire or save into a corruption marker. Each value in the enum is a
     * single exception tying the Class to an id for use of the encode side and the id back to a constructor for use on the decode side. As
     * such its ok if the exceptions to change names so long as their constructor can still read the exception. Each exception is listed
     * in id order below. If you want to remove an exception leave a tombstone comment and mark the id as null in
     * ExceptionSerializationTests.testIds.ids.
     */
    private enum ElasticsearchExceptionHandle {
        INDEX_SHARD_SNAPSHOT_FAILED_EXCEPTION(io.crate.es.index.snapshots.IndexShardSnapshotFailedException.class,
                io.crate.es.index.snapshots.IndexShardSnapshotFailedException::new, 0, UNKNOWN_VERSION_ADDED),
        EXECUTION_CANCELLED_EXCEPTION(CancellableThreads.ExecutionCancelledException.class,
                CancellableThreads.ExecutionCancelledException::new, 2, UNKNOWN_VERSION_ADDED),
        MASTER_NOT_DISCOVERED_EXCEPTION(io.crate.es.discovery.MasterNotDiscoveredException.class,
                io.crate.es.discovery.MasterNotDiscoveredException::new, 3, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_SECURITY_EXCEPTION(ElasticsearchSecurityException.class,
                ElasticsearchSecurityException::new, 4, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RESTORE_EXCEPTION(io.crate.es.index.snapshots.IndexShardRestoreException.class,
                io.crate.es.index.snapshots.IndexShardRestoreException::new, 5, UNKNOWN_VERSION_ADDED),
        INDEX_CLOSED_EXCEPTION(io.crate.es.indices.IndexClosedException.class,
                io.crate.es.indices.IndexClosedException::new, 6, UNKNOWN_VERSION_ADDED),
        BIND_HTTP_EXCEPTION(io.crate.es.http.BindHttpException.class,
                io.crate.es.http.BindHttpException::new, 7, UNKNOWN_VERSION_ADDED),
        NODE_CLOSED_EXCEPTION(io.crate.es.node.NodeClosedException.class,
                io.crate.es.node.NodeClosedException::new, 9, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_FAILED_ENGINE_EXCEPTION(io.crate.es.index.engine.SnapshotFailedEngineException.class,
                io.crate.es.index.engine.SnapshotFailedEngineException::new, 10, UNKNOWN_VERSION_ADDED),
        SHARD_NOT_FOUND_EXCEPTION(io.crate.es.index.shard.ShardNotFoundException.class,
                io.crate.es.index.shard.ShardNotFoundException::new, 11, UNKNOWN_VERSION_ADDED),
        CONNECT_TRANSPORT_EXCEPTION(io.crate.es.transport.ConnectTransportException.class,
                io.crate.es.transport.ConnectTransportException::new, 12, UNKNOWN_VERSION_ADDED),
        NOT_SERIALIZABLE_TRANSPORT_EXCEPTION(io.crate.es.transport.NotSerializableTransportException.class,
                io.crate.es.transport.NotSerializableTransportException::new, 13, UNKNOWN_VERSION_ADDED),
        RESPONSE_HANDLER_FAILURE_TRANSPORT_EXCEPTION(io.crate.es.transport.ResponseHandlerFailureTransportException.class,
                io.crate.es.transport.ResponseHandlerFailureTransportException::new, 14, UNKNOWN_VERSION_ADDED),
        INDEX_CREATION_EXCEPTION(io.crate.es.indices.IndexCreationException.class,
                io.crate.es.indices.IndexCreationException::new, 15, UNKNOWN_VERSION_ADDED),
        INDEX_NOT_FOUND_EXCEPTION(io.crate.es.index.IndexNotFoundException.class,
                io.crate.es.index.IndexNotFoundException::new, 16, UNKNOWN_VERSION_ADDED),
        ILLEGAL_SHARD_ROUTING_STATE_EXCEPTION(io.crate.es.cluster.routing.IllegalShardRoutingStateException.class,
                io.crate.es.cluster.routing.IllegalShardRoutingStateException::new, 17, UNKNOWN_VERSION_ADDED),
        BROADCAST_SHARD_OPERATION_FAILED_EXCEPTION(io.crate.es.action.support.broadcast.BroadcastShardOperationFailedException.class,
                io.crate.es.action.support.broadcast.BroadcastShardOperationFailedException::new, 18, UNKNOWN_VERSION_ADDED),
        RESOURCE_NOT_FOUND_EXCEPTION(ResourceNotFoundException.class,
                ResourceNotFoundException::new, 19, UNKNOWN_VERSION_ADDED),
        ACTION_TRANSPORT_EXCEPTION(io.crate.es.transport.ActionTransportException.class,
                io.crate.es.transport.ActionTransportException::new, 20, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_GENERATION_EXCEPTION(ElasticsearchGenerationException.class,
                ElasticsearchGenerationException::new, 21, UNKNOWN_VERSION_ADDED),
        //      22 was CreateFailedEngineException
        INDEX_SHARD_STARTED_EXCEPTION(io.crate.es.index.shard.IndexShardStartedException.class,
                io.crate.es.index.shard.IndexShardStartedException::new, 23, UNKNOWN_VERSION_ADDED),
        // 25 was GeneralScriptException
        // 26 was BatchOperationException
        SNAPSHOT_CREATION_EXCEPTION(io.crate.es.snapshots.SnapshotCreationException.class,
                io.crate.es.snapshots.SnapshotCreationException::new, 27, UNKNOWN_VERSION_ADDED),
        DELETE_FAILED_ENGINE_EXCEPTION(io.crate.es.index.engine.DeleteFailedEngineException.class, // deprecated in 6.0, remove in 7.0
                io.crate.es.index.engine.DeleteFailedEngineException::new, 28, UNKNOWN_VERSION_ADDED),
        DOCUMENT_MISSING_EXCEPTION(io.crate.es.index.engine.DocumentMissingException.class,
                io.crate.es.index.engine.DocumentMissingException::new, 29, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_EXCEPTION(io.crate.es.snapshots.SnapshotException.class,
                io.crate.es.snapshots.SnapshotException::new, 30, UNKNOWN_VERSION_ADDED),
        INVALID_ALIAS_NAME_EXCEPTION(io.crate.es.indices.InvalidAliasNameException.class,
                io.crate.es.indices.InvalidAliasNameException::new, 31, UNKNOWN_VERSION_ADDED),
        INVALID_INDEX_NAME_EXCEPTION(io.crate.es.indices.InvalidIndexNameException.class,
                io.crate.es.indices.InvalidIndexNameException::new, 32, UNKNOWN_VERSION_ADDED),
        INDEX_PRIMARY_SHARD_NOT_ALLOCATED_EXCEPTION(io.crate.es.indices.IndexPrimaryShardNotAllocatedException.class,
                io.crate.es.indices.IndexPrimaryShardNotAllocatedException::new, 33, UNKNOWN_VERSION_ADDED),
        TRANSPORT_EXCEPTION(io.crate.es.transport.TransportException.class,
                io.crate.es.transport.TransportException::new, 34, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_PARSE_EXCEPTION(ElasticsearchParseException.class,
                ElasticsearchParseException::new, 35, UNKNOWN_VERSION_ADDED),
        MAPPER_EXCEPTION(io.crate.es.index.mapper.MapperException.class,
                io.crate.es.index.mapper.MapperException::new, 37, UNKNOWN_VERSION_ADDED),
        INVALID_TYPE_NAME_EXCEPTION(io.crate.es.indices.InvalidTypeNameException.class,
                io.crate.es.indices.InvalidTypeNameException::new, 38, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_RESTORE_EXCEPTION(io.crate.es.snapshots.SnapshotRestoreException.class,
                io.crate.es.snapshots.SnapshotRestoreException::new, 39, UNKNOWN_VERSION_ADDED),
        PARSING_EXCEPTION(ParsingException.class, ParsingException::new, 40,
            UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_CLOSED_EXCEPTION(io.crate.es.index.shard.IndexShardClosedException.class,
                io.crate.es.index.shard.IndexShardClosedException::new, 41, UNKNOWN_VERSION_ADDED),
        RECOVER_FILES_RECOVERY_EXCEPTION(io.crate.es.indices.recovery.RecoverFilesRecoveryException.class,
                io.crate.es.indices.recovery.RecoverFilesRecoveryException::new, 42, UNKNOWN_VERSION_ADDED),
        TRUNCATED_TRANSLOG_EXCEPTION(io.crate.es.index.translog.TruncatedTranslogException.class,
                io.crate.es.index.translog.TruncatedTranslogException::new, 43, UNKNOWN_VERSION_ADDED),
        RECOVERY_FAILED_EXCEPTION(io.crate.es.indices.recovery.RecoveryFailedException.class,
                io.crate.es.indices.recovery.RecoveryFailedException::new, 44, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RELOCATED_EXCEPTION(io.crate.es.index.shard.IndexShardRelocatedException.class,
                io.crate.es.index.shard.IndexShardRelocatedException::new, 45, UNKNOWN_VERSION_ADDED),
        NODE_SHOULD_NOT_CONNECT_EXCEPTION(io.crate.es.transport.NodeShouldNotConnectException.class,
                io.crate.es.transport.NodeShouldNotConnectException::new, 46, UNKNOWN_VERSION_ADDED),
        // 47 used to be for IndexTemplateAlreadyExistsException which was deprecated in 5.1 removed in 6.0
        TRANSLOG_CORRUPTED_EXCEPTION(io.crate.es.index.translog.TranslogCorruptedException.class,
                io.crate.es.index.translog.TranslogCorruptedException::new, 48, UNKNOWN_VERSION_ADDED),
        CLUSTER_BLOCK_EXCEPTION(io.crate.es.cluster.block.ClusterBlockException.class,
                io.crate.es.cluster.block.ClusterBlockException::new, 49, UNKNOWN_VERSION_ADDED),
        // 51 used to be for IndexShardAlreadyExistsException which was deprecated in 5.1 removed in 6.0
        VERSION_CONFLICT_ENGINE_EXCEPTION(io.crate.es.index.engine.VersionConflictEngineException.class,
                io.crate.es.index.engine.VersionConflictEngineException::new, 52, UNKNOWN_VERSION_ADDED),
        ENGINE_EXCEPTION(io.crate.es.index.engine.EngineException.class, io.crate.es.index.engine.EngineException::new, 53,
            UNKNOWN_VERSION_ADDED),
        // 54 was DocumentAlreadyExistsException, which is superseded by VersionConflictEngineException
        NO_SUCH_NODE_EXCEPTION(io.crate.es.action.NoSuchNodeException.class, io.crate.es.action.NoSuchNodeException::new, 55,
            UNKNOWN_VERSION_ADDED),
        SETTINGS_EXCEPTION(SettingsException.class,
                SettingsException::new, 56, UNKNOWN_VERSION_ADDED),
        INDEX_TEMPLATE_MISSING_EXCEPTION(io.crate.es.indices.IndexTemplateMissingException.class,
                io.crate.es.indices.IndexTemplateMissingException::new, 57, UNKNOWN_VERSION_ADDED),
        SEND_REQUEST_TRANSPORT_EXCEPTION(io.crate.es.transport.SendRequestTransportException.class,
                io.crate.es.transport.SendRequestTransportException::new, 58, UNKNOWN_VERSION_ADDED),
        // 59 used to be EsRejectedExecutionException
        // 60 used to be for EarlyTerminationException
        // 61 used to be for RoutingValidationException
        NOT_SERIALIZABLE_EXCEPTION_WRAPPER(NotSerializableExceptionWrapper.class,
                NotSerializableExceptionWrapper::new, 62, UNKNOWN_VERSION_ADDED),
        // 63 was AliasFilterParsingException
        // 64 was DeleteByQueryFailedEngineException, which was removed in 5.0
        GATEWAY_EXCEPTION(io.crate.es.gateway.GatewayException.class, io.crate.es.gateway.GatewayException::new, 65,
            UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_NOT_RECOVERING_EXCEPTION(io.crate.es.index.shard.IndexShardNotRecoveringException.class,
                io.crate.es.index.shard.IndexShardNotRecoveringException::new, 66, UNKNOWN_VERSION_ADDED),
        HTTP_EXCEPTION(io.crate.es.http.HttpException.class, io.crate.es.http.HttpException::new, 67, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_EXCEPTION(ElasticsearchException.class,
                ElasticsearchException::new, 68, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_MISSING_EXCEPTION(io.crate.es.snapshots.SnapshotMissingException.class,
                io.crate.es.snapshots.SnapshotMissingException::new, 69, UNKNOWN_VERSION_ADDED),
        PRIMARY_MISSING_ACTION_EXCEPTION(io.crate.es.action.PrimaryMissingActionException.class,
                io.crate.es.action.PrimaryMissingActionException::new, 70, UNKNOWN_VERSION_ADDED),
        FAILED_NODE_EXCEPTION(io.crate.es.action.FailedNodeException.class, io.crate.es.action.FailedNodeException::new, 71,
            UNKNOWN_VERSION_ADDED),
        CONCURRENT_SNAPSHOT_EXECUTION_EXCEPTION(io.crate.es.snapshots.ConcurrentSnapshotExecutionException.class,
                io.crate.es.snapshots.ConcurrentSnapshotExecutionException::new, 73, UNKNOWN_VERSION_ADDED),
        BLOB_STORE_EXCEPTION(BlobStoreException.class,
                BlobStoreException::new, 74, UNKNOWN_VERSION_ADDED),
        INCOMPATIBLE_CLUSTER_STATE_VERSION_EXCEPTION(io.crate.es.cluster.IncompatibleClusterStateVersionException.class,
                io.crate.es.cluster.IncompatibleClusterStateVersionException::new, 75, UNKNOWN_VERSION_ADDED),
        RECOVERY_ENGINE_EXCEPTION(io.crate.es.index.engine.RecoveryEngineException.class,
                io.crate.es.index.engine.RecoveryEngineException::new, 76, UNKNOWN_VERSION_ADDED),
        UNCATEGORIZED_EXECUTION_EXCEPTION(UncategorizedExecutionException.class,
                UncategorizedExecutionException::new, 77, UNKNOWN_VERSION_ADDED),
        INDEX_FAILED_ENGINE_EXCEPTION(io.crate.es.index.engine.IndexFailedEngineException.class, // deprecated in 6.0, remove in 7.0
                io.crate.es.index.engine.IndexFailedEngineException::new, 80, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RESTORE_FAILED_EXCEPTION(io.crate.es.index.snapshots.IndexShardRestoreFailedException.class,
                io.crate.es.index.snapshots.IndexShardRestoreFailedException::new, 81, UNKNOWN_VERSION_ADDED),
        REPOSITORY_EXCEPTION(io.crate.es.repositories.RepositoryException.class,
                io.crate.es.repositories.RepositoryException::new, 82, UNKNOWN_VERSION_ADDED),
        RECEIVE_TIMEOUT_TRANSPORT_EXCEPTION(io.crate.es.transport.ReceiveTimeoutTransportException.class,
                io.crate.es.transport.ReceiveTimeoutTransportException::new, 83, UNKNOWN_VERSION_ADDED),
        NODE_DISCONNECTED_EXCEPTION(io.crate.es.transport.NodeDisconnectedException.class,
                io.crate.es.transport.NodeDisconnectedException::new, 84, UNKNOWN_VERSION_ADDED),
        // 85 used to be for AlreadyExpiredException
        // 87 used to be for MergeMappingException
        INVALID_INDEX_TEMPLATE_EXCEPTION(io.crate.es.indices.InvalidIndexTemplateException.class,
                io.crate.es.indices.InvalidIndexTemplateException::new, 88, UNKNOWN_VERSION_ADDED),
        REFRESH_FAILED_ENGINE_EXCEPTION(io.crate.es.index.engine.RefreshFailedEngineException.class,
                io.crate.es.index.engine.RefreshFailedEngineException::new, 90, UNKNOWN_VERSION_ADDED),
        // 91 used to be for AggregationInitializationException
        DELAY_RECOVERY_EXCEPTION(io.crate.es.indices.recovery.DelayRecoveryException.class,
                io.crate.es.indices.recovery.DelayRecoveryException::new, 92, UNKNOWN_VERSION_ADDED),
        // 93 used to be for IndexWarmerMissingException
        NO_NODE_AVAILABLE_EXCEPTION(io.crate.es.client.transport.NoNodeAvailableException.class,
                io.crate.es.client.transport.NoNodeAvailableException::new, 94, UNKNOWN_VERSION_ADDED),
        INVALID_SNAPSHOT_NAME_EXCEPTION(io.crate.es.snapshots.InvalidSnapshotNameException.class,
                io.crate.es.snapshots.InvalidSnapshotNameException::new, 96, UNKNOWN_VERSION_ADDED),
        ILLEGAL_INDEX_SHARD_STATE_EXCEPTION(io.crate.es.index.shard.IllegalIndexShardStateException.class,
                io.crate.es.index.shard.IllegalIndexShardStateException::new, 97, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_SNAPSHOT_EXCEPTION(io.crate.es.index.snapshots.IndexShardSnapshotException.class,
                io.crate.es.index.snapshots.IndexShardSnapshotException::new, 98, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_NOT_STARTED_EXCEPTION(io.crate.es.index.shard.IndexShardNotStartedException.class,
                io.crate.es.index.shard.IndexShardNotStartedException::new, 99, UNKNOWN_VERSION_ADDED),
        ACTION_NOT_FOUND_TRANSPORT_EXCEPTION(io.crate.es.transport.ActionNotFoundTransportException.class,
                io.crate.es.transport.ActionNotFoundTransportException::new, 101, UNKNOWN_VERSION_ADDED),
        TRANSPORT_SERIALIZATION_EXCEPTION(io.crate.es.transport.TransportSerializationException.class,
                io.crate.es.transport.TransportSerializationException::new, 102, UNKNOWN_VERSION_ADDED),
        REMOTE_TRANSPORT_EXCEPTION(io.crate.es.transport.RemoteTransportException.class,
                io.crate.es.transport.RemoteTransportException::new, 103, UNKNOWN_VERSION_ADDED),
        ENGINE_CREATION_FAILURE_EXCEPTION(io.crate.es.index.engine.EngineCreationFailureException.class,
                io.crate.es.index.engine.EngineCreationFailureException::new, 104, UNKNOWN_VERSION_ADDED),
        ROUTING_EXCEPTION(io.crate.es.cluster.routing.RoutingException.class,
                io.crate.es.cluster.routing.RoutingException::new, 105, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RECOVERY_EXCEPTION(io.crate.es.index.shard.IndexShardRecoveryException.class,
                io.crate.es.index.shard.IndexShardRecoveryException::new, 106, UNKNOWN_VERSION_ADDED),
        REPOSITORY_MISSING_EXCEPTION(io.crate.es.repositories.RepositoryMissingException.class,
                io.crate.es.repositories.RepositoryMissingException::new, 107, UNKNOWN_VERSION_ADDED),
        DOCUMENT_SOURCE_MISSING_EXCEPTION(io.crate.es.index.engine.DocumentSourceMissingException.class,
                io.crate.es.index.engine.DocumentSourceMissingException::new, 109, UNKNOWN_VERSION_ADDED),
        // 110 used to be FlushNotAllowedEngineException
        NO_CLASS_SETTINGS_EXCEPTION(NoClassSettingsException.class,
                NoClassSettingsException::new, 111, UNKNOWN_VERSION_ADDED),
        BIND_TRANSPORT_EXCEPTION(io.crate.es.transport.BindTransportException.class,
                io.crate.es.transport.BindTransportException::new, 112, UNKNOWN_VERSION_ADDED),
        ALIASES_NOT_FOUND_EXCEPTION(AliasesNotFoundException.class,
                AliasesNotFoundException::new, 113, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RECOVERING_EXCEPTION(io.crate.es.index.shard.IndexShardRecoveringException.class,
                io.crate.es.index.shard.IndexShardRecoveringException::new, 114, UNKNOWN_VERSION_ADDED),
        TRANSLOG_EXCEPTION(io.crate.es.index.translog.TranslogException.class,
                io.crate.es.index.translog.TranslogException::new, 115, UNKNOWN_VERSION_ADDED),
        PROCESS_CLUSTER_EVENT_TIMEOUT_EXCEPTION(io.crate.es.cluster.metadata.ProcessClusterEventTimeoutException.class,
                io.crate.es.cluster.metadata.ProcessClusterEventTimeoutException::new, 116, UNKNOWN_VERSION_ADDED),
        RETRY_ON_PRIMARY_EXCEPTION(ReplicationOperation.RetryOnPrimaryException.class,
                ReplicationOperation.RetryOnPrimaryException::new, 117, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_TIMEOUT_EXCEPTION(ElasticsearchTimeoutException.class,
                ElasticsearchTimeoutException::new, 118, UNKNOWN_VERSION_ADDED),
        REPOSITORY_VERIFICATION_EXCEPTION(io.crate.es.repositories.RepositoryVerificationException.class,
                io.crate.es.repositories.RepositoryVerificationException::new, 120, UNKNOWN_VERSION_ADDED),
        // 123 used to be IndexAlreadyExistsException and was renamed
        RESOURCE_ALREADY_EXISTS_EXCEPTION(ResourceAlreadyExistsException.class,
            ResourceAlreadyExistsException::new, 123, UNKNOWN_VERSION_ADDED),
        // 124 used to be Script.ScriptParseException
        HTTP_ON_TRANSPORT_EXCEPTION(TcpTransport.HttpOnTransportException.class,
                TcpTransport.HttpOnTransportException::new, 125, UNKNOWN_VERSION_ADDED),
        MAPPER_PARSING_EXCEPTION(io.crate.es.index.mapper.MapperParsingException.class,
                io.crate.es.index.mapper.MapperParsingException::new, 126, UNKNOWN_VERSION_ADDED),
        // 129 was EngineClosedException
        NO_SHARD_AVAILABLE_ACTION_EXCEPTION(io.crate.es.action.NoShardAvailableActionException.class,
                io.crate.es.action.NoShardAvailableActionException::new, 130, UNKNOWN_VERSION_ADDED),
        UNAVAILABLE_SHARDS_EXCEPTION(io.crate.es.action.UnavailableShardsException.class,
                io.crate.es.action.UnavailableShardsException::new, 131, UNKNOWN_VERSION_ADDED),
        FLUSH_FAILED_ENGINE_EXCEPTION(io.crate.es.index.engine.FlushFailedEngineException.class,
                io.crate.es.index.engine.FlushFailedEngineException::new, 132, UNKNOWN_VERSION_ADDED),
        CIRCUIT_BREAKING_EXCEPTION(CircuitBreakingException.class,
                CircuitBreakingException::new, 133, UNKNOWN_VERSION_ADDED),
        NODE_NOT_CONNECTED_EXCEPTION(io.crate.es.transport.NodeNotConnectedException.class,
                io.crate.es.transport.NodeNotConnectedException::new, 134, UNKNOWN_VERSION_ADDED),
        STRICT_DYNAMIC_MAPPING_EXCEPTION(io.crate.es.index.mapper.StrictDynamicMappingException.class,
                io.crate.es.index.mapper.StrictDynamicMappingException::new, 135, UNKNOWN_VERSION_ADDED),
        RETRY_ON_REPLICA_EXCEPTION(io.crate.es.action.support.replication.TransportReplicationAction.RetryOnReplicaException.class,
                io.crate.es.action.support.replication.TransportReplicationAction.RetryOnReplicaException::new, 136,
            UNKNOWN_VERSION_ADDED),
        TYPE_MISSING_EXCEPTION(io.crate.es.indices.TypeMissingException.class,
                io.crate.es.indices.TypeMissingException::new, 137, UNKNOWN_VERSION_ADDED),
        FAILED_TO_COMMIT_CLUSTER_STATE_EXCEPTION(io.crate.es.discovery.Discovery.FailedToCommitClusterStateException.class,
                io.crate.es.discovery.Discovery.FailedToCommitClusterStateException::new, 140, UNKNOWN_VERSION_ADDED),
        QUERY_SHARD_EXCEPTION(io.crate.es.index.query.QueryShardException.class,
                io.crate.es.index.query.QueryShardException::new, 141, UNKNOWN_VERSION_ADDED),
        NO_LONGER_PRIMARY_SHARD_EXCEPTION(ShardStateAction.NoLongerPrimaryShardException.class,
                ShardStateAction.NoLongerPrimaryShardException::new, 142, UNKNOWN_VERSION_ADDED),
        // 143 was ScriptException
        NOT_MASTER_EXCEPTION(io.crate.es.cluster.NotMasterException.class, io.crate.es.cluster.NotMasterException::new, 144,
            UNKNOWN_VERSION_ADDED),
        STATUS_EXCEPTION(ElasticsearchStatusException.class, ElasticsearchStatusException::new, 145,
            UNKNOWN_VERSION_ADDED),
        TASK_CANCELLED_EXCEPTION(io.crate.es.tasks.TaskCancelledException.class,
            io.crate.es.tasks.TaskCancelledException::new, 146, Version.ES_V_6_1_4),
        SHARD_LOCK_OBTAIN_FAILED_EXCEPTION(io.crate.es.env.ShardLockObtainFailedException.class,
                                           io.crate.es.env.ShardLockObtainFailedException::new, 147, Version.ES_V_6_1_4);
        // 148 used to be UnknownNamedObjectException
        // 149 used to be MultiBucketConsumerService.TooManyBucketsException

        final Class<? extends ElasticsearchException> exceptionClass;
        final CheckedFunction<StreamInput, ? extends ElasticsearchException, IOException> constructor;
        final int id;
        final Version versionAdded;

        <E extends ElasticsearchException> ElasticsearchExceptionHandle(Class<E> exceptionClass,
                                                                        CheckedFunction<StreamInput, E, IOException> constructor, int id,
                                                                        Version versionAdded) {
            // We need the exceptionClass because you can't dig it out of the constructor reliably.
            this.exceptionClass = exceptionClass;
            this.constructor = constructor;
            this.versionAdded = versionAdded;
            this.id = id;
        }
    }

    static {
        ID_TO_SUPPLIER = unmodifiableMap(Arrays
                .stream(ElasticsearchExceptionHandle.values()).collect(Collectors.toMap(e -> e.id, e -> e.constructor)));
        CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE = unmodifiableMap(Arrays
                .stream(ElasticsearchExceptionHandle.values()).collect(Collectors.toMap(e -> e.exceptionClass, e -> e)));
    }

    public Index getIndex() {
        List<String> index = getMetadata(INDEX_METADATA_KEY);
        if (index != null && index.isEmpty() == false) {
            List<String> index_uuid = getMetadata(INDEX_METADATA_KEY_UUID);
            return new Index(index.get(0), index_uuid.get(0));
        }

        return null;
    }

    public ShardId getShardId() {
        List<String> shard = getMetadata(SHARD_METADATA_KEY);
        if (shard != null && shard.isEmpty() == false) {
            return new ShardId(getIndex(), Integer.parseInt(shard.get(0)));
        }
        return null;
    }

    public void setIndex(Index index) {
        if (index != null) {
            addMetadata(INDEX_METADATA_KEY, index.getName());
            addMetadata(INDEX_METADATA_KEY_UUID, index.getUUID());
        }
    }

    public void setIndex(String index) {
        if (index != null) {
            setIndex(new Index(index, INDEX_UUID_NA_VALUE));
        }
    }

    public void setShard(ShardId shardId) {
        if (shardId != null) {
            setIndex(shardId.getIndex());
            addMetadata(SHARD_METADATA_KEY, Integer.toString(shardId.id()));
        }
    }

    public void setResources(String type, String... id) {
        assert type != null;
        addMetadata(RESOURCE_METADATA_ID_KEY, id);
        addMetadata(RESOURCE_METADATA_TYPE_KEY, type);
    }

    // lower cases and adds underscores to transitions in a name
    private static String toUnderscoreCase(String value) {
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!changed) {
                    // copy it over here
                    for (int j = 0; j < i; j++) {
                        sb.append(value.charAt(j));
                    }
                    changed = true;
                    if (i == 0) {
                        sb.append(Character.toLowerCase(c));
                    } else {
                        sb.append('_');
                        sb.append(Character.toLowerCase(c));
                    }
                } else {
                    sb.append('_');
                    sb.append(Character.toLowerCase(c));
                }
            } else {
                if (changed) {
                    sb.append(c);
                }
            }
        }
        if (!changed) {
            return value;
        }
        return sb.toString();
    }

}
