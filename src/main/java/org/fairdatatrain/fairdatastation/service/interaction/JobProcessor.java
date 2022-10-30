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
package org.fairdatatrain.fairdatastation.service.interaction;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobProcessor {

    private final JobService jobService;

    private final GenericTrainInteraction trainInteraction;

    // TODO: config
    // TODO: multi-thread? configurable?
    @Scheduled(
            initialDelayString = "${dispatcher.dispatch.initDelay:PT1M}",
            fixedRateString = "${dispatcher.dispatch.interval:PT1M}"
    )
    public void processJobs() {
        log.info("Starting to process jobs");
        Optional<Job> job = jobService.getNextJob();
        while (job.isPresent()) {
            processJob(job.get());
            job = jobService.getNextJob();
        }
        log.info("No more jobs to process now");
    }

    @SneakyThrows
    @Transactional
    public void processJob(Job job) {
        // TODO: some stages? retries?
        // interruption on the go (no need to rerun from start)
        log.info("Processing job {}", job.getUuid());
        trainInteraction.interact(job);
        log.info("Processing job {}: done", job.getUuid());
    }
}
