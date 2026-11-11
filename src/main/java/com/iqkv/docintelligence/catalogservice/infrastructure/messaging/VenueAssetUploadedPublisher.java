/*
 * Copyright 2026 iQKV Foundation Team.
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

package com.iqkv.docintelligence.catalogservice.infrastructure.messaging;

import com.iqkv.docintelligence.catalogservice.infrastructure.config.RabbitMQConfig;
import com.iqkv.docintelligence.catalogservice.venue.VenueAssetUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards {@link VenueAssetUploadedEvent} to RabbitMQ for
 * {@code docintelligence-catalog-processing-worker}.
 *
 * <p>The listener runs in {@code AFTER_COMMIT} phase on purpose: publishing inside the upload
 * transaction could hand the worker an asset id whose row is not yet visible, and the worker would
 * find nothing. Venue uploads are a push-only flow — when messaging is disabled this bean does not
 * exist and the asset simply stays {@code UPLOADED} with no queue to drain it.
 */
@Component
@ConditionalOnProperty(name = "iqkv.messaging.rabbitmq.enabled", havingValue = "true")
public class VenueAssetUploadedPublisher {

  private static final Logger log = LoggerFactory.getLogger(VenueAssetUploadedPublisher.class);

  private final RabbitTemplate rabbitTemplate;

  public VenueAssetUploadedPublisher(final RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVenueAssetUploaded(final VenueAssetUploadedEvent event) {
    rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE,
        RabbitMQConfig.ROUTING_VENUE_ASSET_UPLOADED, event);
    log.info("Published venue asset uploaded event: assetId={}, tenantKey={}",
        event.assetId(), event.tenantKey());
  }
}
