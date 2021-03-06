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

package io.crate.execution.engine;

import io.crate.data.BatchIterator;
import io.crate.data.Row;
import io.crate.data.RowConsumer;
import io.crate.exceptions.SQLExceptions;
import io.crate.execution.jobs.kill.KillJobsRequest;
import io.crate.execution.jobs.kill.TransportKillJobsNodeAction;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.logging.Loggers;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class InterceptingRowConsumer implements RowConsumer {

    private static final Logger LOGGER = Loggers.getLogger(InterceptingRowConsumer.class);

    private final AtomicInteger consumerInvokedAndJobInitialized = new AtomicInteger(2);
    private final UUID jobId;
    private final RowConsumer consumer;
    private final Executor executor;
    private final TransportKillJobsNodeAction transportKillJobsNodeAction;
    private final AtomicBoolean consumerAccepted = new AtomicBoolean(false);

    private Throwable failure = null;
    private BatchIterator<Row> iterator = null;

    InterceptingRowConsumer(UUID jobId,
                            RowConsumer consumer,
                            InitializationTracker jobsInitialized,
                            Executor executor,
                            TransportKillJobsNodeAction transportKillJobsNodeAction) {
        this.jobId = jobId;
        this.consumer = consumer;
        this.executor = executor;
        this.transportKillJobsNodeAction = transportKillJobsNodeAction;
        jobsInitialized.future.whenComplete((o, f) -> tryForwardResult(f));
    }

    @Override
    public void accept(BatchIterator<Row> iterator, @Nullable Throwable failure) {
        if (consumerAccepted.compareAndSet(false, true)) {
            this.iterator = iterator;
            tryForwardResult(failure);
        }
    }

    private void tryForwardResult(Throwable throwable) {
        if (throwable != null && (failure == null || failure instanceof InterruptedException)) {
            failure = SQLExceptions.unwrap(throwable);
        }
        if (consumerInvokedAndJobInitialized.decrementAndGet() > 0) {
            return;
        }
        if (failure == null) {
            assert iterator != null : "iterator must be present";
            executor.execute(() -> consumer.accept(iterator, null));
        } else {
            transportKillJobsNodeAction.broadcast(
                new KillJobsRequest(Collections.singletonList(jobId)), new ActionListener<Long>() {
                    @Override
                    public void onResponse(Long numKilled) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Killed {} contexts for jobId={} before forwarding the failure={}", numKilled, jobId, failure);
                        }
                        consumer.accept(null, failure);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Failed to kill jobId={}, forwarding failure={} anyway", jobId, failure);
                        }
                        consumer.accept(null, failure);
                    }
                });
        }
    }

    @Override
    public String toString() {
        return "InterceptingBatchConsumer{" +
               "consumerInvokedAndJobInitilaized=" + consumerInvokedAndJobInitialized +
               ", jobId=" + jobId +
               ", consumer=" + consumer +
               ", rowReceiverDone=" + consumerAccepted +
               ", failure=" + failure +
               '}';
    }
}
