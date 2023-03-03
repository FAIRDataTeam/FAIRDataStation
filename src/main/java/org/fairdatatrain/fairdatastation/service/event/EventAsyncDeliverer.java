/**
 * The MIT License
 * Copyright © 2022 FAIR Data Team
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
package org.fairdatatrain.fairdatastation.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.data.model.event.EventDelivery;
import org.fairdatatrain.fairdatastation.service.event.delivery.EventDeliverer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventAsyncDeliverer {

    private final EventDeliverer eventDeliverer;

    @Transactional
    @Scheduled(
            initialDelayString = "${dispatcher.dispatch.initDelay:PT10S}",
            fixedRateString = "${dispatcher.dispatch.interval:PT30S}"
    )
    public void processJobs() {
        final List<EventDelivery> eventDeliveryList =
                eventDeliverer.getNextEventDeliveries();
        log.info("Delivering {} items in this iteration", eventDeliveryList.size());
        eventDeliveryList.forEach(this::deliver);
    }

    protected void deliver(EventDelivery eventDelivery) {
        log.info("Delivering event delivery {}", eventDelivery.getUuid());
        if (eventDelivery.getJobEvent() != null) {
            eventDeliverer.deliver(eventDelivery.getJobEvent(), eventDelivery);
        }
        else if (eventDelivery.getJobArtifact() != null) {
            eventDeliverer.deliver(eventDelivery.getJobArtifact(), eventDelivery);
        }
    }
}
