/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.COMMIT_RPC_CONTENT;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.EDIT_CONTENT_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COMMIT_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DEFAULT_OPERATION_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_ERROR_OPTION_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_LOCK_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_LOCK_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_SOURCE_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_TARGET_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_UNLOCK_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_UNLOCK_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_VALIDATE_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_VALIDATE_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.ROLLBACK_ON_ERROR_OPTION;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toFilterStructure;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Locale;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade.KeepaliveDOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.sal.SchemalessNetconfDeviceRpc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.config.input.source.ConfigSource;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;

/**
 * Provides base operations for netconf e.g. get, get-config, edit-config, (un)lock, commit etc.
 * According to RFC-6241
 */
public final class NetconfBaseOps {
    private static final NodeIdentifier CONFIG_SOURCE_NODEID = NodeIdentifier.create(ConfigSource.QNAME);
    private static final NodeIdentifier CONFIG_TARGET_NODEID = NodeIdentifier.create(ConfigTarget.QNAME);

    private final DOMRpcService rpc;
    private final MountPointContext mountContext;
    private final RpcStructureTransformer transformer;

    public NetconfBaseOps(final DOMRpcService rpc, final MountPointContext mountContext) {
        this.rpc = rpc;
        this.mountContext = mountContext;

        if (rpc instanceof KeepaliveDOMRpcService
                && ((KeepaliveDOMRpcService) rpc).getDeviceRpc() instanceof SchemalessNetconfDeviceRpc) {
            this.transformer = new SchemalessRpcStructureTransformer();
        } else {
            this.transformer = new NetconfRpcStructureTransformer(mountContext);
        }
    }

    public ListenableFuture<? extends DOMRpcResult> lock(final FutureCallback<DOMRpcResult> callback,
            final QName datastore) {
        requireNonNull(callback);
        requireNonNull(datastore);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_LOCK_PATH,
                getLockContent(datastore));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> lockCandidate(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_LOCK_PATH,
            getLockContent(NETCONF_CANDIDATE_QNAME));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> lockRunning(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_LOCK_PATH,
            getLockContent(NETCONF_RUNNING_QNAME));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> unlock(final FutureCallback<DOMRpcResult> callback,
            final QName datastore) {
        requireNonNull(callback);
        requireNonNull(datastore);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_UNLOCK_PATH,
                getUnLockContent(datastore));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> unlockRunning(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_UNLOCK_PATH,
            getUnLockContent(NETCONF_RUNNING_QNAME));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> unlockCandidate(final FutureCallback<DOMRpcResult> callback) {
        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_UNLOCK_PATH,
            getUnLockContent(NETCONF_CANDIDATE_QNAME));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> discardChanges(final FutureCallback<DOMRpcResult> callback) {
        requireNonNull(callback);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_DISCARD_CHANGES_PATH, null);
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> commit(final FutureCallback<DOMRpcResult> callback) {
        requireNonNull(callback);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_COMMIT_PATH, COMMIT_RPC_CONTENT);
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> validate(final FutureCallback<DOMRpcResult> callback,
            final QName datastore) {
        requireNonNull(callback);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_VALIDATE_PATH,
            getValidateContent(requireNonNull(datastore)));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> validateCandidate(final FutureCallback<DOMRpcResult> callback) {
        return validate(callback, NETCONF_CANDIDATE_QNAME);
    }

    public ListenableFuture<? extends DOMRpcResult> validateRunning(final FutureCallback<DOMRpcResult> callback) {
        return validate(callback, NETCONF_RUNNING_QNAME);
    }

    public ListenableFuture<? extends DOMRpcResult> copyConfig(final FutureCallback<DOMRpcResult> callback,
                                                               final QName source, final QName target) {
        requireNonNull(callback);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_COPY_CONFIG_PATH,
            getCopyConfigContent(requireNonNull(source), requireNonNull(target)));
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<? extends DOMRpcResult> copyRunningToCandidate(
            final FutureCallback<DOMRpcResult> callback) {
        return copyConfig(callback, NETCONF_RUNNING_QNAME, NETCONF_CANDIDATE_QNAME);
    }

    public ListenableFuture<? extends DOMRpcResult> getConfig(final FutureCallback<DOMRpcResult> callback,
                                                              final QName datastore,
                                                              final Optional<YangInstanceIdentifier> filterPath) {
        requireNonNull(callback);
        requireNonNull(datastore);

        final ListenableFuture<? extends DOMRpcResult> future;
        if (isFilterPresent(filterPath)) {
            final DataContainerChild<?, ?> node = transformer.toFilterStructure(filterPath.get());
            future = rpc.invokeRpc(NETCONF_GET_CONFIG_PATH,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, getSourceNode(datastore), node));
        } else {
            future = rpc.invokeRpc(NETCONF_GET_CONFIG_PATH,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, getSourceNode(datastore)));
        }

        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfigRunningData(
            final FutureCallback<DOMRpcResult> callback, final Optional<YangInstanceIdentifier> filterPath) {
        return extractData(filterPath, getConfigRunning(callback, filterPath));
    }

    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getData(final FutureCallback<DOMRpcResult> callback,
                                                                    final Optional<YangInstanceIdentifier> filterPath) {
        return extractData(filterPath, get(callback, filterPath));
    }

    private ListenableFuture<Optional<NormalizedNode<?, ?>>> extractData(
            final Optional<YangInstanceIdentifier> path, final ListenableFuture<? extends DOMRpcResult> configRunning) {
        return Futures.transform(configRunning, result -> {
            checkArgument(result.getErrors().isEmpty(), "Unable to read data: %s, errors: %s", path,
                result.getErrors());
            final DataContainerChild<?, ?> dataNode = ((ContainerNode) result.getResult())
                    .getChild(NetconfMessageTransformUtil.NETCONF_DATA_NODEID).get();
            return transformer.selectFromDataStructure(dataNode, path.get());
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<? extends DOMRpcResult> getConfigRunning(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return getConfig(callback, NETCONF_RUNNING_QNAME, filterPath);
    }

    public ListenableFuture<? extends DOMRpcResult> getConfigCandidate(final FutureCallback<DOMRpcResult> callback,
            final Optional<YangInstanceIdentifier> filterPath) {
        return getConfig(callback, NETCONF_CANDIDATE_QNAME, filterPath);
    }

    public ListenableFuture<? extends DOMRpcResult> get(final FutureCallback<DOMRpcResult> callback,
                                                        final Optional<YangInstanceIdentifier> filterPath) {
        requireNonNull(callback);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_GET_PATH,
                isFilterPresent(filterPath)
                    ? NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID,
                        toFilterStructure(filterPath.get(), mountContext.getSchemaContext()))
                    : NetconfMessageTransformUtil.GET_RPC_CONTENT);
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    private static boolean isFilterPresent(final Optional<YangInstanceIdentifier> filterPath) {
        return filterPath.isPresent() && !filterPath.get().isEmpty();
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigCandidate(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure,
            final ModifyAction modifyAction, final boolean rollback) {
        return editConfig(callback, NETCONF_CANDIDATE_QNAME, editStructure, Optional.of(modifyAction), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigCandidate(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure,
            final boolean rollback) {
        return editConfig(callback, NETCONF_CANDIDATE_QNAME, editStructure, Optional.empty(), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigRunning(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure,
            final ModifyAction modifyAction, final boolean rollback) {
        return editConfig(callback, NETCONF_RUNNING_QNAME, editStructure, Optional.of(modifyAction), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfigRunning(
            final FutureCallback<? super DOMRpcResult> callback, final DataContainerChild<?, ?> editStructure,
            final boolean rollback) {
        return editConfig(callback, NETCONF_RUNNING_QNAME, editStructure, Optional.empty(), rollback);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(
            final FutureCallback<? super DOMRpcResult> callback, final QName datastore,
            final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> modifyAction,
            final boolean rollback) {
        requireNonNull(callback);

        final ListenableFuture<? extends DOMRpcResult> future = rpc.invokeRpc(NETCONF_EDIT_CONFIG_PATH,
                getEditConfigContent(requireNonNull(datastore), requireNonNull(editStructure), modifyAction, rollback));

        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    public ChoiceNode createEditConfigStrcture(final Optional<NormalizedNode<?, ?>> lastChild,
                                               final Optional<ModifyAction> operation,
                                               final YangInstanceIdentifier dataPath) {
        return Builders.choiceBuilder()
                .withNodeIdentifier(EDIT_CONTENT_NODEID)
                .withChild(transformer.createEditConfigStructure(lastChild, dataPath, operation))
                .build();
    }

    private static ContainerNode getEditConfigContent(
            final QName datastore, final DataContainerChild<?, ?> editStructure,
            final Optional<ModifyAction> defaultOperation, final boolean rollback) {
        final DataContainerNodeBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> editBuilder =
                Builders.containerBuilder().withNodeIdentifier(NETCONF_EDIT_CONFIG_NODEID);

        // Target
        editBuilder.withChild(getTargetNode(datastore));

        // Default operation
        if (defaultOperation.isPresent()) {
            final String opString = defaultOperation.get().name().toLowerCase(Locale.ROOT);
            editBuilder.withChild(Builders.leafBuilder().withNodeIdentifier(NETCONF_DEFAULT_OPERATION_NODEID)
                    .withValue(opString).build());
        }

        // Error option
        if (rollback) {
            editBuilder.withChild(Builders.leafBuilder().withNodeIdentifier(NETCONF_ERROR_OPTION_NODEID)
                    .withValue(ROLLBACK_ON_ERROR_OPTION).build());
        }

        // Edit content
        editBuilder.withChild(editStructure);
        return editBuilder.build();
    }

    public static ContainerNode getSourceNode(final QName datastore) {
        return Builders.containerBuilder()
                .withNodeIdentifier(NETCONF_SOURCE_NODEID)
                .withChild(Builders.choiceBuilder()
                    .withNodeIdentifier(CONFIG_SOURCE_NODEID)
                    .withChild(ImmutableNodes.leafNode(datastore, Empty.getInstance()))
                    .build())
                .build();
    }

    public static ContainerNode getLockContent(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NETCONF_LOCK_NODEID)
                .withChild(getTargetNode(datastore)).build();
    }

    public static ContainerNode getTargetNode(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NETCONF_TARGET_NODEID)
                .withChild(Builders.choiceBuilder().withNodeIdentifier(CONFIG_TARGET_NODEID).withChild(
                    Builders.leafBuilder().withNodeIdentifier(toId(datastore)).withValue(Empty.getInstance()).build())
                    .build()).build();
    }

    public static ContainerNode getCopyConfigContent(final QName source, final QName target) {
        return Builders.containerBuilder().withNodeIdentifier(NETCONF_COPY_CONFIG_NODEID)
                .withChild(getTargetNode(target)).withChild(getSourceNode(source)).build();
    }

    public static ContainerNode getValidateContent(final QName source) {
        return Builders.containerBuilder().withNodeIdentifier(NETCONF_VALIDATE_NODEID)
                .withChild(getSourceNode(source)).build();
    }

    public static ContainerNode getUnLockContent(final QName datastore) {
        return Builders.containerBuilder().withNodeIdentifier(NETCONF_UNLOCK_NODEID)
                .withChild(getTargetNode(datastore)).build();
    }

}
