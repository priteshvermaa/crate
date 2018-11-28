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

package io.crate.execution.ddl.index;

import io.crate.execution.ddl.AbstractDDLTransportAction;
import io.crate.metadata.cluster.ExchangeIndexNameClusterStateExecutor;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateTaskExecutor;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

@Singleton
public class TransportExchangeIndexNameAction extends AbstractDDLTransportAction<ExchangeIndexNameRequest, ExchangeIndexNameResponse> {

    private static final String ACTION_NAME = "internal:crate:sql/index/exchange_name";
    private static final IndicesOptions STRICT_INDICES_OPTIONS = IndicesOptions.fromOptions(false, false, false, false);

    private final ExchangeIndexNameClusterStateExecutor executor;

    @Inject
    public TransportExchangeIndexNameAction(Settings settings,
                                            TransportService transportService,
                                            ClusterService clusterService,
                                            ThreadPool threadPool,
                                            ActionFilters actionFilters,
                                            IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, ACTION_NAME, transportService, clusterService, threadPool, actionFilters,
            indexNameExpressionResolver, ExchangeIndexNameRequest::new, ExchangeIndexNameResponse::new, ExchangeIndexNameResponse::new,
            "exchange-index-name");
        executor = new ExchangeIndexNameClusterStateExecutor(settings, indexNameExpressionResolver);
    }

    @Override
    public ClusterStateTaskExecutor<ExchangeIndexNameRequest> clusterStateTaskExecutor(ExchangeIndexNameRequest request) {
        return executor;
    }

    @Override
    protected ClusterBlockException checkBlock(ExchangeIndexNameRequest request, ClusterState state) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_WRITE,
            indexNameExpressionResolver.concreteIndexNames(state, STRICT_INDICES_OPTIONS,
                request.sourceIndexName(),
                request.targetIndexName()));
    }
}
